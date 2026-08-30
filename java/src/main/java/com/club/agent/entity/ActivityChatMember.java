package com.club.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 讨论群成员快照（块 C）：问卷截止后统一生成（感兴趣成员 ∪ 管理层，老师不在内）。
 * 订阅 / 发送 / 拉历史三方鉴权共用；快照保证名单在讨论期间稳定。
 */
@Data
@TableName("activity_chat_member")
public class ActivityChatMember {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long activityId;

    private Long userId;

    private LocalDateTime createdAt;
}