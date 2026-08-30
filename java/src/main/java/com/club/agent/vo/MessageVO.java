package com.club.agent.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 站内消息视图（id 字符串化防 JS 精度丢失，标记已读需要精确 id）。
 */
@Data
public class MessageVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long recipientId;

    /** concept_void / concept_approved / activity_announce / activity_cancel */
    private String type;

    private String title;

    private String content;

    /** 关联概念（雪花 id） */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long refConceptId;

    /** 关联活动（雪花 id；与 refConceptId 二选一） */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long refActivityId;

    /** 0=未读 1=已读 */
    private Integer readFlag;

    private LocalDateTime createdAt;
}
