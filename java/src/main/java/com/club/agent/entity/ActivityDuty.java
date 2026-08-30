package com.club.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 活动分工项（块 D）：职责描述 + 指派成员（JSONB，实体 String，insert 走 XML CAST）。
 * 分工 ≠ 参加名单：指定"负责某事的人"，含管理层缺席时的联络节点。
 */
@Data
@TableName("activity_duty")
public class ActivityDuty {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long activityId;

    /** 职责描述（如"负责路线与安全"） */
    private String description;

    /** 指派成员 id 数组（JSON 字符串，如 [1,2]） */
    private String assignedMembers;

    private Integer sortOrder;

    private LocalDateTime createdAt;
}