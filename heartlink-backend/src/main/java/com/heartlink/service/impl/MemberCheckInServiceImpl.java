package com.heartlink.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.heartlink.entity.MemberCheckInLog;
import com.heartlink.mapper.MemberCheckInLogMapper;
import com.heartlink.service.MemberCheckInService;
import com.heartlink.service.MemberPointsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class MemberCheckInServiceImpl implements MemberCheckInService {

    private final MemberCheckInLogMapper memberCheckInLogMapper;
    private final MemberPointsService memberPointsService;

    @Override
    @Transactional
    public void performDailyCheckIn(Long userId, Integer earnedPoints) {
        if (userId == null) {
            throw new RuntimeException("\u7528\u6237\u4e0d\u5b58\u5728");
        }
        if (earnedPoints == null || earnedPoints <= 0) {
            throw new RuntimeException("\u7b7e\u5230\u79ef\u5206\u914d\u7f6e\u65e0\u6548");
        }
        if (hasCheckedInToday(userId)) {
            throw new RuntimeException("\u4eca\u5929\u5df2\u7ecf\u7b7e\u5230\u8fc7\u4e86");
        }

        MemberCheckInLog log = new MemberCheckInLog();
        log.setUserId(userId);
        log.setCheckInDate(LocalDate.now());
        log.setEarnedPoints(earnedPoints);
        memberCheckInLogMapper.insert(log);

        memberPointsService.addPoints(userId, earnedPoints, "\u6bcf\u65e5\u7b7e\u5230");
    }

    @Override
    public boolean hasCheckedInToday(Long userId) {
        if (userId == null) {
            return false;
        }
        Long count = memberCheckInLogMapper.selectCount(new LambdaQueryWrapper<MemberCheckInLog>()
                .eq(MemberCheckInLog::getUserId, userId)
                .eq(MemberCheckInLog::getCheckInDate, LocalDate.now()));
        return count != null && count > 0;
    }
}
