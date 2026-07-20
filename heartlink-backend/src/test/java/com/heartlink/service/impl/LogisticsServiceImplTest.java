package com.heartlink.service.impl;

import com.heartlink.config.LogisticsProperties;
import com.heartlink.dto.OrderLogisticsDTO;
import com.heartlink.entity.Order;
import com.heartlink.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class LogisticsServiceImplTest {

    @Mock
    private OrderService orderService;

    private LogisticsProperties properties;
    private LogisticsServiceImpl logisticsService;

    @BeforeEach
    void setUp() {
        properties = new LogisticsProperties();
        logisticsService = new LogisticsServiceImpl(properties, orderService);
    }

    @Test
    void getOrderLogistics_shouldReturnStoredWaybillWhenThirdPartyDisabled() {
        Order order = buildShippedOrder();
        order.setLogisticsProvider(LogisticsProperties.MANUAL_PROVIDER);
        order.setLogisticsStatus(LogisticsProperties.MANUAL_PROVIDER);

        OrderLogisticsDTO dto = logisticsService.getOrderLogistics(order, true);

        assertFalse(dto.getQueryEnabled());
        assertFalse(dto.getRefreshed());
        assertEquals("物流查询未配置，已记录运单信息", dto.getMessage());
        assertEquals(LogisticsProperties.MANUAL_PROVIDER, dto.getLogisticsProvider());
        assertEquals("已发货，未启用第三方物流查询", dto.getLogisticsStatusText());
        assertEquals(1, dto.getTraces().size());
        assertEquals("圆通速递：已发货，未启用第三方物流查询", dto.getTraces().get(0).getAcceptStation());
        verify(orderService, never()).updateById(any(Order.class));
    }

    @Test
    void getOrderLogistics_shouldExposeConfiguredQueryStateWithoutRefreshing() {
        properties.setEnabled(true);
        properties.getKdniao().setEnabled(true);
        properties.getKdniao().setEbusinessId("demo-id");
        properties.getKdniao().setAppKey("demo-key");
        properties.getKdniao().setBaseUrl("https://example.com/kdniao");

        OrderLogisticsDTO dto = logisticsService.getOrderLogistics(buildShippedOrder(), false);

        assertTrue(dto.getQueryEnabled());
        assertFalse(dto.getRefreshed());
        assertNull(dto.getMessage());
        assertEquals("KDNIAO", properties.resolveProviderCode());
    }

    @Test
    void getOrderLogistics_shouldGenerateMockTracesWhenMockProviderEnabled() {
        properties.setEnabled(true);
        properties.setProvider("mock");

        Order order = buildShippedOrder();
        order.setShippedAt(LocalDateTime.now().minusSeconds(31));
        order.setReceiverAddress("浙江省杭州市西湖区文三路 77 号");

        OrderLogisticsDTO dto = logisticsService.getOrderLogistics(order, true);

        assertTrue(dto.getQueryEnabled());
        assertTrue(dto.getRefreshed());
        assertEquals("MOCK", dto.getLogisticsProvider());
        assertEquals("DELIVERED", dto.getLogisticsStatus());
        assertEquals("商品已送达，请提醒收货人确认签收", dto.getMessage());
        assertTrue(dto.getTraces().size() >= 6);
        assertTrue(dto.getTraces().get(0).getAcceptStation().contains("浙江省杭州市西湖区文三路77号"));
        assertTrue(dto.getTraces().get(1).getAcceptStation().contains("配送站"));
        verify(orderService, times(1)).updateById(any(Order.class));
    }

    @Test
    void getOrderLogistics_shouldRepairCompletedOrdersBackToSigned() {
        properties.setEnabled(true);
        properties.setProvider("mock");

        Order order = buildCompletedOrderWithDeliveredLogistics();

        OrderLogisticsDTO dto = logisticsService.getOrderLogistics(order, true);

        assertFalse(dto.getRefreshed());
        assertEquals("SIGNED", dto.getLogisticsStatus());
        assertEquals("\u5df2\u7b7e\u6536", dto.getLogisticsStatusText());
        assertTrue(dto.getTraces().stream().anyMatch(item -> item != null
                && "\u6536\u8d27\u4eba\u5df2\u786e\u8ba4\u7b7e\u6536".equals(item.getAcceptStation())));
        verify(orderService, times(1)).updateById(order);
    }

    @Test
    void tryRefreshOrderLogistics_shouldNotDowngradeCompletedSignedOrders() {
        properties.setEnabled(true);
        properties.setProvider("mock");

        Order order = buildCompletedOrderWithDeliveredLogistics();

        logisticsService.tryRefreshOrderLogistics(order);

        assertEquals("SIGNED", order.getLogisticsStatus());
        assertEquals("\u5df2\u7b7e\u6536", order.getLogisticsStatusText());
        verify(orderService, times(1)).updateById(order);
    }

    private Order buildShippedOrder() {
        Order order = new Order();
        order.setId(1L);
        order.setOrderNo("HL20260413001");
        order.setStatus("SHIPPED");
        order.setParentId(2L);
        order.setChildId(1L);
        order.setExpressCompanyCode("YTO");
        order.setExpressCompanyName("圆通速递");
        order.setTrackingNo("YT20260413001");
        order.setLogisticsUpdatedAt(LocalDateTime.now());
        return order;
    }

    private Order buildCompletedOrderWithDeliveredLogistics() {
        Order order = buildShippedOrder();
        LocalDateTime completedAt = LocalDateTime.now().minusMinutes(5);
        order.setStatus("COMPLETED");
        order.setCompletedAt(completedAt);
        order.setLogisticsStatus("DELIVERED");
        order.setLogisticsStatusText("\u5df2\u9001\u8fbe\uff0c\u5f85\u7b7e\u6536");
        order.setLogisticsLastTrace("delivered");
        order.setLogisticsTraceJson("[]");
        order.setLogisticsUpdatedAt(completedAt.minusMinutes(1));
        return order;
    }
}
