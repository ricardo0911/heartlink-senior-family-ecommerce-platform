package com.heartlink.service;

import com.heartlink.entity.User;

import java.math.BigDecimal;

public interface MemberPointsService {

    User getPointsInfo(Long userId);

    void addPoints(Long userId, Integer points, String reason);

    void deductPoints(Long userId, Integer points, String reason);

    BigDecimal usePointsForDiscount(Long userId, Integer points);

    BigDecimal calculateDiscount(Long userId, BigDecimal originalPrice);

    String getMemberLevelName(Integer level);

    void updateMemberLevel(Long userId);
}
