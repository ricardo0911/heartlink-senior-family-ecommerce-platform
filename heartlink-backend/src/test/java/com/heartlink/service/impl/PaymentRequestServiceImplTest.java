package com.heartlink.service.impl;

import com.heartlink.dto.PayOrderDTO;
import com.heartlink.entity.Order;
import com.heartlink.entity.PaymentRequest;
import com.heartlink.service.FamilyBindService;
import com.heartlink.service.OrderService;
import com.heartlink.service.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentRequestServiceImplTest {

    @Mock
    private OrderService orderService;
    @Mock
    private FamilyBindService familyBindService;
    @Mock
    private ProductService productService;

    private PaymentRequestServiceImpl paymentRequestService;

    @BeforeEach
    void setUp() {
        paymentRequestService = spy(new PaymentRequestServiceImpl(orderService, familyBindService, productService));
    }

    @Test
    void payRequest_shouldDelegateSettlementToOrderPayment() {
        PaymentRequest request = new PaymentRequest();
        request.setId(9L);
        request.setOrderId(33L);
        request.setChildId(10L);
        request.setStatus("PENDING");
        request.setAmount(new BigDecimal("100.00"));
        doReturn(request).when(paymentRequestService).getById(9L);
        doReturn(true).when(paymentRequestService).updateById(any(PaymentRequest.class));

        PayOrderDTO dto = new PayOrderDTO();
        dto.setUserCouponId(12L);
        dto.setPointsToUse(500);

        Order paidOrder = new Order();
        paidOrder.setId(33L);
        paidOrder.setTotalAmount(new BigDecimal("68.00"));
        when(orderService.payOrder(33L, 10L, dto)).thenReturn(paidOrder);

        PaymentRequest result = paymentRequestService.payRequest(9L, 10L, dto);

        assertEquals("PAID", result.getStatus());
        assertEquals(0, new BigDecimal("68.00").compareTo(result.getAmount()));
        assertNotNull(result.getPaidAt());
        verify(orderService).payOrder(33L, 10L, dto);
    }
}
