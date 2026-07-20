package com.heartlink.service.impl;

import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.heartlink.entity.FamilyBind;
import com.heartlink.mapper.FamilyBindMapper;
import com.heartlink.service.FamilyBindService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FamilyBindServiceImpl extends ServiceImpl<FamilyBindMapper, FamilyBind> implements FamilyBindService {

    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final long EXPIRE_MINUTES = 30L;
    private static final long PENDING_PARENT_ID = 0L;
    private static final String DEFAULT_RELATION = "家人";
    private static final String RELATION_MOTHER = "妈妈";
    private static final String RELATION_FATHER = "爸爸";

    @Override
    public String generateBindCode(Long childId, String relation) {
        cleanupExpiredPendingCodes();

        String normalizedRelation = normalizeRelation(relation);
        ensureExclusiveRelationAvailable(childId, normalizedRelation, null);

        String bindCode = generateUniquePendingCode();
        FamilyBind existedPending = getOne(new LambdaQueryWrapper<FamilyBind>()
                .eq(FamilyBind::getChildId, childId)
                .eq(FamilyBind::getParentId, PENDING_PARENT_ID)
                .eq(FamilyBind::getStatus, STATUS_PENDING)
                .orderByDesc(FamilyBind::getCreatedAt)
                .orderByDesc(FamilyBind::getId)
                .last("LIMIT 1"), false);

        if (existedPending != null) {
            existedPending.setBindCode(bindCode);
            existedPending.setRelation(normalizedRelation);
            existedPending.setCreatedAt(LocalDateTime.now());
            updateById(existedPending);
            return bindCode;
        }

        FamilyBind pending = new FamilyBind();
        pending.setChildId(childId);
        pending.setParentId(PENDING_PARENT_ID);
        pending.setBindCode(bindCode);
        pending.setRelation(normalizedRelation);
        pending.setStatus(STATUS_PENDING);
        save(pending);
        return bindCode;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FamilyBind confirmBind(Long parentId, String bindCode) {
        String normalizedBindCode = normalizeBindCode(bindCode);
        if (normalizedBindCode.length() != 6) {
            throw new RuntimeException("请输入6位亲情码");
        }

        cleanupExpiredPendingCodes();

        FamilyBind pending = getOne(new LambdaQueryWrapper<FamilyBind>()
                .eq(FamilyBind::getStatus, STATUS_PENDING)
                .eq(FamilyBind::getBindCode, normalizedBindCode)
                .orderByDesc(FamilyBind::getCreatedAt)
                .orderByDesc(FamilyBind::getId)
                .last("LIMIT 1"), false);
        if (pending == null) {
            throw new RuntimeException("亲情码无效或已过期");
        }

        Long childId = pending.getChildId();
        String relation = normalizeRelation(pending.getRelation());

        FamilyBind existing = getOne(new LambdaQueryWrapper<FamilyBind>()
                .eq(FamilyBind::getChildId, childId)
                .eq(FamilyBind::getParentId, parentId)
                .eq(FamilyBind::getStatus, STATUS_ACTIVE)
                .last("LIMIT 1"), false);
        if (existing != null) {
            throw new RuntimeException("已经绑定过了");
        }

        ensureExclusiveRelationAvailable(childId, relation, parentId);

        if (!removeById(pending.getId())) {
            throw new RuntimeException("亲情码无效或已过期");
        }

        FamilyBind bind = new FamilyBind();
        bind.setChildId(childId);
        bind.setParentId(parentId);
        bind.setBindCode(normalizedBindCode);
        bind.setRelation(relation);
        bind.setStatus(STATUS_ACTIVE);
        save(bind);
        return bind;
    }

    private void ensureExclusiveRelationAvailable(Long childId, String relation, Long currentParentId) {
        if (!isExclusiveRelation(relation)) {
            return;
        }

        LambdaQueryWrapper<FamilyBind> wrapper = new LambdaQueryWrapper<FamilyBind>()
                .eq(FamilyBind::getChildId, childId)
                .eq(FamilyBind::getRelation, relation)
                .eq(FamilyBind::getStatus, STATUS_ACTIVE);
        if (currentParentId != null) {
            wrapper.ne(FamilyBind::getParentId, currentParentId);
        }

        FamilyBind existing = getOne(wrapper.last("LIMIT 1"), false);
        if (existing != null) {
            throw new RuntimeException("已绑定" + relation + "，不能重复绑定");
        }
    }

    private boolean isExclusiveRelation(String relation) {
        return RELATION_MOTHER.equals(relation) || RELATION_FATHER.equals(relation);
    }

    private String normalizeRelation(String relation) {
        return (relation == null || relation.isBlank()) ? DEFAULT_RELATION : relation.trim();
    }

    private String generateUniquePendingCode() {
        for (int i = 0; i < 20; i++) {
            String code = RandomUtil.randomNumbers(6);
            long existed = count(new LambdaQueryWrapper<FamilyBind>()
                    .eq(FamilyBind::getStatus, STATUS_PENDING)
                    .eq(FamilyBind::getBindCode, code));
            if (existed == 0) {
                return code;
            }
        }
        throw new RuntimeException("亲情码生成失败，请稍后重试");
    }

    private void cleanupExpiredPendingCodes() {
        LocalDateTime expiredBefore = LocalDateTime.now().minusMinutes(EXPIRE_MINUTES);
        remove(new LambdaQueryWrapper<FamilyBind>()
                .eq(FamilyBind::getStatus, STATUS_PENDING)
                .and(w -> w.lt(FamilyBind::getCreatedAt, expiredBefore).or().isNull(FamilyBind::getCreatedAt)));
    }

    private String normalizeBindCode(String bindCode) {
        if (bindCode == null) {
            return "";
        }
        StringBuilder digits = new StringBuilder();
        for (char ch : bindCode.toCharArray()) {
            if (ch >= '0' && ch <= '9') {
                digits.append(ch);
            } else if (ch >= '０' && ch <= '９') {
                digits.append((char) (ch - '０' + '0'));
            }
        }
        return digits.toString();
    }

    @Override
    public List<FamilyBind> getParentsByChildId(Long childId) {
        return list(new LambdaQueryWrapper<FamilyBind>()
                .eq(FamilyBind::getChildId, childId)
                .eq(FamilyBind::getStatus, STATUS_ACTIVE));
    }

    @Override
    public List<FamilyBind> getChildrenByParentId(Long parentId) {
        return list(new LambdaQueryWrapper<FamilyBind>()
                .eq(FamilyBind::getParentId, parentId)
                .eq(FamilyBind::getStatus, STATUS_ACTIVE));
    }
}
