package com.heartlink.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.heartlink.entity.Coupon;
import com.heartlink.entity.UserCoupon;

import java.math.BigDecimal;
import java.util.List;

public interface CouponService extends IService<Coupon> {

    List<Coupon> getAvailableCoupons(Long userId);

    UserCoupon receiveCoupon(Long couponId, Long userId);

    UserCoupon issueCouponToUser(Long couponId, Long userId);

    UserCoupon exchangeCoupon(Long couponId, Long userId);

    Coupon updateExchangeConfig(Long couponId, Integer exchangeEnabled, Integer exchangePoints);

    List<UserCoupon> getMyCoupons(Long userId);

    List<UserCoupon> getUsableCoupons(BigDecimal amount, Long categoryId, Long userId);
}
