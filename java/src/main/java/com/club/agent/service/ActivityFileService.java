package com.club.agent.service;

import com.club.agent.dto.ActivityFileDTO;
import com.club.agent.vo.ActivityFileVO;

/**
 * 正式文件域（块 D）：章节草稿保存 + 发布（文件 + 分工 + 状态 3→4 + 通知）。
 * 文件主体复用表单引擎（form_template type=file：章节=字段，内容=答案）；
 * 分工为列表型结构，专用表 activity_duty（不走引擎字段模型）。
 * 草稿仅发起人/管理层可见；发布后全员可见（状态 4）。
 */
public interface ActivityFileService {

    /** 保存草稿（发起人本人 + 讨论中；章节全量覆盖） */
    void saveDraft(Long clubId, Long activityId, Long userId, ActivityFileDTO dto);

    /** 发布（发起人本人 + 讨论中；章节 ≥1 + 分工 ≥1 → 落库 + 状态 3→4 + 全员通知 + 指派通知） */
    void publish(Long clubId, Long activityId, Long userId, ActivityFileDTO dto);

    /** 查看（发布后全员；草稿仅发起人/管理层；未撰写 1043） */
    ActivityFileVO detail(Long clubId, Long activityId, Long userId);
}