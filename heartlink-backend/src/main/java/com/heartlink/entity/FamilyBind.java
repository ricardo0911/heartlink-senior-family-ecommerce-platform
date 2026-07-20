package com.heartlink.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 家庭绑定关系
 * 子女与长辈通过亲情码绑定
 */
@Data
@TableName("hl_family_bind")
public class FamilyBind {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /** 子女用户ID */
    private Long childId;
    
    /** 长辈用户ID */
    private Long parentId;
    
    /** 亲情绑定码(6位) */
    private String bindCode;
    
    /** 关系称呼: 妈妈/爸爸/奶奶/姥姥等 */
    private String relation;
    
    /** 状态: PENDING-待确认, ACTIVE-已绑定 */
    private String status;
    
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
