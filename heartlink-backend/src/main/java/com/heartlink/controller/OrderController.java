package com.heartlink.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.heartlink.common.Result;
import com.heartlink.config.LogisticsProperties;
import com.heartlink.dto.CreateOrderDTO;
import com.heartlink.dto.OrderLogisticsDTO;
import com.heartlink.dto.PayOrderDTO;
import com.heartlink.dto.RefundRequestDTO;
import com.heartlink.entity.Order;
import com.heartlink.entity.User;
import com.heartlink.service.ImageUploadService;
import com.heartlink.service.LogisticsService;
import com.heartlink.service.OrderService;
import com.heartlink.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@Tag(name = "Order")
@RestController
@RequestMapping("/api/order")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final UserService userService;
    private final LogisticsService logisticsService;
    private final ImageUploadService imageUploadService;

    @Operation(summary = "Create order from shelf item")
    @PostMapping("/create")
    public Result<Order> createOrder(@Valid @RequestBody CreateOrderDTO dto) {
        Long childId = StpUtil.getLoginIdAsLong();
        assertRole(childId, "CHILD");
        Order order = orderService.createOrder(dto, childId);
        return Result.success("Order created", order);
    }

    @Operation(summary = "Pay order")
    @PostMapping("/{orderId}/pay")
    public Result<Order> payOrder(@PathVariable Long orderId,
                                  @RequestBody(required = false) PayOrderDTO dto) {
        Long childId = StpUtil.getLoginIdAsLong();
        assertRole(childId, "CHILD");
        Order order = orderService.payOrder(orderId, childId, dto);
        return Result.success("Payment completed", order);
    }

    @Operation(summary = "Get child orders")
    @GetMapping("/child-orders")
    public Result<IPage<Order>> getChildOrders(@RequestParam(defaultValue = "1") Integer page,
                                               @RequestParam(defaultValue = "10") Integer size,
                                               @RequestParam(required = false) String status) {
        Long childId = StpUtil.getLoginIdAsLong();
        assertRole(childId, "CHILD");
        refreshChildMockLogistics(childId, status);
        IPage<Order> result = orderService.getChildOrders(childId, page, size, status);
        refreshMockLogistics(result);
        return Result.success(result);
    }

    @Operation(summary = "Get parent orders")
    @GetMapping("/parent-orders")
    public Result<IPage<Order>> getParentOrders(@RequestParam(defaultValue = "1") Integer page,
                                                @RequestParam(defaultValue = "10") Integer size,
                                                @RequestParam(required = false) String status) {
        Long parentId = StpUtil.getLoginIdAsLong();
        assertRole(parentId, "PARENT");
        refreshParentMockLogistics(parentId, status);
        IPage<Order> result = orderService.getParentOrders(parentId, page, size, status);
        refreshMockLogistics(result);
        return Result.success(result);
    }

    @Operation(summary = "Get order detail")
    @GetMapping("/{orderId}")
    public Result<Order> getOrderDetail(@PathVariable Long orderId) {
        Long userId = StpUtil.getLoginIdAsLong();
        User user = userService.getById(userId);
        if (user == null) {
            throw new RuntimeException("user not found");
        }

        Order order = loadAuthorizedOrder(orderId, user);
        refreshMockLogistics(order);
        return Result.success(order);
    }

    @Operation(summary = "Get order logistics")
    @GetMapping("/{orderId}/logistics")
    public Result<OrderLogisticsDTO> getOrderLogistics(@PathVariable Long orderId,
                                                       @RequestParam(defaultValue = "true") boolean refresh) {
        Long userId = StpUtil.getLoginIdAsLong();
        User user = userService.getById(userId);
        if (user == null) {
            throw new RuntimeException("user not found");
        }
        Order order = loadAuthorizedOrder(orderId, user);
        return Result.success(logisticsService.getOrderLogistics(order, refresh));
    }

    @Operation(summary = "Generate greeting card")
    @PostMapping("/{orderId}/greeting")
    public Result<String> generateGreeting(@PathVariable Long orderId) {
        Long childId = StpUtil.getLoginIdAsLong();
        assertRole(childId, "CHILD");
        return Result.success(orderService.generateGreetingCard(orderId, childId));
    }

    @Operation(summary = "Confirm receive")
    @PostMapping("/{orderId}/confirm-receive")
    public Result<Order> confirmReceive(@PathVariable Long orderId) {
        Long userId = StpUtil.getLoginIdAsLong();
        User user = userService.getById(userId);
        if (user == null) {
            throw new RuntimeException("user not found");
        }
        loadAuthorizedOrder(orderId, user);
        Order order = orderService.confirmReceive(orderId, userId);
        return Result.success("Receive confirmed", order);
    }

    @Operation(summary = "Request refund")
    @PostMapping("/{id}/refund")
    public Result<Order> requestRefund(@PathVariable Long id, @RequestBody RefundRequestDTO dto) {
        Long userId = StpUtil.getLoginIdAsLong();
        Order order = orderService.requestRefund(
                id,
                userId,
                dto == null ? null : dto.getReason(),
                dto == null ? null : dto.getImages()
        );
        return Result.success("Refund requested", order);
    }

    @Operation(summary = "Upload refund proof image")
    @PostMapping(value = "/refund/upload-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<Map<String, String>> uploadRefundImage(@RequestParam("file") MultipartFile file,
                                                         HttpServletRequest request) {
        Long userId = StpUtil.getLoginIdAsLong();
        if (userService.getById(userId) == null) {
            throw new RuntimeException("user not found");
        }
        return Result.success("Upload success", imageUploadService.uploadImage(file, request));
    }

    @Operation(summary = "Approve refund")
    @PostMapping("/{id}/refund-approve")
    public Result<Order> approveRefund(@PathVariable Long id) {
        Long childId = StpUtil.getLoginIdAsLong();
        assertRole(childId, "CHILD");
        Order order = orderService.approveRefund(id, childId);
        return Result.success("Refund approved", order);
    }

    @Operation(summary = "Reject refund")
    @PostMapping("/{id}/refund-reject")
    public Result<Order> rejectRefund(@PathVariable Long id) {
        Long childId = StpUtil.getLoginIdAsLong();
        assertRole(childId, "CHILD");
        Order order = orderService.rejectRefund(id, childId);
        return Result.success("Refund rejected", order);
    }

    private void assertRole(Long userId, String expectedRole) {
        User user = userService.getById(userId);
        if (user == null || !expectedRole.equals(user.getRole())) {
            throw new RuntimeException("no permission to access this api");
        }
    }

    private Order loadAuthorizedOrder(Long orderId, User user) {
        Order order = orderService.getById(orderId);
        if (order == null) {
            throw new RuntimeException("order not found");
        }

        Long userId = user.getId();
        if ("CHILD".equals(user.getRole()) && !userId.equals(order.getChildId())) {
            throw new RuntimeException("no permission to view this order");
        }
        if ("PARENT".equals(user.getRole()) && !userId.equals(order.getParentId())) {
            throw new RuntimeException("no permission to view this order");
        }
        return order;
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

    private void refreshChildMockLogistics(Long childId, String status) {
        if (childId == null || (StringUtils.hasText(status) && !"SHIPPED".equals(status))) {
            return;
        }
        orderService.list(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Order>()
                        .eq(Order::getChildId, childId)
                        .eq(Order::getStatus, "SHIPPED")
                        .eq(Order::getLogisticsProvider, LogisticsProperties.MOCK_PROVIDER)
                        .isNotNull(Order::getTrackingNo)
                        .ne(Order::getTrackingNo, ""))
                .forEach(logisticsService::tryRefreshOrderLogistics);
    }

    private void refreshParentMockLogistics(Long parentId, String status) {
        if (parentId == null || (StringUtils.hasText(status) && !"SHIPPED".equals(status))) {
            return;
        }
        orderService.list(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Order>()
                        .eq(Order::getParentId, parentId)
                        .eq(Order::getStatus, "SHIPPED")
                        .eq(Order::getLogisticsProvider, LogisticsProperties.MOCK_PROVIDER)
                        .isNotNull(Order::getTrackingNo)
                        .ne(Order::getTrackingNo, ""))
                .forEach(logisticsService::tryRefreshOrderLogistics);
    }
}
