package com.heartlink.service.impl;

import com.alibaba.fastjson2.JSON;
import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.heartlink.dto.CreateOrderDTO;
import com.heartlink.dto.PayOrderDTO;
import com.heartlink.entity.Address;
import com.heartlink.entity.Coupon;
import com.heartlink.entity.FamilyBind;
import com.heartlink.entity.Order;
import com.heartlink.entity.Product;
import com.heartlink.entity.SmartShelf;
import com.heartlink.entity.Supplier;
import com.heartlink.entity.User;
import com.heartlink.entity.UserCoupon;
import com.heartlink.mapper.OrderMapper;
import com.heartlink.mapper.UserCouponMapper;
import com.heartlink.service.AddressService;
import com.heartlink.service.AiService;
import com.heartlink.service.CouponService;
import com.heartlink.service.FamilyBindService;
import com.heartlink.service.MemberPointsService;
import com.heartlink.service.OrderService;
import com.heartlink.service.ProductService;
import com.heartlink.service.SmartShelfService;
import com.heartlink.service.SupplierService;
import com.heartlink.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl extends ServiceImpl<OrderMapper, Order> implements OrderService {

    private static final BigDecimal ZERO = new BigDecimal("0.00");
    private static final BigDecimal TEN = new BigDecimal("10");
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final int DEFAULT_CREDIT_SCORE = 100;
    private static final int HIGH_TRUST_CREDIT_SCORE = 90;
    private static final int LOW_CREDIT_SCORE = 50;

    private final SmartShelfService smartShelfService;
    private final ProductService productService;
    private final AiService aiService;
    private final AddressService addressService;
    private final FamilyBindService familyBindService;
    private final CouponService couponService;
    private final MemberPointsService memberPointsService;
    private final UserCouponMapper userCouponMapper;
    private final SupplierService supplierService;
    private final UserService userService;

    @Override
    @Transactional
    public Order createOrder(CreateOrderDTO dto, Long childId) {
        if (dto == null) {
            throw new RuntimeException("order request is required");
        }
        if (dto.getProductId() != null) {
            return createOrderByProduct(dto, childId);
        }
        if (dto.getShelfId() != null) {
            return createOrderFromShelf(dto, childId);
        }
        throw new RuntimeException("shelfId or productId is required");
    }

    private Order createOrderFromShelf(CreateOrderDTO dto, Long childId) {
        SmartShelf shelf = smartShelfService.getById(dto.getShelfId());
        if (shelf == null) {
            throw new RuntimeException("shelf item not found");
        }
        if (!canCreateOrderFromShelf(shelf, childId)) {
            throw new RuntimeException("no permission to operate this shelf item");
        }
        if (!"LIKE".equals(shelf.getReaction())) {
            throw new RuntimeException("only liked shelf items can create orders");
        }

        Product product = productService.getById(shelf.getProductId());
        if (product == null) {
            throw new RuntimeException("product not found");
        }

        return buildAndSavePendingOrder(dto, childId, shelf.getParentId(), product);
    }

    private Order createOrderByProduct(CreateOrderDTO dto, Long childId) {
        Product product = productService.getById(dto.getProductId());
        if (product == null) {
            throw new RuntimeException("product not found");
        }

        Long parentId = resolveDirectOrderParentId(dto.getParentId(), childId);
        return buildAndSavePendingOrder(dto, childId, parentId, product);
    }

    private Long resolveDirectOrderParentId(Long parentId, Long childId) {
        List<FamilyBind> binds = familyBindService.getParentsByChildId(childId);
        if (binds == null || binds.isEmpty()) {
            throw new RuntimeException("no bound parent");
        }

        if (parentId == null) {
            if (binds.size() == 1) {
                return binds.get(0).getParentId();
            }
            throw new RuntimeException("parentId is required");
        }

        boolean matched = binds.stream()
                .anyMatch(bind -> parentId.equals(bind.getParentId()));
        if (!matched) {
            throw new RuntimeException("no permission to order for this parent");
        }
        return parentId;
    }

    private Order buildAndSavePendingOrder(CreateOrderDTO dto, Long childId, Long parentId, Product product) {
        int quantity = normalizeQuantity(dto.getQuantity());
        ReceiverSnapshot receiverSnapshot = resolveReceiverSnapshot(dto, childId, true);

        Order order = new Order();
        order.setOrderNo(IdUtil.getSnowflakeNextIdStr());
        order.setParentId(parentId);
        order.setChildId(childId);
        order.setProductId(product.getId());
        order.setProductName(product.getName());
        order.setProductImage(product.getImages() != null && !product.getImages().isEmpty()
                ? product.getImages().get(0)
                : null);
        order.setQuantity(quantity);
        order.setPrice(product.getPrice());
        order.setTotalAmount(product.getPrice().multiply(BigDecimal.valueOf(quantity)));
        applyReceiverSnapshot(order, receiverSnapshot);
        order.setStatus("PENDING_PAY");
        fillSupplySnapshot(order, product);

        if (Boolean.TRUE.equals(dto.getGenerateGreeting())) {
            order.setGreetingCard(aiService.generateGreetingCard(product.getName(), "Mom", null));
        }

        save(order);
        return enrichOrder(order);
    }

    @Override
    @Transactional
    public Order payOrder(Long orderId, Long childId) {
        return payOrder(orderId, childId, null);
    }

    @Override
    @Transactional
    public Order payOrder(Long orderId, Long childId, PayOrderDTO dto) {
        Order order = getById(orderId);
        if (order == null || !order.getChildId().equals(childId)) {
            throw new RuntimeException("order not found");
        }
        if (!"PENDING_PAY".equals(order.getStatus())) {
            throw new RuntimeException("invalid order status");
        }
        repairReceiverInfoIfPossible(order);
        if (!hasCompleteReceiverInfo(order)) {
            throw new RuntimeException("\u8bf7\u5148\u8bbe\u7f6e\u5b8c\u6574\u7684\u6536\u8d27\u5730\u5740");
        }

        PaymentSettlement settlement = buildSettlement(order, childId, dto);

        order.setTotalAmount(settlement.finalAmount);
        order.setStatus("PAID");
        order.setPaidAt(LocalDateTime.now());
        if (!updateById(order)) {
            throw new RuntimeException("failed to update order");
        }
        syncProductTradeMetrics(order, -normalizeQuantity(order.getQuantity()), normalizeQuantity(order.getQuantity()));

        if (settlement.userCoupon != null) {
            settlement.userCoupon.setStatus("USED");
            settlement.userCoupon.setOrderId(order.getId());
            settlement.userCoupon.setUsedAt(LocalDateTime.now());
            if (userCouponMapper.updateById(settlement.userCoupon) <= 0) {
                throw new RuntimeException("failed to update coupon");
            }
        }

        return enrichOrder(order);
    }

    @Override
    public IPage<Order> getChildOrders(Long childId, Integer page, Integer size, String status) {
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<Order>()
                .eq(Order::getChildId, childId);

        if (status != null && !status.isEmpty()) {
            wrapper.eq(Order::getStatus, status);
        }

        wrapper.orderByDesc(Order::getCreatedAt);
        return page(new Page<>(page, size), wrapper);
    }

    @Override
    public IPage<Order> getParentOrders(Long parentId, Integer page, Integer size, String status) {
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<Order>()
                .eq(Order::getParentId, parentId);

        if (status != null && !status.isEmpty()) {
            wrapper.eq(Order::getStatus, status);
        }

        wrapper.orderByDesc(Order::getCreatedAt);
        return page(new Page<>(page, size), wrapper);
    }

    @Override
    public String generateGreetingCard(Long orderId, Long childId) {
        Order order = getById(orderId);
        if (order == null) {
            throw new RuntimeException("order not found");
        }
        if (!order.getChildId().equals(childId)) {
            throw new RuntimeException("no permission to operate this order");
        }

        String greeting = aiService.generateGreetingCard(order.getProductName(), "Mom", null);
        order.setGreetingCard(greeting);
        updateById(order);
        return greeting;
    }

    @Override
    @Transactional
    public Order confirmReceive(Long orderId, Long userId) {
        Order order = getById(orderId);
        if (order == null || !canConfirmReceive(order, userId)) {
            throw new RuntimeException("order not found");
        }
        if (!"SHIPPED".equals(order.getStatus())) {
            throw new RuntimeException("cannot confirm receive for current order status");
        }
        if (StringUtils.hasText(order.getTrackingNo())
                && !"DELIVERED".equals(order.getLogisticsStatus())
                && !"SIGNED".equals(order.getLogisticsStatus())) {
            throw new RuntimeException("order has not been delivered yet");
        }
        order.setStatus("COMPLETED");
        LocalDateTime completedAt = LocalDateTime.now();
        order.setCompletedAt(completedAt);
        order.setLogisticsStatus("SIGNED");
        order.setLogisticsStatusText("已签收");
        order.setLogisticsLastTrace(formatSignedTrace(completedAt));
        order.setLogisticsUpdatedAt(completedAt);
        order.setLogisticsTraceJson(appendSignedTrace(order.getLogisticsTraceJson(), completedAt));
        updateById(order);
        return enrichOrder(order);
    }

    @Override
    @Transactional
    public Order requestRefund(Long orderId, Long userId, String reason, List<String> images) {
        Order order = getById(orderId);
        if (order == null) {
            throw new RuntimeException("order not found");
        }
        User requester = userService.getById(userId);
        if (requester == null) {
            throw new RuntimeException("user not found");
        }
        if (!userId.equals(order.getChildId()) && !userId.equals(order.getParentId())) {
            throw new RuntimeException("no permission to operate this order");
        }

        boolean negotiatedRefund = isNegotiatedRefund(order);
        boolean directRefund = isDirectRefund(order);
        if (!directRefund && !negotiatedRefund) {
            throw new RuntimeException("refund not allowed for current order status");
        }

        int creditScore = normalizeCreditScore(requester.getCreditScore());
        boolean autoApprove = isHighTrustCredit(creditScore);
        boolean reasonRequired = requiresRefundReason(creditScore);
        boolean proofImagesRequired = requiresProofImages(creditScore, order);

        String normalizedReason = normalizeText(reason);
        if (reasonRequired && !StringUtils.hasText(normalizedReason)) {
            throw new RuntimeException("refund reason is required");
        }

        List<String> normalizedImages = normalizeRefundImages(images);
        if (proofImagesRequired && normalizedImages.isEmpty()) {
            throw new RuntimeException("signed orders require refund proof images");
        }

        order.setRefundReason(StringUtils.hasText(normalizedReason) ? normalizedReason : null);
        order.setRefundImages(normalizedImages.isEmpty() ? null : JSON.toJSONString(normalizedImages));
        order.setRefundCreditScore(creditScore);
        if (autoApprove) {
            return markRefundApproved(order, true);
        }
        order.setRefundStatus("REQUESTED");
        updateById(order);
        return enrichOrder(order);
    }

    @Override
    @Transactional
    public Order approveRefund(Long orderId, Long childId) {
        Order order = getById(orderId);
        if (order == null || !childId.equals(order.getChildId())) {
            throw new RuntimeException("order not found");
        }
        if (!"REQUESTED".equals(order.getRefundStatus())) {
            throw new RuntimeException("invalid refund status");
        }
        return markRefundApproved(order, false);
    }

    @Override
    @Transactional
    public Order approveRefundByAdmin(Long orderId) {
        Order order = getById(orderId);
        if (order == null) {
            throw new RuntimeException("order not found");
        }
        if (!"REQUESTED".equals(order.getRefundStatus())) {
            throw new RuntimeException("invalid refund status");
        }
        return markRefundApproved(order, true);
    }

    @Override
    @Transactional
    public Order rejectRefund(Long orderId, Long childId) {
        Order order = getById(orderId);
        if (order == null || !childId.equals(order.getChildId())) {
            throw new RuntimeException("order not found");
        }
        if (!"REQUESTED".equals(order.getRefundStatus())) {
            throw new RuntimeException("invalid refund status");
        }
        order.setRefundStatus("REJECTED");
        updateById(order);
        return enrichOrder(order);
    }

    @Override
    public Order getById(Serializable id) {
        Order order = super.getById(id);
        return enrichOrder(repairReceiverInfoIfPossible(order));
    }

    @Override
    public <E extends IPage<Order>> E page(E page, Wrapper<Order> queryWrapper) {
        E result = super.page(page, queryWrapper);
        if (result != null && result.getRecords() != null) {
            result.getRecords().forEach(this::repairReceiverInfoIfPossible);
            enrichOrders(result.getRecords());
        }
        return result;
    }

    private Order markRefundApproved(Order order, boolean cancelOrder) {
        order.setRefundStatus("COMPLETED");
        order.setRefundAt(LocalDateTime.now());
        if (cancelOrder) {
            order.setStatus("CANCELLED");
        }
        if (!updateById(order)) {
            throw new RuntimeException("failed to update order");
        }
        syncProductTradeMetrics(order, normalizeQuantity(order.getQuantity()), -normalizeQuantity(order.getQuantity()));
        return enrichOrder(order);
    }

    private Order enrichOrder(Order order) {
        if (order == null) {
            return null;
        }
        List<Order> single = new ArrayList<>();
        single.add(order);
        enrichOrders(single);
        return order;
    }

    private void enrichOrders(List<Order> orders) {
        if (orders == null || orders.isEmpty()) {
            return;
        }
        Set<Long> childIds = new LinkedHashSet<>();
        for (Order order : orders) {
            if (order != null && order.getChildId() != null) {
                childIds.add(order.getChildId());
            }
        }

        Map<Long, User> childUserMap = new HashMap<>();
        if (!childIds.isEmpty()) {
            List<User> users = userService.listByIds(childIds);
            if (users != null) {
                for (User user : users) {
                    if (user != null && user.getId() != null) {
                        childUserMap.put(user.getId(), user);
                    }
                }
            }
        }

        for (Order order : orders) {
            if (order == null) {
                continue;
            }
            User childUser = childUserMap.get(order.getChildId());
            int childCreditScore = normalizeCreditScore(childUser == null ? null : childUser.getCreditScore());
            order.setChildCreditScore(childCreditScore);

            int refundRiskScore = resolveRefundRiskScore(order, childCreditScore);
            order.setRefundCreditRiskLevel(resolveRefundRiskLevel(refundRiskScore));
            order.setRefundCreditRiskText(buildRefundRiskText(refundRiskScore));
        }
    }

    private void syncProductTradeMetrics(Order order, int stockDelta, int salesDelta) {
        if (order == null || order.getProductId() == null || (stockDelta == 0 && salesDelta == 0)) {
            return;
        }
        productService.adjustInventoryAndSales(order.getProductId(), stockDelta, salesDelta);
    }

    private ReceiverSnapshot resolveReceiverSnapshot(CreateOrderDTO dto, Long childId, boolean required) {
        return resolveReceiverSnapshot(
                dto == null ? null : dto.getReceiverName(),
                dto == null ? null : dto.getReceiverPhone(),
                dto == null ? null : dto.getReceiverAddress(),
                childId,
                required
        );
    }

    private ReceiverSnapshot resolveReceiverSnapshot(String receiverName,
                                                    String receiverPhone,
                                                    String receiverAddress,
                                                    Long childId,
                                                    boolean required) {
        ReceiverSnapshot current = new ReceiverSnapshot(
                normalizeText(receiverName),
                normalizeText(receiverPhone),
                normalizeText(receiverAddress)
        );
        if (current.isComplete()) {
            return current;
        }

        Address preferredAddress = resolvePreferredAddress(childId);
        if (preferredAddress == null) {
            if (required) {
                throw new RuntimeException("\u8bf7\u5148\u8bbe\u7f6e\u6536\u8d27\u5730\u5740");
            }
            return current;
        }

        ReceiverSnapshot resolved = new ReceiverSnapshot(
                StringUtils.hasText(current.receiverName) ? current.receiverName : normalizeText(preferredAddress.getReceiverName()),
                StringUtils.hasText(current.receiverPhone) ? current.receiverPhone : normalizeText(preferredAddress.getReceiverPhone()),
                StringUtils.hasText(current.receiverAddress) ? current.receiverAddress : buildReceiverAddress(preferredAddress)
        );

        if (required && !resolved.isComplete()) {
            throw new RuntimeException("\u8bf7\u5148\u5b8c\u5584\u9ed8\u8ba4\u6536\u8d27\u5730\u5740");
        }
        return resolved;
    }

    private Address resolvePreferredAddress(Long childId) {
        if (childId == null) {
            return null;
        }
        List<Address> addresses = addressService.getAddressList(childId);
        if (addresses == null || addresses.isEmpty()) {
            return null;
        }
        for (Address address : addresses) {
            if (Integer.valueOf(1).equals(address.getIsDefault())) {
                return address;
            }
        }
        return addresses.get(0);
    }

    private String buildReceiverAddress(Address address) {
        if (address == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        appendAddressPart(builder, address.getProvince());
        appendAddressPart(builder, address.getCity());
        appendAddressPart(builder, address.getDistrict());
        appendAddressPart(builder, address.getDetailAddress());
        return builder.toString();
    }

    private void appendAddressPart(StringBuilder builder, String value) {
        String normalized = normalizeText(value);
        if (StringUtils.hasText(normalized)) {
            builder.append(normalized);
        }
    }

    private void applyReceiverSnapshot(Order order, ReceiverSnapshot snapshot) {
        if (order == null || snapshot == null) {
            return;
        }
        order.setReceiverName(StringUtils.hasText(snapshot.receiverName) ? snapshot.receiverName : null);
        order.setReceiverPhone(StringUtils.hasText(snapshot.receiverPhone) ? snapshot.receiverPhone : null);
        order.setReceiverAddress(StringUtils.hasText(snapshot.receiverAddress) ? snapshot.receiverAddress : null);
    }

    private Order repairReceiverInfoIfPossible(Order order) {
        if (!shouldRepairReceiverInfo(order)) {
            return order;
        }
        ReceiverSnapshot snapshot = resolveReceiverSnapshot(
                order.getReceiverName(),
                order.getReceiverPhone(),
                order.getReceiverAddress(),
                order.getChildId(),
                false
        );
        if (snapshot == null || !snapshot.isComplete()) {
            return order;
        }
        if (!hasReceiverInfoChanged(order, snapshot)) {
            return order;
        }
        applyReceiverSnapshot(order, snapshot);
        updateById(order);
        return order;
    }

    private boolean shouldRepairReceiverInfo(Order order) {
        if (order == null || hasCompleteReceiverInfo(order)) {
            return false;
        }
        return "PENDING_PAY".equals(order.getStatus()) || "PAID".equals(order.getStatus());
    }

    private boolean hasCompleteReceiverInfo(Order order) {
        return order != null
                && StringUtils.hasText(order.getReceiverName())
                && StringUtils.hasText(order.getReceiverPhone())
                && StringUtils.hasText(order.getReceiverAddress());
    }

    private boolean hasReceiverInfoChanged(Order order, ReceiverSnapshot snapshot) {
        return !normalizeText(order.getReceiverName()).equals(snapshot.receiverName)
                || !normalizeText(order.getReceiverPhone()).equals(snapshot.receiverPhone)
                || !normalizeText(order.getReceiverAddress()).equals(snapshot.receiverAddress);
    }

    private boolean canConfirmReceive(Order order, Long userId) {
        if (order == null || userId == null) {
            return false;
        }
        return userId.equals(order.getParentId()) || userId.equals(order.getChildId());
    }

    private boolean isDirectRefund(Order order) {
        if (order == null) {
            return false;
        }
        return "PAID".equals(order.getStatus()) || "SHIPPED".equals(order.getStatus());
    }

    private boolean isNegotiatedRefund(Order order) {
        if (order == null) {
            return false;
        }
        return "COMPLETED".equals(order.getStatus()) || "SIGNED".equals(order.getLogisticsStatus());
    }

    private int normalizeCreditScore(Integer creditScore) {
        if (creditScore == null) {
            return DEFAULT_CREDIT_SCORE;
        }
        return Math.max(0, Math.min(100, creditScore));
    }

    private boolean isHighTrustCredit(int creditScore) {
        return creditScore > HIGH_TRUST_CREDIT_SCORE;
    }

    private boolean isLowCredit(int creditScore) {
        return creditScore < LOW_CREDIT_SCORE;
    }

    private boolean requiresRefundReason(int creditScore) {
        return !isHighTrustCredit(creditScore);
    }

    private boolean requiresProofImages(int creditScore, Order order) {
        return !isHighTrustCredit(creditScore) && isNegotiatedRefund(order);
    }

    private int resolveRefundRiskScore(Order order, int childCreditScore) {
        if (order != null
                && order.getRefundCreditScore() != null
                && !"NONE".equals(order.getRefundStatus())
                && StringUtils.hasText(order.getRefundStatus())) {
            return normalizeCreditScore(order.getRefundCreditScore());
        }
        return childCreditScore;
    }

    private String resolveRefundRiskLevel(int creditScore) {
        if (isHighTrustCredit(creditScore)) {
            return "HIGH_TRUST";
        }
        if (isLowCredit(creditScore)) {
            return "LOW_CREDIT";
        }
        return "NORMAL";
    }

    private String buildRefundRiskText(int creditScore) {
        if (isHighTrustCredit(creditScore)) {
            return "信用分较高，可直接退款";
        }
        if (isLowCredit(creditScore)) {
            return "信用分较低，本次退款需重点核验";
        }
        return "信用分正常，本次退款需填写原因";
    }

    private List<String> normalizeRefundImages(List<String> images) {
        if (images == null || images.isEmpty()) {
            return List.of();
        }
        Set<String> unique = new LinkedHashSet<>();
        for (String item : images) {
            String normalized = normalizeText(item);
            if (StringUtils.hasText(normalized)) {
                unique.add(normalized);
            }
        }
        return new ArrayList<>(unique);
    }

    private String formatSignedTrace(LocalDateTime signedAt) {
        return (signedAt == null ? LocalDateTime.now() : signedAt) + " 收货人已确认签收";
    }

    private String appendSignedTrace(String traceJson, LocalDateTime signedAt) {
        List<com.heartlink.dto.LogisticsTraceItemDTO> traces = new ArrayList<>();
        if (StringUtils.hasText(traceJson)) {
            try {
                traces = JSON.parseArray(traceJson, com.heartlink.dto.LogisticsTraceItemDTO.class);
            } catch (Exception ignored) {
                traces = new ArrayList<>();
            }
        }
        String signedTime = (signedAt == null ? LocalDateTime.now() : signedAt).toString();
        boolean alreadySigned = traces.stream()
                .anyMatch(item -> item != null && "收货人已确认签收".equals(item.getAcceptStation()));
        if (!alreadySigned) {
            com.heartlink.dto.LogisticsTraceItemDTO signedTrace = new com.heartlink.dto.LogisticsTraceItemDTO();
            signedTrace.setAcceptTime(signedTime);
            signedTrace.setAcceptStation("收货人已确认签收");
            signedTrace.setLocation("订单完成");
            traces.add(0, signedTrace);
        }
        return JSON.toJSONString(traces);
    }

    private String normalizeText(String value) {
        return value == null ? "" : value.trim();
    }

    private static final class ReceiverSnapshot {
        private final String receiverName;
        private final String receiverPhone;
        private final String receiverAddress;

        private ReceiverSnapshot(String receiverName, String receiverPhone, String receiverAddress) {
            this.receiverName = receiverName;
            this.receiverPhone = receiverPhone;
            this.receiverAddress = receiverAddress;
        }

        private boolean isComplete() {
            return StringUtils.hasText(receiverName)
                    && StringUtils.hasText(receiverPhone)
                    && StringUtils.hasText(receiverAddress);
        }
    }

    private boolean canCreateOrderFromShelf(SmartShelf shelf, Long childId) {
        if (childId == null || shelf == null) {
            return false;
        }
        if (childId.equals(shelf.getChildId())) {
            return true;
        }
        if (!isParentCollectedShelf(shelf)) {
            return false;
        }
        return familyBindService.getParentsByChildId(childId).stream()
                .anyMatch(bind -> shelf.getParentId().equals(bind.getParentId()));
    }

    private boolean isParentCollectedShelf(SmartShelf shelf) {
        return shelf.getChildId() != null
                && shelf.getParentId() != null
                && shelf.getChildId().equals(shelf.getParentId());
    }

    private PaymentSettlement buildSettlement(Order order, Long childId, PayOrderDTO dto) {
        BigDecimal originalAmount = normalizeMoney(resolveOriginalAmount(order));
        BigDecimal memberAmount = normalizeMoney(memberPointsService.calculateDiscount(childId, originalAmount));
        if (memberAmount.compareTo(originalAmount) > 0) {
            memberAmount = originalAmount;
        }

        UserCoupon userCoupon = null;
        BigDecimal couponDiscount = ZERO;
        if (dto != null && dto.getUserCouponId() != null) {
            userCoupon = loadValidUserCoupon(dto.getUserCouponId(), childId);
            Coupon coupon = loadValidCoupon(userCoupon, order, memberAmount);
            couponDiscount = calculateCouponDiscount(coupon, memberAmount);
        }

        BigDecimal amountAfterCoupon = normalizeMoney(memberAmount.subtract(couponDiscount));
        int pointsToUse = normalizePoints(dto == null ? null : dto.getPointsToUse());
        int maxPointsToUse = amountAfterCoupon.multiply(HUNDRED).setScale(0, RoundingMode.DOWN).intValue();
        if (pointsToUse > maxPointsToUse) {
            throw new RuntimeException("points exceed payable amount");
        }

        BigDecimal pointsDiscount = ZERO;
        if (pointsToUse > 0) {
            pointsDiscount = normalizeMoney(memberPointsService.usePointsForDiscount(childId, pointsToUse));
        }

        BigDecimal finalAmount = normalizeMoney(amountAfterCoupon.subtract(pointsDiscount));
        if (finalAmount.compareTo(BigDecimal.ZERO) < 0) {
            finalAmount = ZERO;
        }

        return new PaymentSettlement(finalAmount, userCoupon);
    }

    private BigDecimal resolveOriginalAmount(Order order) {
        if (order.getTotalAmount() != null) {
            return order.getTotalAmount();
        }
        if (order.getPrice() != null && order.getQuantity() != null) {
            return order.getPrice().multiply(BigDecimal.valueOf(order.getQuantity()));
        }
        return ZERO;
    }

    private UserCoupon loadValidUserCoupon(Long userCouponId, Long childId) {
        UserCoupon userCoupon = userCouponMapper.selectById(userCouponId);
        if (userCoupon == null || !childId.equals(userCoupon.getUserId())) {
            throw new RuntimeException("coupon not found");
        }
        if (!"UNUSED".equals(userCoupon.getStatus())) {
            throw new RuntimeException("coupon is not available");
        }
        return userCoupon;
    }

    private Coupon loadValidCoupon(UserCoupon userCoupon, Order order, BigDecimal amount) {
        Coupon coupon = couponService.getById(userCoupon.getCouponId());
        if (coupon == null || coupon.getStatus() == null || coupon.getStatus() != 1) {
            throw new RuntimeException("coupon is unavailable");
        }

        LocalDateTime now = LocalDateTime.now();
        if (coupon.getStartTime() != null && coupon.getStartTime().isAfter(now)) {
            throw new RuntimeException("coupon is unavailable");
        }
        if (coupon.getEndTime() != null && !coupon.getEndTime().isAfter(now)) {
            throw new RuntimeException("coupon has expired");
        }
        if (coupon.getMinAmount() != null && amount.compareTo(coupon.getMinAmount()) < 0) {
            throw new RuntimeException("coupon threshold not met");
        }
        if (coupon.getCategoryId() != null) {
            Product product = productService.getById(order.getProductId());
            if (product == null || !coupon.getCategoryId().equals(product.getCategoryId())) {
                throw new RuntimeException("coupon does not match this product");
            }
        }
        return coupon;
    }

    private BigDecimal calculateCouponDiscount(Coupon coupon, BigDecimal amount) {
        if (coupon.getValue() == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return ZERO;
        }

        if ("FIXED".equalsIgnoreCase(coupon.getType())) {
            return normalizeMoney(coupon.getValue().min(amount));
        }

        if ("PERCENT".equalsIgnoreCase(coupon.getType())) {
            BigDecimal payRate = coupon.getValue().compareTo(TEN) > 0
                    ? coupon.getValue().divide(HUNDRED, 4, RoundingMode.DOWN)
                    : coupon.getValue().divide(TEN, 4, RoundingMode.DOWN);
            if (payRate.compareTo(BigDecimal.ZERO) < 0) {
                payRate = BigDecimal.ZERO;
            }
            if (payRate.compareTo(BigDecimal.ONE) > 0) {
                payRate = BigDecimal.ONE;
            }
            return normalizeMoney(amount.subtract(amount.multiply(payRate)));
        }

        return ZERO;
    }

    private int normalizeQuantity(Integer quantity) {
        return (quantity == null || quantity <= 0) ? 1 : quantity;
    }

    private void fillSupplySnapshot(Order order, Product product) {
        String sourceType = normalizeSourceType(product.getSourceType(), product.getSupplierId());
        order.setSupplierId(product.getSupplierId());
        order.setSourceType(sourceType);
        order.setExternalSkuId(trimToNull(product.getExternalSkuId()));
        order.setPurchasePriceSnapshot(product.getPurchasePrice());

        if ("SELF_OPERATED".equalsIgnoreCase(sourceType)) {
            order.setSettlementMode("SELF");
            order.setSettlementTaxRateSnapshot(null);
            order.setSettlementTaxSource("SELF_OPERATED");
            order.setProcurementStatus("SELF_FULFILLED");
            return;
        }

        Supplier supplier = supplierService.getById(product.getSupplierId());
        if (supplier != null) {
            order.setSupplierName(supplier.getName());
            order.setSettlementMode(defaultIfBlank(supplier.getSettlementMode(), "MANUAL"));
        } else {
            order.setSettlementMode("MANUAL");
        }
        BigDecimal effectiveTaxRate = resolveEffectiveTaxRate(product, supplier);
        if (effectiveTaxRate == null) {
            throw new RuntimeException("third-party product tax rate is not configured");
        }
        order.setSettlementTaxRateSnapshot(effectiveTaxRate);
        order.setSettlementTaxSource(resolveSettlementTaxSource(product));
        order.setProcurementStatus(resolveProcurementStatus(product.getDeliveryMode(), sourceType));
    }

    private BigDecimal resolveEffectiveTaxRate(Product product, Supplier supplier) {
        String taxRateMode = trimToNull(product.getTaxRateMode());
        if ("CUSTOM".equalsIgnoreCase(taxRateMode)) {
            return normalizeRate(product.getTaxRate());
        }
        if (supplier != null && supplier.getDefaultTaxRate() != null) {
            return normalizeRate(supplier.getDefaultTaxRate());
        }
        return normalizeRate(product.getTaxRate());
    }

    private String resolveSettlementTaxSource(Product product) {
        return "CUSTOM".equalsIgnoreCase(trimToNull(product.getTaxRateMode()))
                ? "PRODUCT_CUSTOM"
                : "SUPPLIER_DEFAULT";
    }

    private int normalizePoints(Integer points) {
        if (points == null) {
            return 0;
        }
        if (points < 0) {
            throw new RuntimeException("points must be positive");
        }
        return points;
    }

    private BigDecimal normalizeMoney(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) < 0) {
            return ZERO;
        }
        return amount.setScale(2, RoundingMode.DOWN);
    }

    private BigDecimal normalizeRate(BigDecimal amount) {
        if (amount == null) {
            return null;
        }
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new RuntimeException("tax rate must be positive");
        }
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    private String normalizeSourceType(String sourceType, Long supplierId) {
        String normalized = trimToNull(sourceType);
        if (normalized != null) {
            return normalized;
        }
        return supplierId == null ? "SELF_OPERATED" : "CURATED_SUPPLIER";
    }

    private String resolveProcurementStatus(String deliveryMode, String sourceType) {
        if ("SELF_OPERATED".equalsIgnoreCase(sourceType)) {
            return "SELF_FULFILLED";
        }
        if ("SUPPLIER_DIRECT".equalsIgnoreCase(trimToNull(deliveryMode))) {
            return "DIRECT_SHIP";
        }
        return "PENDING_PROCUREMENT";
    }

    private String defaultIfBlank(String value, String fallback) {
        String normalized = trimToNull(value);
        return normalized == null ? fallback : normalized;
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private static class PaymentSettlement {
        private final BigDecimal finalAmount;
        private final UserCoupon userCoupon;

        private PaymentSettlement(BigDecimal finalAmount, UserCoupon userCoupon) {
            this.finalAmount = finalAmount;
            this.userCoupon = userCoupon;
        }
    }
}
