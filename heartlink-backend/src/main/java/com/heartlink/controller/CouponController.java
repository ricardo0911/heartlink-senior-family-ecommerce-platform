package com.heartlink.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.heartlink.common.Result;
import com.heartlink.entity.Coupon;
import com.heartlink.entity.UserCoupon;
import com.heartlink.service.CouponService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

@Tag(name = "优惠券")
@RestController
@RequestMapping("/api/coupon")
@RequiredArgsConstructor
public class CouponController {

    private final CouponService couponService;

    @Operation(summary = "可领取/可兑换的优惠券")
    @GetMapping("/available")
    public Result<List<Coupon>> available() {
        Long userId = StpUtil.getLoginIdAsLong();
        return Result.success(couponService.getAvailableCoupons(userId));
    }

    @Operation(summary = "领取优惠券")
    @PostMapping("/{id}/receive")
    public Result<UserCoupon> receive(@PathVariable Long id) {
        Long userId = StpUtil.getLoginIdAsLong();
        UserCoupon userCoupon = couponService.receiveCoupon(id, userId);
        return Result.success("领取成功", userCoupon);
    }

    @Operation(summary = "积分兑换优惠券")
    @PostMapping("/{id}/exchange")
    public Result<UserCoupon> exchange(@PathVariable Long id) {
        Long userId = StpUtil.getLoginIdAsLong();
        UserCoupon userCoupon = couponService.exchangeCoupon(id, userId);
        return Result.success("兑换成功", userCoupon);
    }

    @Operation(summary = "我的优惠券")
    @GetMapping("/my")
    public Result<List<UserCoupon>> my() {
        Long userId = StpUtil.getLoginIdAsLong();
        return Result.success(couponService.getMyCoupons(userId));
    }

    @Operation(summary = "下单可用券")
    @GetMapping("/usable")
    public Result<List<UserCoupon>> usable(
            @RequestParam(required = false) BigDecimal amount,
            @RequestParam(required = false) Long categoryId) {
        Long userId = StpUtil.getLoginIdAsLong();
        return Result.success(couponService.getUsableCoupons(amount, categoryId, userId));
    }
}
