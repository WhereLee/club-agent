package com.club.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理层经验问答会话（双项目集成阶段2 · J3）。
 * 会话私有（创建人本人可用）；软删保留历史（审计）。
 */
@Data
@TableName("qa_session")
public class QaSession {

    public static final int STATUS_VALID = 1;
    public static final int STATUS_DELETED = 0;

    public static final String DEFAULT_TITLE = "新会话";

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long clubId;

    private Long userId;

    private String title;

    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
