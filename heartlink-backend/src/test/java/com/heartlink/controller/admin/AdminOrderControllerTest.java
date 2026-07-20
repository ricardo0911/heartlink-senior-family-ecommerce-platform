package com.heartlink.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.heartlink.common.Result;
import com.heartlink.config.LogisticsProperties;
import com.heartlink.dto.AdminShipOrderDTO;
import com.heartlink.entity.Order;
import com.heartlink.service.LogisticsService;
import com.heartlink.service.OrderService;
import com.heartlink.service.PaymentRequestService;
import com.heartlink.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminOrderControllerTest {

    @Mock
    private OrderService orderService;
    @Mock
    private LogisticsService logisticsService;
    @Mock
    private PaymentRequestService paymentRequestService;
    @Mock
    private UserService userService;

    private LogisticsProperties logisticsProperties;
    private AdminOrderController controller;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Order.class);
        logisticsProperties = new LogisticsProperties();
        controller = new AdminOrderController(orderService, logisticsService, logisticsProperties, paymentRequestService, userService);
        lenient().when(paymentRequestService.list(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        lenient().when(userService.listByIds(any(Collection.class))).thenReturn(List.of());
    }

    @Test
    void ship_shouldMarkManualLogisticsWhenThirdPartyDisabled() {
        Order order = buildPaidOrder();
        when(orderService.getById(1L)).thenReturn(order);
        when(orderService.updateById(any(Order.class))).thenReturn(true);

        Result<Order> result = controller.ship(1L, buildShipDto());

        assertEquals(200, result.getCode());
        assertEquals("SHIPPED", order.getStatus());
        assertNotNull(order.getShippedAt());
        assertEquals(LogisticsProperties.MANUAL_PROVIDER, order.getLogisticsProvider());
        assertEquals(LogisticsProperties.MANUAL_PROVIDER, order.getLogisticsStatus());
        assertEquals("已发货，未启用第三方物流查询", order.getLogisticsStatusText());
        assertTrue(order.getLogisticsLastTrace().contains("可凭运单号手动查询"));
        verify(orderService).updateById(order);
        verify(logisticsService).tryRefreshOrderLogistics(order);
    }

    @Test
    void ship_shouldUseKdniaoWhenThirdPartyConfigured() {
        logisticsProperties.setEnabled(true);
        logisticsProperties.getKdniao().setEnabled(true);
        logisticsProperties.getKdniao().setEbusinessId("demo-id");
        logisticsProperties.getKdniao().setAppKey("demo-key");
        logisticsProperties.getKdniao().setBaseUrl("https://example.com/kdniao");

        Order order = buildPaidOrder();
        when(orderService.getById(1L)).thenReturn(order);
        when(orderService.updateById(any(Order.class))).thenReturn(true);

        controller.ship(1L, buildShipDto());

        assertEquals("KDNIAO", order.getLogisticsProvider());
        assertEquals("PENDING", order.getLogisticsStatus());
        assertEquals("已发货，待物流更新", order.getLogisticsStatusText());
        assertTrue(order.getLogisticsLastTrace().contains("等待快递揽收"));
    }

    @Test
    void logisticsPage_shouldBuildWrapperWithTrackingFilters() {
        when(orderService.list(any(LambdaQueryWrapper.class))).thenReturn(List.of(buildShippedMockOrder()));
        Page<Order> page = new Page<>(1, 10);
        page.setRecords(List.of(buildPaidOrder()));
        page.setTotal(1);
        when(orderService.page(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);

        Result<IPage<Order>> result = controller.logisticsPage(1, 10, "SHIPPED", "IN_TRANSIT", "HL2026", "YT2026", "yto");

        assertEquals(200, result.getCode());
        assertNotNull(result.getData());
        assertEquals(1, result.getData().getTotal());

        ArgumentCaptor<LambdaQueryWrapper<Order>> wrapperCaptor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(orderService).page(any(Page.class), wrapperCaptor.capture());

        LambdaQueryWrapper<Order> wrapper = wrapperCaptor.getValue();
        String sqlSegment = wrapper.getSqlSegment();

        assertTrue(sqlSegment.contains("tracking_no"));
        assertTrue(sqlSegment.contains("express_company_code"));
        assertTrue(sqlSegment.contains("logistics_status"));
        assertTrue(wrapper.getParamNameValuePairs().containsValue("SHIPPED"));
        assertTrue(wrapper.getParamNameValuePairs().containsValue("IN_TRANSIT"));
        assertTrue(paramValuesContain(wrapper.getParamNameValuePairs().values(), "HL2026"));
        assertTrue(paramValuesContain(wrapper.getParamNameValuePairs().values(), "YT2026"));
        assertTrue(wrapper.getParamNameValuePairs().containsValue("YTO"));
    }

    @Test
    void logisticsPage_shouldRefreshMockOrdersBeforePaging() {
        Order refreshCandidate = buildShippedMockOrder();
        when(orderService.list(any(LambdaQueryWrapper.class))).thenReturn(List.of(refreshCandidate));

        Page<Order> page = new Page<>(1, 10);
        page.setRecords(List.of(refreshCandidate));
        page.setTotal(1);
        when(orderService.page(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);

        controller.logisticsPage(1, 10, null, "DELIVERED", "HL2026", "YT2026", "yto");

        InOrder inOrder = inOrder(orderService, logisticsService);
        inOrder.verify(orderService).list(any(LambdaQueryWrapper.class));
        inOrder.verify(logisticsService).tryRefreshOrderLogistics(refreshCandidate);
        inOrder.verify(orderService).page(any(Page.class), any(LambdaQueryWrapper.class));
    }

    @Test
    void refundApprove_shouldDelegateToAdminRefundFlow() {
        Order order = new Order();
        order.setId(3L);
        order.setRefundStatus("APPROVED");
        when(orderService.approveRefundByAdmin(3L)).thenReturn(order);

        Result<Void> result = controller.refundApprove(3L);

        assertEquals(200, result.getCode());
        verify(orderService).approveRefundByAdmin(3L);
    }

    private Order buildPaidOrder() {
        Order order = new Order();
        order.setId(1L);
        order.setStatus("PAID");
        return order;
    }

    private Order buildShippedMockOrder() {
        Order order = new Order();
        order.setId(2L);
        order.setStatus("SHIPPED");
        order.setLogisticsProvider(LogisticsProperties.MOCK_PROVIDER);
        order.setTrackingNo("YT20260413001");
        order.setOrderNo("HL2026");
        order.setExpressCompanyCode("YTO");
        return order;
    }

    private AdminShipOrderDTO buildShipDto() {
        AdminShipOrderDTO dto = new AdminShipOrderDTO();
        dto.setExpressCompanyCode("YTO");
        dto.setExpressCompanyName("圆通速递");
        dto.setTrackingNo("YT20260413001");
        return dto;
    }

    private boolean paramValuesContain(Collection<Object> values, String expectedFragment) {
        return values.stream()
                .map(String::valueOf)
                .anyMatch(value -> value.contains(expectedFragment));
    }
}
