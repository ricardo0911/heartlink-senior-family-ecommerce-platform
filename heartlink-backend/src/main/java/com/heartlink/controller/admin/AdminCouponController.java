package com.heartlink.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.heartlink.common.Result;
import com.heartlink.dto.AdminIssueCouponDTO;
import com.heartlink.entity.Coupon;
import com.heartlink.entity.UserCoupon;
import com.heartlink.service.CouponService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "管理端优惠券管理")
@RestController
@RequestMapping("/api/admin/coupon")
@RequiredArgsConstructor
public class AdminCouponController {

    private final CouponService couponService;

    @Operation(summary = "分页查询优惠券")
    @GetMapping("/page")
    public Result<IPage<Coupon>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Integer status) {
        LambdaQueryWrapper<Coupon> wrapper = new LambdaQueryWrapper<>();
        if (status != null) {
            wrapper.eq(Coupon::getStatus, status);
        }
        wrapper.orderByDesc(Coupon::getCreatedAt);
        IPage<Coupon> result = couponService.page(new Page<>(page, size), wrapper);
        return Result.success(result);
    }

    @Operation(summary = "新增或更新优惠券")
    @PostMapping("/save")
    public Result<Coupon> save(@RequestBody Coupon coupon) {
        if (coupon.getId() != null) {
            couponService.updateById(coupon);
        } else {
            couponService.save(coupon);
        }
        return Result.success(coupon);
    }

    @Operation(summary = "更新优惠券状态")
    @PostMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        Coupon coupon = new Coupon();
        coupon.setId(id);
        coupon.setStatus(status);
        couponService.updateById(coupon);
        return Result.success(null);
    }

    @Operation(summary = "发放优惠券给指定用户")
    @PostMapping("/{id}/issue")
    public Result<UserCoupon> issueCoupon(@PathVariable Long id,
                                          @Valid @RequestBody AdminIssueCouponDTO dto) {
        return Result.success("发放成功", couponService.issueCouponToUser(id, dto.getUserId()));
    }
}
