package com.heartlink.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 智能货架
 * 子女推送给长辈的商品
 */
@Data
@TableName("hl_smart_shelf")
public class SmartShelf {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /** 子女用户ID */
    private Long childId;
    
    /** 长辈用户ID */
    private Long parentId;
    
    /** 商品ID */
    private Long productId;
    
    /** 子女的语音推荐语 */
    private String voiceMessage;
    
    /** 长辈反馈: PENDING-待查看, LIKE-喜欢, DISLIKE-不喜欢 */
    private String reaction;
    
    /** 备注 */
    private String remark;
    
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    
    /** 长辈查看时间 */
    private LocalDateTime viewedAt;
    
    /** 长辈反馈时间 */
    private LocalDateTime reactedAt;
}
