package com.heartlink.schedule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.heartlink.config.LogisticsProperties;
import com.heartlink.entity.Order;
import com.heartlink.service.LogisticsService;
import com.heartlink.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class MockLogisticsScheduler {

    private final LogisticsProperties logisticsProperties;
    private final OrderService orderService;
    private final LogisticsService logisticsService;

    @Scheduled(fixedDelay = 5000L, initialDelay = 5000L)
    public void refreshMockLogisticsProgress() {
        if (!logisticsProperties.isQueryEnabled() || !logisticsProperties.isMockProvider()) {
            return;
        }

        List<Order> activeOrders = orderService.list(new LambdaQueryWrapper<Order>()
                .isNotNull(Order::getTrackingNo)
                .ne(Order::getTrackingNo, "")
                .eq(Order::getLogisticsProvider, LogisticsProperties.MOCK_PROVIDER)
                .in(Order::getStatus, "SHIPPED", "COMPLETED")
                .and(wrapper -> wrapper.isNull(Order::getLogisticsStatus)
                        .or()
                        .ne(Order::getLogisticsStatus, "SIGNED")));

        for (Order order : activeOrders) {
            try {
                if (StringUtils.hasText(order.getTrackingNo())) {
                    logisticsService.tryRefreshOrderLogistics(order);
                }
            } catch (Exception e) {
                log.warn("mock logistics scheduler skipped orderId={}", order.getId(), e);
            }
        }
    }
}
