package com.heartlink.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.heartlink.dto.PayOrderDTO;
import com.heartlink.entity.FamilyBind;
import com.heartlink.entity.Order;
import com.heartlink.entity.PaymentRequest;
import com.heartlink.entity.Product;
import com.heartlink.mapper.PaymentRequestMapper;
import com.heartlink.service.FamilyBindService;
import com.heartlink.service.OrderService;
import com.heartlink.service.PaymentRequestService;
import com.heartlink.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PaymentRequestServiceImpl extends ServiceImpl<PaymentRequestMapper, PaymentRequest>
        implements PaymentRequestService {

    private final OrderService orderService;
    private final FamilyBindService familyBindService;
    private final ProductService productService;

    @Override
    @Transactional
    public PaymentRequest createRequest(Long orderId, Long parentId, String message) {
        Order order = orderService.getById(orderId);
        if (order == null) {
            throw new RuntimeException("order not found");
        }
        if (!order.getParentId().equals(parentId)) {
            throw new RuntimeException("no permission to operate this order");
        }
        if (!"PENDING_PAY".equals(order.getStatus())) {
            throw new RuntimeException("order is not waiting for payment");
        }

        List<FamilyBind> binds = familyBindService.getChildrenByParentId(parentId);
        if (binds.isEmpty()) {
            throw new RuntimeException("no bound child");
        }

        Long childId = binds.get(0).getChildId();
        LambdaQueryWrapper<PaymentRequest> existsWrapper = new LambdaQueryWrapper<PaymentRequest>()
                .eq(PaymentRequest::getOrderId, orderId)
                .eq(PaymentRequest::getStatus, "PENDING");
        if (count(existsWrapper) > 0) {
            throw new RuntimeException("payment request already pending");
        }

        PaymentRequest request = new PaymentRequest();
        request.setOrderId(orderId);
        request.setParentId(parentId);
        request.setChildId(childId);
        request.setProductName(order.getProductName());
        request.setProductImage(order.getProductImage());
        request.setAmount(order.getTotalAmount());
        request.setMessage(message != null ? message : "Please help me pay for this order.");
        request.setStatus("PENDING");
        save(request);
        touchOrderForPaymentRequest(orderId);
        return request;
    }

    @Override
    @Transactional
    public PaymentRequest createRequestByProduct(Long productId, Long parentId, String message) {
        if (productId == null) {
            throw new RuntimeException("product not found");
        }

        Product product = productService.getById(productId);
        if (product == null) {
            throw new RuntimeException("product not found");
        }

        List<FamilyBind> binds = familyBindService.getChildrenByParentId(parentId);
        if (binds.isEmpty()) {
            throw new RuntimeException("no bound child");
        }

        Long childId = binds.get(0).getChildId();

        Order pendingOrder = orderService.getOne(new LambdaQueryWrapper<Order>()
                .eq(Order::getParentId, parentId)
                .eq(Order::getChildId, childId)
                .eq(Order::getProductId, productId)
                .eq(Order::getStatus, "PENDING_PAY")
                .orderByDesc(Order::getCreatedAt)
                .orderByDesc(Order::getId)
                .last("LIMIT 1"), false);

        if (pendingOrder == null) {
            pendingOrder = new Order();
            pendingOrder.setOrderNo(IdUtil.getSnowflakeNextIdStr());
            pendingOrder.setParentId(parentId);
            pendingOrder.setChildId(childId);
            pendingOrder.setProductId(product.getId());
            pendingOrder.setProductName(product.getName());
            if (product.getImages() != null && !product.getImages().isEmpty()) {
                pendingOrder.setProductImage(product.getImages().get(0));
            }
            pendingOrder.setQuantity(1);
            pendingOrder.setPrice(product.getPrice());
            pendingOrder.setTotalAmount(product.getPrice());
            pendingOrder.setStatus("PENDING_PAY");
            orderService.save(pendingOrder);
        }

        return createRequest(pendingOrder.getId(), parentId, message);
    }

    @Override
    public List<PaymentRequest> getPendingRequests(Long childId) {
        return list(new LambdaQueryWrapper<PaymentRequest>()
                .eq(PaymentRequest::getChildId, childId)
                .eq(PaymentRequest::getStatus, "PENDING")
                .orderByDesc(PaymentRequest::getCreatedAt));
    }

    @Override
    public IPage<PaymentRequest> getChildRequests(Long childId, Integer page, Integer size, String status) {
        LambdaQueryWrapper<PaymentRequest> wrapper = new LambdaQueryWrapper<PaymentRequest>()
                .eq(PaymentRequest::getChildId, childId);
        if (status != null && !status.isEmpty()) {
            wrapper.eq(PaymentRequest::getStatus, status);
        }
        wrapper.orderByDesc(PaymentRequest::getCreatedAt);
        return page(new Page<>(page, size), wrapper);
    }

    @Override
    public IPage<PaymentRequest> getParentRequests(Long parentId, Integer page, Integer size, String status) {
        LambdaQueryWrapper<PaymentRequest> wrapper = new LambdaQueryWrapper<PaymentRequest>()
                .eq(PaymentRequest::getParentId, parentId);
        if (status != null && !status.isEmpty()) {
            wrapper.eq(PaymentRequest::getStatus, status);
        }
        wrapper.orderByDesc(PaymentRequest::getCreatedAt);
        return page(new Page<>(page, size), wrapper);
    }

    @Override
    @Transactional
    public PaymentRequest payRequest(Long requestId, Long childId) {
        return payRequest(requestId, childId, null);
    }

    @Override
    @Transactional
    public PaymentRequest payRequest(Long requestId, Long childId, PayOrderDTO dto) {
        PaymentRequest request = getById(requestId);
        if (request == null || !request.getChildId().equals(childId)) {
            throw new RuntimeException("payment request not found");
        }
        if (!"PENDING".equals(request.getStatus())) {
            throw new RuntimeException("invalid payment request status");
        }

        Order paidOrder = orderService.payOrder(request.getOrderId(), childId, dto);
        request.setStatus("PAID");
        request.setAmount(paidOrder.getTotalAmount());
        request.setPaidAt(LocalDateTime.now());
        updateById(request);
        return request;
    }

    @Override
    @Transactional
    public PaymentRequest rejectRequest(Long requestId, Long childId) {
        PaymentRequest request = getById(requestId);
        if (request == null || !request.getChildId().equals(childId)) {
            throw new RuntimeException("payment request not found");
        }
        if (!"PENDING".equals(request.getStatus())) {
            throw new RuntimeException("invalid payment request status");
        }
        request.setStatus("REJECTED");
        updateById(request);
        return request;
    }

    @Override
    public int getPendingCount(Long childId) {
        return (int) count(new LambdaQueryWrapper<PaymentRequest>()
                .eq(PaymentRequest::getChildId, childId)
                .eq(PaymentRequest::getStatus, "PENDING"));
    }

    private void touchOrderForPaymentRequest(Long orderId) {
        if (orderId == null) {
            return;
        }
        orderService.lambdaUpdate()
                .eq(Order::getId, orderId)
                .set(Order::getUpdatedAt, LocalDateTime.now())
                .update();
    }
}
