package com.heartlink.service.impl;

import com.heartlink.dto.CreateOrderDTO;
import com.heartlink.dto.PayOrderDTO;
import com.heartlink.entity.Address;
import com.heartlink.service.AddressService;
import com.heartlink.entity.Coupon;
import com.heartlink.entity.FamilyBind;
import com.heartlink.entity.Order;
import com.heartlink.entity.Product;
import com.heartlink.entity.SmartShelf;
import com.heartlink.entity.Supplier;
import com.heartlink.entity.User;
import com.heartlink.entity.UserCoupon;
import com.heartlink.mapper.UserCouponMapper;
import com.heartlink.service.AiService;
import com.heartlink.service.CouponService;
import com.heartlink.service.FamilyBindService;
import com.heartlink.service.MemberPointsService;
import com.heartlink.service.ProductService;
import com.heartlink.service.SmartShelfService;
import com.heartlink.service.SupplierService;
import com.heartlink.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    @Mock
    private SmartShelfService smartShelfService;
    @Mock
    private ProductService productService;
    @Mock
    private AiService aiService;
    @Mock
    private AddressService addressService;
    @Mock
    private FamilyBindService familyBindService;
    @Mock
    private CouponService couponService;
    @Mock
    private MemberPointsService memberPointsService;
    @Mock
    private UserCouponMapper userCouponMapper;
    @Mock
    private SupplierService supplierService;
    @Mock
    private UserService userService;

    private OrderServiceImpl orderService;

    @BeforeEach
    void setUp() {
        orderService = spy(new OrderServiceImpl(
                smartShelfService,
                productService,
                aiService,
                addressService,
                familyBindService,
                couponService,
                memberPointsService,
                userCouponMapper,
                supplierService,
                userService
        ));

        Address address = new Address();
        address.setUserId(10L);
        address.setReceiverName("测试收货人");
        address.setReceiverPhone("13800138000");
        address.setProvince("山东省");
        address.setCity("烟台市");
        address.setDistrict("莱阳市");
        address.setDetailAddress("城厢街道 1 号");
        address.setIsDefault(1);
        lenient().when(addressService.getAddressList(anyLong())).thenReturn(List.of(address));

        lenient().when(userService.getById(anyLong())).thenAnswer(invocation -> {
            Long id = invocation.getArgument(0);
            User user = new User();
            user.setId(id);
            user.setCreditScore(70);
            return user;
        });
    }

    @Test
    void createOrder_shouldThrowWhenChildNotOwner() {
        CreateOrderDTO dto = new CreateOrderDTO();
        dto.setShelfId(100L);

        SmartShelf shelf = new SmartShelf();
        shelf.setId(100L);
        shelf.setChildId(999L);
        shelf.setParentId(1L);
        shelf.setReaction("LIKE");
        when(smartShelfService.getById(100L)).thenReturn(shelf);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> orderService.createOrder(dto, 10L));
        assertEquals("no permission to operate this shelf item", ex.getMessage());
        verify(productService, never()).getById(anyLong());
    }

    @Test
    void createOrder_shouldThrowWhenReactionNotLike() {
        CreateOrderDTO dto = new CreateOrderDTO();
        dto.setShelfId(101L);

        SmartShelf shelf = new SmartShelf();
        shelf.setId(101L);
        shelf.setChildId(10L);
        shelf.setParentId(1L);
        shelf.setReaction("PENDING");
        when(smartShelfService.getById(101L)).thenReturn(shelf);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> orderService.createOrder(dto, 10L));
        assertEquals("only liked shelf items can create orders", ex.getMessage());
        verify(productService, never()).getById(anyLong());
    }

    @Test
    void createOrder_shouldAllowBoundChildToOrderParentCollectedItem() {
        CreateOrderDTO dto = new CreateOrderDTO();
        dto.setShelfId(102L);
        dto.setQuantity(2);

        SmartShelf shelf = new SmartShelf();
        shelf.setId(102L);
        shelf.setChildId(1L);
        shelf.setParentId(1L);
        shelf.setProductId(8L);
        shelf.setReaction("LIKE");
        when(smartShelfService.getById(102L)).thenReturn(shelf);

        FamilyBind bind = new FamilyBind();
        bind.setChildId(10L);
        bind.setParentId(1L);
        when(familyBindService.getParentsByChildId(10L)).thenReturn(List.of(bind));

        Product product = new Product();
        product.setId(8L);
        product.setName("Health Product");
        product.setPrice(new BigDecimal("88.00"));
        product.setImages(List.of("/static/products/oatmeal.png"));
        when(productService.getById(8L)).thenReturn(product);
        doReturn(true).when(orderService).save(any(Order.class));

        Order result = orderService.createOrder(dto, 10L);

        assertEquals(1L, result.getParentId());
        assertEquals(10L, result.getChildId());
        assertEquals(8L, result.getProductId());
        assertEquals(2, result.getQuantity());
        assertEquals("PENDING_PAY", result.getStatus());
        verify(orderService).save(any(Order.class));
    }

    @Test
    void createOrder_shouldAllowDirectProductOrderForBoundParent() {
        CreateOrderDTO dto = new CreateOrderDTO();
        dto.setProductId(9L);
        dto.setParentId(1L);
        dto.setQuantity(3);

        FamilyBind bind = new FamilyBind();
        bind.setChildId(10L);
        bind.setParentId(1L);
        when(familyBindService.getParentsByChildId(10L)).thenReturn(List.of(bind));

        Product product = new Product();
        product.setId(9L);
        product.setName("Walker");
        product.setPrice(new BigDecimal("129.00"));
        product.setImages(List.of("/static/products/cane.png"));
        when(productService.getById(9L)).thenReturn(product);
        when(aiService.generateGreetingCard("Walker", "Mom", null)).thenReturn("Take care");
        doReturn(true).when(orderService).save(any(Order.class));

        Order result = orderService.createOrder(dto, 10L);

        assertEquals(1L, result.getParentId());
        assertEquals(10L, result.getChildId());
        assertEquals(9L, result.getProductId());
        assertEquals(3, result.getQuantity());
        assertEquals("PENDING_PAY", result.getStatus());
        assertEquals("Take care", result.getGreetingCard());
        assertEquals(0, new BigDecimal("387.00").compareTo(result.getTotalAmount()));
        verify(orderService).save(any(Order.class));
    }

    @Test
    void createOrder_shouldThrowWhenDirectOrderParentNotBound() {
        CreateOrderDTO dto = new CreateOrderDTO();
        dto.setProductId(9L);
        dto.setParentId(2L);

        FamilyBind bind = new FamilyBind();
        bind.setChildId(10L);
        bind.setParentId(1L);
        when(familyBindService.getParentsByChildId(10L)).thenReturn(List.of(bind));

        Product product = new Product();
        product.setId(9L);
        product.setName("Walker");
        product.setPrice(new BigDecimal("129.00"));
        when(productService.getById(9L)).thenReturn(product);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> orderService.createOrder(dto, 10L));
        assertEquals("no permission to order for this parent", ex.getMessage());
        verify(orderService, never()).save(any(Order.class));
    }

    @Test
    void createOrder_shouldSnapshotSupplierInfoWhenProductUsesSupplyPool() {
        CreateOrderDTO dto = new CreateOrderDTO();
        dto.setProductId(12L);
        dto.setParentId(1L);

        FamilyBind bind = new FamilyBind();
        bind.setChildId(10L);
        bind.setParentId(1L);
        when(familyBindService.getParentsByChildId(10L)).thenReturn(List.of(bind));

        Product product = new Product();
        product.setId(12L);
        product.setName("Massage Pillow");
        product.setPrice(new BigDecimal("299.00"));
        product.setSupplierId(88L);
        product.setSourceType("CURATED_SUPPLIER");
        product.setExternalSkuId("SKU-8801");
        product.setDeliveryMode("SUPPLIER_DIRECT");
        when(productService.getById(12L)).thenReturn(product);

        Supplier supplier = new Supplier();
        supplier.setId(88L);
        supplier.setName("SilverCare");
        supplier.setSettlementMode("MONTHLY");
        when(supplierService.getById(88L)).thenReturn(supplier);

        doReturn(true).when(orderService).save(any(Order.class));

        Order result = orderService.createOrder(dto, 10L);

        assertEquals(88L, result.getSupplierId());
        assertEquals("SilverCare", result.getSupplierName());
        assertEquals("CURATED_SUPPLIER", result.getSourceType());
        assertEquals("SKU-8801", result.getExternalSkuId());
        assertEquals("MONTHLY", result.getSettlementMode());
        assertEquals("DIRECT_SHIP", result.getProcurementStatus());
    }

    @Test
    void generateGreetingCard_shouldThrowWhenChildNotOwner() {
        Order order = new Order();
        order.setId(1L);
        order.setChildId(88L);
        doReturn(order).when(orderService).getById(1L);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> orderService.generateGreetingCard(1L, 10L));
        assertEquals("no permission to operate this order", ex.getMessage());
        verify(aiService, never()).generateGreetingCard(any(), any(), any());
    }

    @Test
    void confirmReceive_shouldThrowWhenStatusNotShipped() {
        Order order = new Order();
        order.setId(2L);
        order.setParentId(1L);
        order.setStatus("PAID");
        doReturn(order).when(orderService).getById(2L);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> orderService.confirmReceive(2L, 1L));
        assertEquals("cannot confirm receive for current order status", ex.getMessage());
        verify(orderService, never()).updateById(any(Order.class));
    }

    @Test
    void confirmReceive_shouldThrowWhenParentNotOwner() {
        Order order = new Order();
        order.setId(3L);
        order.setParentId(2L);
        order.setChildId(10L);
        order.setStatus("SHIPPED");
        doReturn(order).when(orderService).getById(3L);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> orderService.confirmReceive(3L, 1L));
        assertEquals("order not found", ex.getMessage());
    }

    @Test
    void confirmReceive_shouldCompleteOrderWhenValid() {
        Order order = new Order();
        order.setId(4L);
        order.setParentId(1L);
        order.setChildId(10L);
        order.setStatus("SHIPPED");
        doReturn(order).when(orderService).getById(4L);
        doReturn(true).when(orderService).updateById(any(Order.class));

        Order result = orderService.confirmReceive(4L, 1L);

        assertEquals("COMPLETED", result.getStatus());
        assertNotNull(result.getCompletedAt());
        assertEquals("SIGNED", result.getLogisticsStatus());
        assertEquals("已签收", result.getLogisticsStatusText());
        verify(orderService).updateById(order);
    }

    @Test
    void confirmReceive_shouldAllowChildOwner() {
        Order order = new Order();
        order.setId(6L);
        order.setParentId(1L);
        order.setChildId(10L);
        order.setStatus("SHIPPED");
        doReturn(order).when(orderService).getById(6L);
        doReturn(true).when(orderService).updateById(any(Order.class));

        Order result = orderService.confirmReceive(6L, 10L);

        assertEquals("COMPLETED", result.getStatus());
        assertEquals("SIGNED", result.getLogisticsStatus());
        verify(orderService).updateById(order);
    }

    @Test
    void confirmReceive_shouldThrowWhenTrackedOrderNotDelivered() {
        Order order = new Order();
        order.setId(7L);
        order.setParentId(1L);
        order.setChildId(10L);
        order.setStatus("SHIPPED");
        order.setTrackingNo("YT20260413001");
        order.setLogisticsStatus("IN_TRANSIT");
        doReturn(order).when(orderService).getById(7L);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> orderService.confirmReceive(7L, 10L));

        assertEquals("order has not been delivered yet", ex.getMessage());
        verify(orderService, never()).updateById(any(Order.class));
    }

    @Test
    void requestRefund_shouldAllowDirectRefundWithoutImages() {
        Order order = new Order();
        order.setId(8L);
        order.setChildId(10L);
        order.setParentId(1L);
        order.setStatus("SHIPPED");
        doReturn(order).when(orderService).getById(8L);
        doReturn(true).when(orderService).updateById(any(Order.class));

        Order result = orderService.requestRefund(8L, 10L, "物流太慢", List.of());

        assertEquals("REQUESTED", result.getRefundStatus());
        assertEquals("物流太慢", result.getRefundReason());
        assertEquals(null, result.getRefundImages());
        verify(orderService).updateById(order);
    }

    @Test
    void requestRefund_shouldAutoApproveWhenCreditScoreAboveNinety() {
        Order order = new Order();
        order.setId(18L);
        order.setChildId(10L);
        order.setParentId(1L);
        order.setStatus("SHIPPED");
        doReturn(order).when(orderService).getById(18L);
        doReturn(true).when(orderService).updateById(any(Order.class));

        User user = new User();
        user.setId(10L);
        user.setCreditScore(95);
        when(userService.getById(10L)).thenReturn(user);

        Order result = orderService.requestRefund(18L, 10L, "", List.of());

        assertEquals("APPROVED", result.getRefundStatus());
        assertEquals("CANCELLED", result.getStatus());
        assertEquals(95, result.getRefundCreditScore());
        assertNotNull(result.getRefundAt());
        verify(orderService).updateById(order);
    }

    @Test
    void requestRefund_shouldAutoApproveSignedOrderForHighTrustUserWithoutImages() {
        Order order = new Order();
        order.setId(19L);
        order.setChildId(10L);
        order.setParentId(1L);
        order.setStatus("COMPLETED");
        order.setLogisticsStatus("SIGNED");
        doReturn(order).when(orderService).getById(19L);
        doReturn(true).when(orderService).updateById(any(Order.class));

        User user = new User();
        user.setId(10L);
        user.setCreditScore(96);
        when(userService.getById(10L)).thenReturn(user);

        Order result = orderService.requestRefund(19L, 10L, null, List.of());

        assertEquals("APPROVED", result.getRefundStatus());
        assertEquals("CANCELLED", result.getStatus());
        assertEquals(96, result.getRefundCreditScore());
        verify(orderService).updateById(order);
    }

    @Test
    void requestRefund_shouldRequireImagesAfterSignOff() {
        Order order = new Order();
        order.setId(9L);
        order.setChildId(10L);
        order.setParentId(1L);
        order.setStatus("COMPLETED");
        order.setLogisticsStatus("SIGNED");
        doReturn(order).when(orderService).getById(9L);

        RuntimeException ex = assertThrows(
                RuntimeException.class,
                () -> orderService.requestRefund(9L, 10L, "商品破损", List.of())
        );

        assertEquals("signed orders require refund proof images", ex.getMessage());
        verify(orderService, never()).updateById(any(Order.class));
    }

    @Test
    void approveRefund_shouldRestoreInventoryAndReduceSalesWhenChildApproves() {
        Order order = new Order();
        order.setId(10L);
        order.setChildId(10L);
        order.setParentId(1L);
        order.setProductId(12L);
        order.setQuantity(2);
        order.setStatus("SHIPPED");
        order.setRefundStatus("REQUESTED");
        doReturn(order).when(orderService).getById(10L);
        doReturn(true).when(orderService).updateById(any(Order.class));

        Order result = orderService.approveRefund(10L, 10L);

        assertEquals("APPROVED", result.getRefundStatus());
        assertEquals("SHIPPED", result.getStatus());
        assertNotNull(result.getRefundAt());
        verify(productService).adjustInventoryAndSales(12L, 2, -2);
        verify(orderService).updateById(order);
    }

    @Test
    void payOrder_shouldApplyMemberCouponAndPoints() {
        Order order = new Order();
        order.setId(5L);
        order.setChildId(10L);
        order.setProductId(8L);
        order.setQuantity(1);
        order.setStatus("PENDING_PAY");
        order.setTotalAmount(new BigDecimal("100.00"));
        doReturn(order).when(orderService).getById(5L);
        doReturn(true).when(orderService).updateById(any(Order.class));

        Product product = new Product();
        product.setId(8L);
        product.setCategoryId(3L);
        when(productService.getById(8L)).thenReturn(product);

        when(memberPointsService.calculateDiscount(10L, new BigDecimal("100.00")))
                .thenReturn(new BigDecimal("95.00"));
        when(memberPointsService.usePointsForDiscount(10L, 1000))
                .thenReturn(new BigDecimal("10.00"));

        UserCoupon userCoupon = new UserCoupon();
        userCoupon.setId(6L);
        userCoupon.setUserId(10L);
        userCoupon.setCouponId(16L);
        userCoupon.setStatus("UNUSED");
        when(userCouponMapper.selectById(6L)).thenReturn(userCoupon);
        when(userCouponMapper.updateById(any(UserCoupon.class))).thenReturn(1);

        Coupon coupon = new Coupon();
        coupon.setId(16L);
        coupon.setName("Full reduction");
        coupon.setType("FIXED");
        coupon.setValue(new BigDecimal("10.00"));
        coupon.setMinAmount(new BigDecimal("50.00"));
        coupon.setCategoryId(3L);
        coupon.setStatus(1);
        coupon.setStartTime(LocalDateTime.now().minusDays(1));
        coupon.setEndTime(LocalDateTime.now().plusDays(1));
        when(couponService.getById(16L)).thenReturn(coupon);

        PayOrderDTO dto = new PayOrderDTO();
        dto.setUserCouponId(6L);
        dto.setPointsToUse(1000);

        Order result = orderService.payOrder(5L, 10L, dto);

        assertEquals("PAID", result.getStatus());
        assertNotNull(result.getPaidAt());
        assertEquals(0, new BigDecimal("75.00").compareTo(result.getTotalAmount()));

        ArgumentCaptor<UserCoupon> captor = ArgumentCaptor.forClass(UserCoupon.class);
        verify(userCouponMapper).updateById(captor.capture());
        assertEquals("USED", captor.getValue().getStatus());
        assertEquals(5L, captor.getValue().getOrderId());
        assertNotNull(captor.getValue().getUsedAt());
        verify(productService).adjustInventoryAndSales(8L, -1, 1);
    }
}
