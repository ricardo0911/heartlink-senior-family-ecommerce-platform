package com.heartlink.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.heartlink.entity.Coupon;
import com.heartlink.entity.User;
import com.heartlink.entity.UserCoupon;
import com.heartlink.mapper.CouponMapper;
import com.heartlink.mapper.UserCouponMapper;
import com.heartlink.service.CouponService;
import com.heartlink.service.MemberPointsService;
import com.heartlink.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CouponServiceImpl extends ServiceImpl<CouponMapper, Coupon> implements CouponService {

    private final UserCouponMapper userCouponMapper;
    private final UserService userService;
    private final MemberPointsService memberPointsService;

    @Override
    public List<Coupon> getAvailableCoupons(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        List<Coupon> coupons = list(new LambdaQueryWrapper<Coupon>()
                .eq(Coupon::getStatus, 1)
                .gt(Coupon::getRemainCount, 0)
                .le(Coupon::getStartTime, now)
                .ge(Coupon::getEndTime, now)
                .orderByDesc(Coupon::getCreatedAt));
        if (userId == null || coupons.isEmpty()) {
            return coupons;
        }

        Set<Long> receivedCouponIds = userCouponMapper.selectList(new LambdaQueryWrapper<UserCoupon>()
                        .select(UserCoupon::getCouponId)
                        .eq(UserCoupon::getUserId, userId))
                .stream()
                .map(UserCoupon::getCouponId)
                .collect(Collectors.toSet());
        if (receivedCouponIds.isEmpty()) {
            return coupons;
        }

        return coupons.stream()
                .filter(coupon -> !receivedCouponIds.contains(coupon.getId()))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public UserCoupon receiveCoupon(Long couponId, Long userId) {
        Coupon coupon = getCouponOrThrow(couponId);
        validateCouponWindow(coupon);
        if (isExchangeEnabled(coupon)) {
            throw new RuntimeException("该优惠券仅支持积分兑换");
        }
        return grantCouponToUser(coupon, userId);
    }

    @Override
    @Transactional
    public UserCoupon issueCouponToUser(Long couponId, Long userId) {
        User user = userService.getById(userId);
        if (user == null || user.getStatus() == null || user.getStatus() != 1) {
            throw new RuntimeException("target user not found");
        }
        if ("ADMIN".equalsIgnoreCase(user.getRole())) {
            throw new RuntimeException("cannot issue coupon to admin");
        }
        return grantCouponToUser(getCouponOrThrow(couponId), userId);
    }

    @Override
    @Transactional
    public UserCoupon exchangeCoupon(Long couponId, Long userId) {
        Coupon coupon = getCouponOrThrow(couponId);
        validateCouponWindow(coupon);
        if (!isExchangeEnabled(coupon)) {
            throw new RuntimeException("该优惠券暂不支持积分兑换");
        }

        int requiredPoints = coupon.getExchangePoints() != null ? coupon.getExchangePoints() : 0;
        if (requiredPoints <= 0) {
            throw new RuntimeException("兑换积分配置无效");
        }

        UserCoupon existing = findExistingCoupon(userId, coupon.getId());
        if (existing != null) {
            fillCouponInfo(existing, coupon, LocalDateTime.now());
            return existing;
        }

        memberPointsService.deductPoints(userId, requiredPoints, "积分兑换优惠券");
        return grantCouponToUser(coupon, userId);
    }

    @Override
    @Transactional
    public Coupon updateExchangeConfig(Long couponId, Integer exchangeEnabled, Integer exchangePoints) {
        Coupon coupon = getById(couponId);
        if (coupon == null) {
            throw new RuntimeException("coupon not found");
        }

        int enabled = exchangeEnabled != null && exchangeEnabled == 1 ? 1 : 0;
        int requiredPoints = exchangePoints != null ? exchangePoints : 0;
        if (enabled == 1 && requiredPoints <= 0) {
            throw new RuntimeException("启用积分兑换后，所需积分必须大于 0");
        }

        coupon.setExchangeEnabled(enabled);
        coupon.setExchangePoints(enabled == 1 ? requiredPoints : 0);
        updateById(coupon);
        return getById(couponId);
    }

    @Override
    public List<UserCoupon> getMyCoupons(Long userId) {
        List<UserCoupon> userCoupons = userCouponMapper.selectList(new LambdaQueryWrapper<UserCoupon>()
                .eq(UserCoupon::getUserId, userId)
                .orderByDesc(UserCoupon::getReceivedAt));
        return enrichCoupons(userCoupons);
    }

    @Override
    public List<UserCoupon> getUsableCoupons(BigDecimal amount, Long categoryId, Long userId) {
        List<UserCoupon> myCoupons = userCouponMapper.selectList(new LambdaQueryWrapper<UserCoupon>()
                .eq(UserCoupon::getUserId, userId)
                .eq(UserCoupon::getStatus, "UNUSED"));

        Set<Long> couponIds = myCoupons.stream()
                .map(UserCoupon::getCouponId)
                .collect(Collectors.toSet());
        if (couponIds.isEmpty()) {
            return List.of();
        }

        LocalDateTime now = LocalDateTime.now();
        Map<Long, Coupon> couponMap = listByIds(couponIds).stream()
                .filter(coupon -> coupon.getStatus() != null && coupon.getStatus() == 1)
                .filter(coupon -> coupon.getStartTime() == null || !coupon.getStartTime().isAfter(now))
                .filter(coupon -> coupon.getEndTime() == null || coupon.getEndTime().isAfter(now))
                .filter(coupon -> amount == null || coupon.getMinAmount() == null || amount.compareTo(coupon.getMinAmount()) >= 0)
                .filter(coupon -> categoryId == null || coupon.getCategoryId() == null || categoryId.equals(coupon.getCategoryId()))
                .collect(Collectors.toMap(Coupon::getId, Function.identity()));

        List<UserCoupon> usableCoupons = myCoupons.stream()
                .filter(userCoupon -> couponMap.containsKey(userCoupon.getCouponId()))
                .collect(Collectors.toList());
        return enrichCoupons(usableCoupons, couponMap, now);
    }

    private Coupon getCouponOrThrow(Long couponId) {
        Coupon coupon = getById(couponId);
        if (coupon == null || coupon.getStatus() == null || coupon.getStatus() != 1) {
            throw new RuntimeException("coupon not found");
        }
        return coupon;
    }

    private void validateCouponWindow(Coupon coupon) {
        LocalDateTime now = LocalDateTime.now();
        if (coupon.getStartTime() != null && coupon.getStartTime().isAfter(now)) {
            throw new RuntimeException("优惠券尚未开始");
        }
        if (coupon.getEndTime() != null && !coupon.getEndTime().isAfter(now)) {
            throw new RuntimeException("优惠券已过期");
        }
    }

    private boolean isExchangeEnabled(Coupon coupon) {
        return coupon.getExchangeEnabled() != null && coupon.getExchangeEnabled() == 1;
    }

    private UserCoupon grantCouponToUser(Coupon coupon, Long userId) {
        if (coupon.getRemainCount() == null || coupon.getRemainCount() <= 0) {
            throw new RuntimeException("coupon out of stock");
        }

        UserCoupon existing = findExistingCoupon(userId, coupon.getId());
        if (existing != null) {
            fillCouponInfo(existing, coupon, LocalDateTime.now());
            return existing;
        }

        coupon.setRemainCount(coupon.getRemainCount() - 1);
        updateById(coupon);

        UserCoupon userCoupon = new UserCoupon();
        userCoupon.setUserId(userId);
        userCoupon.setCouponId(coupon.getId());
        userCoupon.setStatus("UNUSED");
        userCoupon.setReceivedAt(LocalDateTime.now());
        userCouponMapper.insert(userCoupon);
        fillCouponInfo(userCoupon, coupon, LocalDateTime.now());
        return userCoupon;
    }

    private List<UserCoupon> enrichCoupons(List<UserCoupon> userCoupons) {
        if (userCoupons.isEmpty()) {
            return userCoupons;
        }

        Set<Long> couponIds = userCoupons.stream()
                .map(UserCoupon::getCouponId)
                .collect(Collectors.toSet());
        Map<Long, Coupon> couponMap = listByIds(couponIds).stream()
                .collect(Collectors.toMap(Coupon::getId, Function.identity()));
        return enrichCoupons(userCoupons, couponMap, LocalDateTime.now());
    }

    private List<UserCoupon> enrichCoupons(List<UserCoupon> userCoupons,
                                           Map<Long, Coupon> couponMap,
                                           LocalDateTime now) {
        for (UserCoupon userCoupon : userCoupons) {
            Coupon coupon = couponMap.get(userCoupon.getCouponId());
            if (coupon == null) {
                continue;
            }
            fillCouponInfo(userCoupon, coupon, now);
        }
        return userCoupons;
    }

    private UserCoupon findExistingCoupon(Long userId, Long couponId) {
        return userCouponMapper.selectOne(new LambdaQueryWrapper<UserCoupon>()
                .eq(UserCoupon::getUserId, userId)
                .eq(UserCoupon::getCouponId, couponId)
                .last("limit 1"));
    }

    private void fillCouponInfo(UserCoupon userCoupon, Coupon coupon, LocalDateTime now) {
        userCoupon.setName(coupon.getName());
        userCoupon.setType(coupon.getType());
        userCoupon.setValue(coupon.getValue());
        userCoupon.setMinAmount(coupon.getMinAmount());
        userCoupon.setCategoryId(coupon.getCategoryId());
        userCoupon.setStartTime(coupon.getStartTime());
        userCoupon.setExpireTime(coupon.getEndTime());

        if ("UNUSED".equals(userCoupon.getStatus())
                && coupon.getEndTime() != null
                && !coupon.getEndTime().isAfter(now)) {
            userCoupon.setStatus("EXPIRED");
        }
    }
}
