package com.heartlink.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.heartlink.common.Result;
import com.heartlink.entity.HealthReminder;
import com.heartlink.entity.Order;
import com.heartlink.entity.PaymentRequest;
import com.heartlink.entity.Product;
import com.heartlink.service.OrderService;
import com.heartlink.service.PaymentRequestService;
import com.heartlink.service.ProductService;
import com.heartlink.service.ReminderService;
import com.heartlink.service.SettlementMetricsService;
import com.heartlink.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Tag(name = "管理端-仪表盘")
@RestController
@RequestMapping("/api/admin/dashboard")
@RequiredArgsConstructor
public class AdminDashboardController {

    private final UserService userService;
    private final ProductService productService;
    private final OrderService orderService;
    private final PaymentRequestService paymentRequestService;
    private final ReminderService reminderService;
    private final SettlementMetricsService settlementMetricsService;

    @Operation(summary = "统计数据")
    @GetMapping("/stats")
    public Result<Map<String, Object>> stats() {
        long userCount = userService.count();
        long orderCount = orderService.count();
        long productCount = productService.count();
        List<Order> pendingSettlementOrders = orderService.list(new LambdaQueryWrapper<Order>()
                .in(Order::getStatus, List.of("PAID", "SHIPPED")));
        long lowStockCount = productService.count(new LambdaQueryWrapper<Product>()
                .eq(Product::getStatus, 1)
                .le(Product::getStock, 10));
        long inventoryCount = productService.list().stream()
                .map(Product::getStock)
                .filter(Objects::nonNull)
                .mapToLong(Integer::longValue)
                .sum();
        long pendingRefundCount = orderService.count(new LambdaQueryWrapper<Order>()
                .eq(Order::getRefundStatus, "REQUESTED"));
        long pendingPaymentRequestCount = paymentRequestService.count(new LambdaQueryWrapper<PaymentRequest>()
                .eq(PaymentRequest::getStatus, "PENDING"));
        long activeReminderCount = reminderService.count(new LambdaQueryWrapper<HealthReminder>()
                .eq(HealthReminder::getEnabled, 1));

        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Order::getStatus, "COMPLETED");
        List<Order> completedOrders = orderService.list(wrapper);
        BigDecimal salesAmount = completedOrders.stream()
                .map(Order::getTotalAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal pendingSettlementAmount = pendingSettlementOrders.stream()
                .map(Order::getTotalAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<String, Object> settlementOverview = settlementMetricsService.buildOverview(null, null, null, null);

        Map<String, Object> data = new HashMap<>();
        data.put("userCount", userCount);
        data.put("orderCount", orderCount);
        data.put("productCount", productCount);
        data.put("inventoryCount", inventoryCount);
        data.put("lowStockCount", lowStockCount);
        data.put("pendingRefundCount", pendingRefundCount);
        data.put("pendingPaymentRequestCount", pendingPaymentRequestCount);
        data.put("activeReminderCount", activeReminderCount);
        data.put("salesAmount", salesAmount);
        data.put("pendingSettlementOrderCount", pendingSettlementOrders.size());
        data.put("pendingSettlementAmount", pendingSettlementAmount);
        data.put("thirdPartyIncome", settlementOverview.get("thirdPartyIncome"));
        data.put("selfOperatedSales", settlementOverview.get("selfOperatedSales"));
        data.put("selfOperatedGrossProfit", settlementOverview.get("selfOperatedGrossProfit"));
        data.put("refundReversalAmount", settlementOverview.get("refundReversalAmount"));
        data.put("netBusinessIncome", settlementOverview.get("netBusinessIncome"));
        data.put("missingCostOrderCount", settlementOverview.get("missingCostOrderCount"));
        data.put("missingTaxRateOrderCount", settlementOverview.get("missingTaxRateOrderCount"));
        return Result.success(data);
    }

    @Operation(summary = "最近订单")
    @GetMapping("/recent-orders")
    public Result<List<Order>> recentOrders() {
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(Order::getCreatedAt).last("LIMIT 10");
        List<Order> orders = orderService.list(wrapper);
        return Result.success(orders);
    }

    @Operation(summary = "销售趋势")
    @GetMapping("/sales-trend")
    public Result<List<Map<String, Object>>> salesTrend(@RequestParam(defaultValue = "7") int days) {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(Math.max(days, 1) - 1L);
        List<Map<String, Object>> trend = settlementMetricsService.buildTrend(startDate, endDate, "day", null, null);
        return Result.success(trend);
    }

    @Operation(summary = "库存报警")
    @GetMapping("/inventory-alerts")
    public Result<List<Map<String, Object>>> inventoryAlerts(@RequestParam(defaultValue = "10") int threshold,
                                                             @RequestParam(defaultValue = "8") int limit) {
        int safeThreshold = Math.max(1, threshold);
        int safeLimit = Math.min(Math.max(limit, 1), 20);

        List<Product> lowStockProducts = productService.list(new LambdaQueryWrapper<Product>()
                .eq(Product::getStatus, 1)
                .isNotNull(Product::getStock)
                .le(Product::getStock, safeThreshold));

        List<Map<String, Object>> alerts = lowStockProducts.stream()
                .sorted(Comparator
                        .comparing((Product item) -> item.getStock() == null ? Integer.MAX_VALUE : item.getStock())
                        .thenComparing((Product item) -> item.getSales() == null ? 0 : item.getSales(), Comparator.reverseOrder()))
                .limit(safeLimit)
                .map(this::buildInventoryAlertItem)
                .collect(Collectors.toList());

        return Result.success(alerts);
    }

    private Map<String, Object> buildInventoryAlertItem(Product product) {
        int stock = product.getStock() == null ? 0 : product.getStock();
        int sales = product.getSales() == null ? 0 : product.getSales();
        boolean critical = stock <= 3;

        Map<String, Object> item = new HashMap<>();
        item.put("id", product.getId());
        item.put("name", product.getName());
        item.put("stock", stock);
        item.put("sales", sales);
        item.put("level", critical ? "CRITICAL" : "WARNING");
        item.put("levelLabel", critical ? "告急" : "预警");
        item.put("suggestion", stock <= 0 ? "立即补货或下架" : (critical ? "优先补货" : "尽快补货"));
        return item;
    }
}
