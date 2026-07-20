package com.heartlink.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.heartlink.entity.FamilyBind;

import java.util.List;

/**
 * 家庭绑定服务接口
 */
public interface FamilyBindService extends IService<FamilyBind> {
    
    /**
     * 生成亲情码(子女端调用)
     */
    String generateBindCode(Long childId, String relation);
    
    /**
     * 确认绑定(长辈端调用)
     */
    FamilyBind confirmBind(Long parentId, String bindCode);
    
    /**
     * 获取子女绑定的所有长辈
     */
    List<FamilyBind> getParentsByChildId(Long childId);
    
    /**
     * 获取长辈绑定的所有子女
     */
    List<FamilyBind> getChildrenByParentId(Long parentId);
}
