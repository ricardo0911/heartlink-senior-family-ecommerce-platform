package com.heartlink.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.heartlink.entity.MemberPointsConfig;
import com.heartlink.mapper.MemberPointsConfigMapper;
import com.heartlink.service.MemberPointsConfigService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MemberPointsConfigServiceImpl
        extends ServiceImpl<MemberPointsConfigMapper, MemberPointsConfig>
        implements MemberPointsConfigService {

    private static final long DEFAULT_CONFIG_ID = 1L;
    private static final int DEFAULT_DAILY_CHECK_IN_POINTS = 10;

    @Override
    @Transactional
    public MemberPointsConfig getConfig() {
        MemberPointsConfig config = getById(DEFAULT_CONFIG_ID);
        if (config == null) {
            config = new MemberPointsConfig();
            config.setId(DEFAULT_CONFIG_ID);
            config.setDailyCheckInPoints(DEFAULT_DAILY_CHECK_IN_POINTS);
            save(config);
            return getById(DEFAULT_CONFIG_ID);
        }
        if (config.getDailyCheckInPoints() == null) {
            config.setDailyCheckInPoints(DEFAULT_DAILY_CHECK_IN_POINTS);
            updateById(config);
        }
        return config;
    }

    @Override
    public int getDailyCheckInPoints() {
        MemberPointsConfig config = getConfig();
        return config.getDailyCheckInPoints() != null
                ? config.getDailyCheckInPoints()
                : DEFAULT_DAILY_CHECK_IN_POINTS;
    }

    @Override
    @Transactional
    public MemberPointsConfig saveDailyCheckInPoints(Integer dailyCheckInPoints) {
        int safePoints = dailyCheckInPoints == null
                ? DEFAULT_DAILY_CHECK_IN_POINTS
                : Math.max(dailyCheckInPoints, 0);
        MemberPointsConfig config = getConfig();
        config.setDailyCheckInPoints(safePoints);
        updateById(config);
        return getConfig();
    }
}
