package com.heartlink.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.heartlink.entity.HealthProfile;
import com.heartlink.mapper.HealthProfileMapper;
import com.heartlink.service.HealthProfileService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * 健康档案服务实现
 */
@Service
public class HealthProfileServiceImpl extends ServiceImpl<HealthProfileMapper, HealthProfile> implements HealthProfileService {

    private static final int MAX_PROFILE_COUNT = 2;
    private static final String DEFAULT_PRIMARY_PROFILE_NAME = "本人";
    private static final String DEFAULT_PRIMARY_RELATION = "本人";
    private static final String DEFAULT_SECONDARY_PROFILE_NAME = "家人";
    private static final String DEFAULT_SECONDARY_RELATION = "家人";

    @Override
    public HealthProfile getOrCreateByParentId(Long parentId) {
        List<HealthProfile> profiles = listProfilesInternal(parentId);
        HealthProfile primaryProfile = profiles.stream()
                .filter(item -> Integer.valueOf(1).equals(item.getIsPrimary()))
                .findFirst()
                .orElse(null);

        if (primaryProfile == null) {
            if (!profiles.isEmpty()) {
                primaryProfile = profiles.get(0);
                clearPrimaryFlag(parentId, primaryProfile.getId());
                primaryProfile.setIsPrimary(1);
                primaryProfile.setSortOrder(0);
                primaryProfile.setUpdatedAt(LocalDateTime.now());
                updateById(primaryProfile);
                primaryProfile = getById(primaryProfile.getId());
            } else {
                primaryProfile = createDefaultPrimaryProfile(parentId);
            }
        }

        return primaryProfile;
    }

    @Override
    public List<HealthProfile> listByParentId(Long parentId) {
        List<HealthProfile> profiles = listProfilesInternal(parentId);
        if (profiles.isEmpty()) {
            return Collections.singletonList(createDefaultPrimaryProfile(parentId));
        }
        return profiles;
    }

    @Override
    public HealthProfile getByIdAndParentId(Long id, Long parentId) {
        if (id == null || parentId == null) {
            return null;
        }
        return getOne(new LambdaQueryWrapper<HealthProfile>()
                .eq(HealthProfile::getId, id)
                .eq(HealthProfile::getParentId, parentId)
                .last("LIMIT 1"));
    }

    @Override
    public HealthProfile saveOrUpdateProfile(HealthProfile profile) {
        if (profile.getParentId() == null) {
            throw new RuntimeException("parentId不能为空");
        }

        profile.setUpdatedAt(LocalDateTime.now());

        List<HealthProfile> existingProfiles = listProfilesInternal(profile.getParentId());
        HealthProfile existing = profile.getId() == null ? null : getByIdAndParentId(profile.getId(), profile.getParentId());
        if (profile.getId() != null && existing == null) {
            throw new RuntimeException("健康档案不存在");
        }
        if (existing == null && existingProfiles.size() >= MAX_PROFILE_COUNT) {
            throw new RuntimeException("健康档案最多只能添加2个人");
        }

        boolean makePrimary;
        if (existing == null) {
            makePrimary = existingProfiles.isEmpty() || Integer.valueOf(1).equals(profile.getIsPrimary());
        } else {
            makePrimary = Integer.valueOf(1).equals(profile.getIsPrimary()) || Integer.valueOf(1).equals(existing.getIsPrimary());
        }

        profile.setIsPrimary(makePrimary ? 1 : 0);
        profile.setProfileName(normalizeProfileName(profile.getProfileName(), makePrimary));
        profile.setRelation(normalizeRelation(profile.getRelation(), makePrimary));
        if (profile.getSortOrder() == null) {
            profile.setSortOrder(resolveSortOrder(existingProfiles, existing, makePrimary));
        }

        if (makePrimary) {
            clearPrimaryFlag(profile.getParentId(), existing == null ? null : existing.getId());
        }

        if (existing != null) {
            profile.setId(existing.getId());
            updateById(profile);
        } else {
            save(profile);
        }

        return getById(profile.getId());
    }

    @Override
    public HealthProfile setPrimaryProfile(Long parentId, Long profileId) {
        HealthProfile target = getByIdAndParentId(profileId, parentId);
        if (target == null) {
            throw new RuntimeException("健康档案不存在");
        }

        clearPrimaryFlag(parentId, target.getId());
        target.setIsPrimary(1);
        target.setSortOrder(0);
        target.setUpdatedAt(LocalDateTime.now());
        updateById(target);
        return getById(target.getId());
    }

    private List<HealthProfile> listProfilesInternal(Long parentId) {
        return list(new LambdaQueryWrapper<HealthProfile>()
                .eq(HealthProfile::getParentId, parentId)
                .orderByDesc(HealthProfile::getIsPrimary)
                .orderByAsc(HealthProfile::getSortOrder)
                .orderByAsc(HealthProfile::getId));
    }

    private HealthProfile createDefaultPrimaryProfile(Long parentId) {
        HealthProfile profile = new HealthProfile();
        profile.setParentId(parentId);
        profile.setProfileName(DEFAULT_PRIMARY_PROFILE_NAME);
        profile.setRelation(DEFAULT_PRIMARY_RELATION);
        profile.setIsPrimary(1);
        profile.setSortOrder(0);
        profile.setUpdatedAt(LocalDateTime.now());
        save(profile);
        return profile;
    }

    private void clearPrimaryFlag(Long parentId, Long keepId) {
        LambdaUpdateWrapper<HealthProfile> wrapper = new LambdaUpdateWrapper<HealthProfile>()
                .eq(HealthProfile::getParentId, parentId)
                .set(HealthProfile::getIsPrimary, 0)
                .set(HealthProfile::getUpdatedAt, LocalDateTime.now());
        if (keepId != null) {
            wrapper.ne(HealthProfile::getId, keepId);
        }
        update(wrapper);
    }

    private Integer resolveSortOrder(List<HealthProfile> profiles, HealthProfile existing, boolean primary) {
        if (primary) {
            return 0;
        }
        if (existing != null && existing.getSortOrder() != null) {
            return existing.getSortOrder();
        }
        int maxSortOrder = profiles.stream()
                .map(HealthProfile::getSortOrder)
                .filter(item -> item != null)
                .max(Integer::compareTo)
                .orElse(0);
        return Math.max(1, maxSortOrder + 1);
    }

    private String normalizeProfileName(String rawName, boolean primary) {
        String value = rawName == null ? "" : rawName.trim();
        if (!value.isEmpty()) {
            return value;
        }
        return primary ? DEFAULT_PRIMARY_PROFILE_NAME : DEFAULT_SECONDARY_PROFILE_NAME;
    }

    private String normalizeRelation(String rawRelation, boolean primary) {
        String value = rawRelation == null ? "" : rawRelation.trim();
        if (!value.isEmpty()) {
            return value;
        }
        return primary ? DEFAULT_PRIMARY_RELATION : DEFAULT_SECONDARY_RELATION;
    }
}
