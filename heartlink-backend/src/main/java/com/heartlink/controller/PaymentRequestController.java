package com.heartlink.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.heartlink.common.Result;
import com.heartlink.dto.PayOrderDTO;
import com.heartlink.entity.PaymentRequest;
import com.heartlink.entity.User;
import com.heartlink.service.PaymentRequestService;
import com.heartlink.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "Payment Request")
@RestController
@RequestMapping("/api/payment-request")
@RequiredArgsConstructor
public class PaymentRequestController {

    private final PaymentRequestService paymentRequestService;
    private final UserService userService;

    @Operation(summary = "Create payment request")
    @PostMapping("/create")
    public Result<PaymentRequest> createRequest(@RequestParam Long orderId,
                                                @RequestParam(required = false) String message) {
        Long parentId = StpUtil.getLoginIdAsLong();
        assertRole(parentId, "PARENT");
        PaymentRequest request = paymentRequestService.createRequest(orderId, parentId, message);
        return Result.success("Payment request sent", request);
    }

    @Operation(summary = "Create payment request by product")
    @PostMapping("/create-by-product")
    public Result<PaymentRequest> createRequestByProduct(@RequestParam Long productId,
                                                         @RequestParam(required = false) String message) {
        Long parentId = StpUtil.getLoginIdAsLong();
        assertRole(parentId, "PARENT");
        PaymentRequest request = paymentRequestService.createRequestByProduct(productId, parentId, message);
        return Result.success("Payment request sent", request);
    }

    @Operation(summary = "Get pending requests")
    @GetMapping("/pending")
    public Result<List<PaymentRequest>> getPendingRequests() {
        Long childId = StpUtil.getLoginIdAsLong();
        assertRole(childId, "CHILD");
        return Result.success(paymentRequestService.getPendingRequests(childId));
    }

    @Operation(summary = "Get pending request count")
    @GetMapping("/pending-count")
    public Result<Map<String, Integer>> getPendingCount() {
        Long childId = StpUtil.getLoginIdAsLong();
        assertRole(childId, "CHILD");
        Map<String, Integer> result = new HashMap<>();
        result.put("count", paymentRequestService.getPendingCount(childId));
        return Result.success(result);
    }

    @Operation(summary = "Get child payment requests")
    @GetMapping("/child-requests")
    public Result<IPage<PaymentRequest>> getChildRequests(@RequestParam(defaultValue = "1") Integer page,
                                                          @RequestParam(defaultValue = "10") Integer size,
                                                          @RequestParam(required = false) String status) {
        Long childId = StpUtil.getLoginIdAsLong();
        assertRole(childId, "CHILD");
        return Result.success(paymentRequestService.getChildRequests(childId, page, size, status));
    }

    @Operation(summary = "Get parent payment requests")
    @GetMapping("/parent-requests")
    public Result<IPage<PaymentRequest>> getParentRequests(@RequestParam(defaultValue = "1") Integer page,
                                                           @RequestParam(defaultValue = "10") Integer size,
                                                           @RequestParam(required = false) String status) {
        Long parentId = StpUtil.getLoginIdAsLong();
        assertRole(parentId, "PARENT");
        return Result.success(paymentRequestService.getParentRequests(parentId, page, size, status));
    }

    @Operation(summary = "Pay request")
    @PostMapping("/{requestId}/pay")
    public Result<PaymentRequest> payRequest(@PathVariable Long requestId,
                                             @RequestBody(required = false) PayOrderDTO dto) {
        Long childId = StpUtil.getLoginIdAsLong();
        assertRole(childId, "CHILD");
        PaymentRequest request = paymentRequestService.payRequest(requestId, childId, dto);
        return Result.success("Payment completed", request);
    }

    @Operation(summary = "Reject request")
    @PostMapping("/{requestId}/reject")
    public Result<PaymentRequest> rejectRequest(@PathVariable Long requestId) {
        Long childId = StpUtil.getLoginIdAsLong();
        assertRole(childId, "CHILD");
        PaymentRequest request = paymentRequestService.rejectRequest(requestId, childId);
        return Result.success("Request rejected", request);
    }

    private void assertRole(Long userId, String expectedRole) {
        User user = userService.getById(userId);
        if (user == null || !expectedRole.equals(user.getRole())) {
            throw new RuntimeException("no permission to access this api");
        }
    }
}
