package com.heartlink.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.heartlink.entity.HealthProfile;

import java.util.List;

/**
 * 健康档案服务接口
 */
public interface HealthProfileService extends IService<HealthProfile> {

    /**
     * 获取主档案；不存在时自动创建默认主档案。
     */
    HealthProfile getOrCreateByParentId(Long parentId);

    /**
     * 列出当前长辈账号下的全部健康档案。
     */
    List<HealthProfile> listByParentId(Long parentId);

    /**
     * 按档案ID和长辈ID查询档案。
     */
    HealthProfile getByIdAndParentId(Long id, Long parentId);

    /**
     * 保存或更新档案。
     */
    HealthProfile saveOrUpdateProfile(HealthProfile profile);

    /**
     * 设置主档案。
     */
    HealthProfile setPrimaryProfile(Long parentId, Long profileId);
}
