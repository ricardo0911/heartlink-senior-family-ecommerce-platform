package com.heartlink.service.impl;

import com.heartlink.entity.Coupon;
import com.heartlink.entity.User;
import com.heartlink.entity.UserCoupon;
import com.heartlink.mapper.UserCouponMapper;
import com.heartlink.service.MemberPointsService;
import com.heartlink.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CouponServiceImplTest {

    @Mock
    private UserCouponMapper userCouponMapper;
    @Mock
    private UserService userService;
    @Mock
    private MemberPointsService memberPointsService;

    private CouponServiceImpl couponService;

    @BeforeEach
    void setUp() {
        couponService = spy(new CouponServiceImpl(userCouponMapper, userService, memberPointsService));
    }

    @Test
    void getMyCoupons_shouldAttachCouponMetadata() {
        UserCoupon userCoupon = new UserCoupon();
        userCoupon.setId(1L);
        userCoupon.setUserId(10L);
        userCoupon.setCouponId(20L);
        userCoupon.setStatus("UNUSED");
        when(userCouponMapper.selectList(any())).thenReturn(List.of(userCoupon));

        Coupon coupon = new Coupon();
        coupon.setId(20L);
        coupon.setName("Care coupon");
        coupon.setType("FIXED");
        coupon.setValue(new BigDecimal("9.00"));
        coupon.setMinAmount(new BigDecimal("51.00"));
        coupon.setCategoryId(6L);
        coupon.setEndTime(LocalDateTime.now().plusDays(3));
        doReturn(List.of(coupon)).when(couponService).listByIds(any());

        List<UserCoupon> result = couponService.getMyCoupons(10L);

        assertEquals(1, result.size());
        assertEquals("Care coupon", result.get(0).getName());
        assertEquals("FIXED", result.get(0).getType());
        assertEquals(0, new BigDecimal("9.00").compareTo(result.get(0).getValue()));
        assertEquals(0, new BigDecimal("51.00").compareTo(result.get(0).getMinAmount()));
        assertEquals(6L, result.get(0).getCategoryId());
        assertEquals(coupon.getEndTime(), result.get(0).getExpireTime());
    }

    @Test
    void issueCouponToUser_shouldRejectAdminRecipient() {
        User admin = new User();
        admin.setId(1L);
        admin.setRole("ADMIN");
        admin.setStatus(1);
        when(userService.getById(1L)).thenReturn(admin);

        assertThrows(RuntimeException.class, () -> couponService.issueCouponToUser(2L, 1L));
    }
}
