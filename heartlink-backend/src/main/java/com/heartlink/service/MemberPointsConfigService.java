package com.heartlink.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.heartlink.entity.MemberPointsConfig;

public interface MemberPointsConfigService extends IService<MemberPointsConfig> {

    MemberPointsConfig getConfig();

    int getDailyCheckInPoints();

    MemberPointsConfig saveDailyCheckInPoints(Integer dailyCheckInPoints);
}
