package com.heartlink.service.impl;

import com.heartlink.entity.User;
import com.heartlink.mapper.UserMapper;
import com.heartlink.service.MemberPointsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@RequiredArgsConstructor
public class MemberPointsServiceImpl implements MemberPointsService {

    private static final int SILVER_THRESHOLD = 1000;
    private static final int GOLD_THRESHOLD = 5000;
    private static final int DIAMOND_THRESHOLD = 20000;

    private static final BigDecimal NORMAL_DISCOUNT = new BigDecimal("1.00");
    private static final BigDecimal SILVER_DISCOUNT = new BigDecimal("0.98");
    private static final BigDecimal GOLD_DISCOUNT = new BigDecimal("0.95");
    private static final BigDecimal DIAMOND_DISCOUNT = new BigDecimal("0.90");

    private static final int POINTS_TO_YUAN_RATIO = 100;

    private final UserMapper userMapper;

    @Override
    public User getPointsInfo(Long userId) {
        User user = userMapper.selectById(userId);
        if (user != null) {
            if (user.getPoints() == null) {
                user.setPoints(0);
            }
            if (user.getMemberLevel() == null) {
                user.setMemberLevel(0);
            }
        }
        return user;
    }

    @Override
    @Transactional
    public void addPoints(Long userId, Integer points, String reason) {
        if (points == null || points <= 0) {
            return;
        }

        User user = userMapper.selectById(userId);
        if (user == null) {
            return;
        }

        int currentPoints = user.getPoints() != null ? user.getPoints() : 0;
        user.setPoints(currentPoints + points);
        userMapper.updateById(user);
        updateMemberLevel(userId);
    }

    @Override
    @Transactional
    public void deductPoints(Long userId, Integer points, String reason) {
        if (points == null || points <= 0) {
            throw new RuntimeException("积分必须大于 0");
        }

        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        int currentPoints = user.getPoints() != null ? user.getPoints() : 0;
        if (currentPoints < points) {
            throw new RuntimeException("积分不足");
        }

        user.setPoints(currentPoints - points);
        userMapper.updateById(user);
    }

    @Override
    @Transactional
    public BigDecimal usePointsForDiscount(Long userId, Integer points) {
        deductPoints(userId, points, "积分抵扣订单金额");
        return new BigDecimal(points).divide(new BigDecimal(POINTS_TO_YUAN_RATIO), 2, RoundingMode.DOWN);
    }

    @Override
    public BigDecimal calculateDiscount(Long userId, BigDecimal originalPrice) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            return originalPrice;
        }

        int level = user.getMemberLevel() != null ? user.getMemberLevel() : 0;
        BigDecimal discountRate = getDiscountRate(level);
        return originalPrice.multiply(discountRate).setScale(2, RoundingMode.DOWN);
    }

    private BigDecimal getDiscountRate(int level) {
        switch (level) {
            case 1:
                return SILVER_DISCOUNT;
            case 2:
                return GOLD_DISCOUNT;
            case 3:
                return DIAMOND_DISCOUNT;
            default:
                return NORMAL_DISCOUNT;
        }
    }

    @Override
    public String getMemberLevelName(Integer level) {
        int safeLevel = level != null ? level : 0;
        switch (safeLevel) {
            case 1:
                return "银卡会员";
            case 2:
                return "金卡会员";
            case 3:
                return "钻石会员";
            default:
                return "普通会员";
        }
    }

    @Override
    @Transactional
    public void updateMemberLevel(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            return;
        }

        int points = user.getPoints() != null ? user.getPoints() : 0;
        int newLevel = 0;
        if (points >= DIAMOND_THRESHOLD) {
            newLevel = 3;
        } else if (points >= GOLD_THRESHOLD) {
            newLevel = 2;
        } else if (points >= SILVER_THRESHOLD) {
            newLevel = 1;
        }

        int currentLevel = user.getMemberLevel() != null ? user.getMemberLevel() : 0;
        if (newLevel > currentLevel) {
            user.setMemberLevel(newLevel);
            userMapper.updateById(user);
        }
    }
}
