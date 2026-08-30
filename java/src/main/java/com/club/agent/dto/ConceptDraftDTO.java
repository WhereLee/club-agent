package com.club.agent.dto;

import lombok.Data;

/**
 * 概念草稿：发起理由 + 四项内容（提交时校验必填）。
 */
@Data
public class ConceptDraftDTO {

    /** 发起理由（必填；LangGraph AI 起草会话的输入入口） */
    private String reason;

    /** 预计时间 */
    private String plannedTime;

    /** 预计地点 */
    private String plannedLocation;

    /** 活动简述 */
    private String content;
}
