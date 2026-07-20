package com.heartlink.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.heartlink.common.Result;
import com.heartlink.config.LogisticsProperties;
import com.heartlink.dto.AdminShipOrderDTO;
import com.heartlink.dto.OrderLogisticsDTO;
import com.heartlink.entity.Order;
import com.heartlink.entity.PaymentRequest;
import com.heartlink.entity.User;
import com.heartlink.service.LogisticsService;
import com.heartlink.service.OrderService;
import com.heartlink.service.PaymentRequestService;
import com.heartlink.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Tag(name = "管理端订单管理")
@RestController
@RequestMapping("/api/admin/order")
@RequiredArgsConstructor
public class AdminOrderController {

    private final OrderService orderService;
    private final LogisticsService logisticsService;
    private final LogisticsProperties logisticsProperties;
    private final PaymentRequestService paymentRequestService;
    private final UserService userService;

    @Operation(summary = "分页查询订单")
    @GetMapping("/page")
    public Result<IPage<Order>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String orderNo) {
        refreshAdminOrderPageMockLogistics(status, orderNo);
        LambdaQueryWrapper<Order> wrapper = buildOrderPageWrapper(status, orderNo);
        wrapper.orderByDesc(Order::getUpdatedAt)
                .orderByDesc(Order::getCreatedAt);
        IPage<Order> result = orderService.page(new Page<>(page, size), wrapper);
        refreshMockLogistics(result);
        return Result.success(enrichAdminOrderPage(result));
    }

    @Operation(summary = "分页查询物流订单")
    @GetMapping("/logistics/page")
    public Result<IPage<Order>> logisticsPage(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String logisticsStatus,
            @RequestParam(required = false) String orderNo,
            @RequestParam(required = false) String trackingNo,
            @RequestParam(required = false) String expressCompanyCode) {
        refreshAdminLogisticsPageMockLogistics(status, orderNo, trackingNo, expressCompanyCode);
        LambdaQueryWrapper<Order> wrapper = buildLogisticsPageWrapper(status, logisticsStatus, orderNo, trackingNo, expressCompanyCode);
        wrapper.orderByDesc(Order::getLogisticsUpdatedAt)
                .orderByDesc(Order::getShippedAt)
                .orderByDesc(Order::getCreatedAt);
        IPage<Order> result = orderService.page(new Page<>(page, size), wrapper);
        refreshMockLogistics(result);
        return Result.success(enrichAdminOrderPage(result));
    }

    @Operation(summary = "分页查询退款订单")
    @GetMapping("/refund/page")
    public Result<IPage<Order>> refundPage(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String refundStatus,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String orderNo) {
        LambdaQueryWrapper<Order> wrapper = buildOrderPageWrapper(status, orderNo);
        wrapper.ne(Order::getRefundStatus, "NONE");
        if (StringUtils.hasText(refundStatus)) {
            wrapper.eq(Order::getRefundStatus, refundStatus);
        }
        wrapper.orderByDesc(Order::getUpdatedAt)
                .orderByDesc(Order::getCreatedAt);
        IPage<Order> result = orderService.page(new Page<>(page, size), wrapper);
        return Result.success(enrichAdminOrderPage(result));
    }

    @Operation(summary = "获取订单详情")
    @GetMapping("/{id}")
    public Result<Order> getById(@PathVariable Long id) {
        Order order = orderService.getById(id);
        refreshMockLogistics(order);
        return Result.success(enrichAdminOrder(order));
    }

    @Operation(summary = "发货")
    @PostMapping("/{id}/ship")
    public Result<Order> ship(@PathVariable Long id, @Valid @RequestBody AdminShipOrderDTO dto) {
        Order order = orderService.getById(id);
        if (order == null) {
            return Result.error("订单不存在");
        }
        if (!"PAID".equals(order.getStatus())) {
            return Result.error("订单状态不正确");
        }

        String expressCompanyCode = dto.getExpressCompanyCode() == null ? "" : dto.getExpressCompanyCode().trim().toUpperCase();
        String expressCompanyName = dto.getExpressCompanyName() == null ? "" : dto.getExpressCompanyName().trim();
        String trackingNo = dto.getTrackingNo() == null ? "" : dto.getTrackingNo().trim();
        if (!StringUtils.hasText(expressCompanyCode)
                || !StringUtils.hasText(expressCompanyName)
                || !StringUtils.hasText(trackingNo)) {
            return Result.error("请先填写完整的发货录入信息");
        }

        boolean thirdPartyEnabled = logisticsProperties.isQueryEnabled();
        order.setStatus("SHIPPED");
        order.setShippedAt(LocalDateTime.now());
        order.setExpressCompanyCode(expressCompanyCode);
        order.setExpressCompanyName(expressCompanyName);
        order.setTrackingNo(trackingNo);
        order.setLogisticsProvider(logisticsProperties.resolveProviderCode());
        order.setLogisticsStatus(thirdPartyEnabled ? "PENDING" : LogisticsProperties.MANUAL_PROVIDER);
        order.setLogisticsStatusText(thirdPartyEnabled ? "已发货，待物流更新" : "已发货，未启用第三方物流查询");
        order.setLogisticsLastTrace(thirdPartyEnabled
                ? order.getExpressCompanyName() + "：商家已录入运单，等待快递揽收"
                : order.getExpressCompanyName() + "：商家已录入运单，可凭运单号手动查询");
        order.setLogisticsUpdatedAt(LocalDateTime.now());
        orderService.updateById(order);
        logisticsService.tryRefreshOrderLogistics(order);
        return Result.success(orderService.getById(id));
    }

    @Operation(summary = "查询订单物流")
    @GetMapping("/{id}/logistics")
    public Result<OrderLogisticsDTO> getLogistics(@PathVariable Long id,
                                                  @RequestParam(defaultValue = "true") boolean refresh) {
        Order order = orderService.getById(id);
        if (order == null) {
            return Result.error("订单不存在");
        }
        return Result.success(logisticsService.getOrderLogistics(order, refresh));
    }

    @Operation(summary = "同意退款")
    @PostMapping("/{id}/refund-approve")
    public Result<Void> refundApprove(@PathVariable Long id) {
        try {
            orderService.approveRefundByAdmin(id);
        } catch (RuntimeException ex) {
            return Result.error(ex.getMessage());
        }
        return Result.success(null);
    }

    @Operation(summary = "拒绝退款")
    @PostMapping("/{id}/refund-reject")
    public Result<Void> refundReject(@PathVariable Long id) {
        Order order = orderService.getById(id);
        if (order == null) {
            return Result.error("订单不存在");
        }
        if (!"REQUESTED".equals(order.getRefundStatus())) {
            return Result.error("退款状态不正确");
        }
        order.setRefundStatus("REJECTED");
        orderService.updateById(order);
        return Result.success(null);
    }

    private IPage<Order> enrichAdminOrderPage(IPage<Order> page) {
        if (page == null) {
            return null;
        }
        page.setRecords(enrichAdminOrders(page.getRecords()));
        return page;
    }

    private Order enrichAdminOrder(Order order) {
        List<Order> records = enrichAdminOrders(order == null ? List.of() : List.of(order));
        return records.isEmpty() ? order : records.get(0);
    }

    private List<Order> enrichAdminOrders(List<Order> orders) {
        if (orders == null || orders.isEmpty()) {
            return orders;
        }

        Set<Long> parentIds = new LinkedHashSet<>();
        Set<Long> childIds = new LinkedHashSet<>();
        Set<Long> orderIds = new LinkedHashSet<>();
        for (Order order : orders) {
            if (order == null) {
                continue;
            }
            if (order.getParentId() != null) {
                parentIds.add(order.getParentId());
            }
            if (order.getChildId() != null) {
                childIds.add(order.getChildId());
            }
            if (order.getId() != null) {
                orderIds.add(order.getId());
            }
        }

        Set<Long> userIds = new LinkedHashSet<>();
        userIds.addAll(parentIds);
        userIds.addAll(childIds);

        Map<Long, User> userMap = new HashMap<>();
        if (!userIds.isEmpty()) {
            for (User user : userService.listByIds(userIds)) {
                if (user != null && user.getId() != null) {
                    userMap.put(user.getId(), user);
                }
            }
        }

        Map<Long, PaymentRequest> requestMap = new HashMap<>();
        if (!orderIds.isEmpty()) {
            List<PaymentRequest> requests = paymentRequestService.list(new LambdaQueryWrapper<PaymentRequest>()
                    .in(PaymentRequest::getOrderId, orderIds)
                    .orderByDesc(PaymentRequest::getCreatedAt)
                    .orderByDesc(PaymentRequest::getId));
            for (PaymentRequest request : requests) {
                if (request != null && request.getOrderId() != null && !requestMap.containsKey(request.getOrderId())) {
                    requestMap.put(request.getOrderId(), request);
                }
            }
        }

        List<Order> enriched = new ArrayList<>(orders.size());
        for (Order order : orders) {
            if (order == null) {
                continue;
            }
            Order item = new Order();
            BeanUtils.copyProperties(order, item);

            item.setParentNickname(resolveUserDisplayName(userMap.get(order.getParentId()), order.getParentId()));
            item.setChildNickname(resolveUserDisplayName(userMap.get(order.getChildId()), order.getChildId()));

            PaymentRequest request = requestMap.get(order.getId());
            if (request != null) {
                item.setOrderSourceType("PARENT_PAYMENT_REQUEST");
                item.setOrderSourceText("长辈代付");
                item.setPaymentRequestStatus(request.getStatus());
            } else {
                item.setOrderSourceType("CHILD_DIRECT");
                item.setOrderSourceText("子女下单");
                item.setPaymentRequestStatus(null);
            }

            enriched.add(item);
        }
        return enriched;
    }

    private String resolveUserDisplayName(User user, Long userId) {
        if (user == null) {
            return userId == null ? "-" : "ID " + userId;
        }
        if (StringUtils.hasText(user.getNickname())) {
            return user.getNickname();
        }
        if (StringUtils.hasText(user.getPhone())) {
            return user.getPhone();
        }
        return user.getId() == null ? "-" : "ID " + user.getId();
    }

    private LambdaQueryWrapper<Order> buildOrderPageWrapper(String status, String orderNo) {
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(status)) {
            wrapper.eq(Order::getStatus, status);
        }
        if (StringUtils.hasText(orderNo)) {
            wrapper.like(Order::getOrderNo, orderNo);
        }
        return wrapper;
    }

    private LambdaQueryWrapper<Order> buildLogisticsPageWrapper(String status,
                                                                String logisticsStatus,
                                                                String orderNo,
                                                                String trackingNo,
                                                                String expressCompanyCode) {
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
        wrapper.isNotNull(Order::getTrackingNo)
                .ne(Order::getTrackingNo, "");
        if (StringUtils.hasText(status)) {
            wrapper.eq(Order::getStatus, status);
        }
        if (StringUtils.hasText(logisticsStatus)) {
            wrapper.eq(Order::getLogisticsStatus, logisticsStatus);
        }
        if (StringUtils.hasText(orderNo)) {
            wrapper.like(Order::getOrderNo, orderNo.trim());
        }
        if (StringUtils.hasText(trackingNo)) {
            wrapper.like(Order::getTrackingNo, trackingNo.trim());
        }
        if (StringUtils.hasText(expressCompanyCode)) {
            wrapper.eq(Order::getExpressCompanyCode, expressCompanyCode.trim().toUpperCase());
        }
        return wrapper;
    }

    private void refreshMockLogistics(IPage<Order> page) {
        if (page == null || page.getRecords() == null) {
            return;
        }
        page.getRecords().forEach(this::refreshMockLogistics);
    }

    private void refreshMockLogistics(Order order) {
        if (order == null) {
            return;
        }
        if (LogisticsProperties.MOCK_PROVIDER.equalsIgnoreCase(String.valueOf(order.getLogisticsProvider()))) {
            logisticsService.tryRefreshOrderLogistics(order);
        }
    }

    private void refreshAdminOrderPageMockLogistics(String status, String orderNo) {
        if (StringUtils.hasText(status) && !"SHIPPED".equals(status)) {
            return;
        }
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<Order>()
                .eq(Order::getStatus, "SHIPPED")
                .eq(Order::getLogisticsProvider, LogisticsProperties.MOCK_PROVIDER)
                .isNotNull(Order::getTrackingNo)
                .ne(Order::getTrackingNo, "");
        if (StringUtils.hasText(orderNo)) {
            wrapper.like(Order::getOrderNo, orderNo.trim());
        }
        orderService.list(wrapper).forEach(logisticsService::tryRefreshOrderLogistics);
    }

    private void refreshAdminLogisticsPageMockLogistics(String status,
                                                        String orderNo,
                                                        String trackingNo,
                                                        String expressCompanyCode) {
        if (StringUtils.hasText(status) && !"SHIPPED".equals(status)) {
            return;
        }
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<Order>()
                .eq(Order::getStatus, "SHIPPED")
                .eq(Order::getLogisticsProvider, LogisticsProperties.MOCK_PROVIDER)
                .isNotNull(Order::getTrackingNo)
                .ne(Order::getTrackingNo, "");
        if (StringUtils.hasText(orderNo)) {
            wrapper.like(Order::getOrderNo, orderNo.trim());
        }
        if (StringUtils.hasText(trackingNo)) {
            wrapper.like(Order::getTrackingNo, trackingNo.trim());
        }
        if (StringUtils.hasText(expressCompanyCode)) {
            wrapper.eq(Order::getExpressCompanyCode, expressCompanyCode.trim().toUpperCase());
        }
        orderService.list(wrapper).forEach(logisticsService::tryRefreshOrderLogistics);
    }
}
