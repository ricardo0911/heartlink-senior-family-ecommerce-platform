package com.heartlink.service;

public interface MemberCheckInService {

    void performDailyCheckIn(Long userId, Integer earnedPoints);

    boolean hasCheckedInToday(Long userId);
}
