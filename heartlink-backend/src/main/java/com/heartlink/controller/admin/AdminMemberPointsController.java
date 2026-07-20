package com.heartlink.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.heartlink.common.Result;
import com.heartlink.dto.AdminCouponExchangeConfigDTO;
import com.heartlink.dto.AdminMemberPointsConfigDTO;
import com.heartlink.entity.Coupon;
import com.heartlink.entity.MemberPointsConfig;
import com.heartlink.service.CouponService;
import com.heartlink.service.MemberPointsConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "管理端积分管理")
@RestController
@RequestMapping("/api/admin/member-points")
@RequiredArgsConstructor
public class AdminMemberPointsController {

    private final MemberPointsConfigService memberPointsConfigService;
    private final CouponService couponService;

    @Operation(summary = "获取积分配置")
    @GetMapping("/config")
    public Result<MemberPointsConfig> getConfig() {
        return Result.success(memberPointsConfigService.getConfig());
    }

    @Operation(summary = "保存积分配置")
    @PostMapping("/config")
    public Result<MemberPointsConfig> saveConfig(@RequestBody AdminMemberPointsConfigDTO dto) {
        Integer dailyCheckInPoints = dto != null ? dto.getDailyCheckInPoints() : null;
        if (dailyCheckInPoints == null || dailyCheckInPoints < 0) {
            throw new RuntimeException("每日签到积分必须大于等于 0");
        }
        return Result.success("保存成功", memberPointsConfigService.saveDailyCheckInPoints(dailyCheckInPoints));
    }

    @Operation(summary = "获取可配置兑换的优惠券列表")
    @GetMapping("/exchange-coupons")
    public Result<List<Coupon>> exchangeCoupons() {
        List<Coupon> coupons = couponService.list(
                new LambdaQueryWrapper<Coupon>().orderByDesc(Coupon::getCreatedAt)
        );
        return Result.success(coupons);
    }

    @Operation(summary = "保存优惠券兑换配置")
    @PostMapping("/exchange-coupons/{id}")
    public Result<Coupon> saveExchangeConfig(@PathVariable Long id,
                                             @RequestBody AdminCouponExchangeConfigDTO dto) {
        if (dto == null) {
            throw new RuntimeException("兑换配置不能为空");
        }
        return Result.success(
                "保存成功",
                couponService.updateExchangeConfig(id, dto.getExchangeEnabled(), dto.getExchangePoints())
        );
    }
}
