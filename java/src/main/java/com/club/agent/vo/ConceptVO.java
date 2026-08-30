package com.club.agent.vo;

import com.club.agent.entity.ConceptTrace;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 概念视图（列表/详情共用）。
 * 列表接口不填充 votes/traces（保持轻量），详情接口填充。
 */
@Data
public class ConceptVO {

    /** 雪花 ID 超出 JS 安全整数范围，序列化为字符串防前端精度丢失 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long clubId;

    /** 发起者 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;

    /** 发起者昵称 */
    private String requesterNickname;

    /** 当前用户是否已投当前轮（列表接口返回，用于前端隐藏"投票"按钮） */
    private Boolean myVoted;

    /** 1=起草中 2=已提交待审 3=复议中 4=待老师批复 5=已通过 6=已作废 */
    private Integer status;

    private String reason;

    private String plannedTime;

    private String plannedLocation;

    private String content;

    /** D3 起：提交后异步生成的"发起人思路"简析（详情页展示；生成中/失败为 null） */
    private String aiBrief;

    private LocalDateTime submittedAt;

    /** 当前阶段截止时间 */
    private LocalDateTime deadline;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    /** 投票记录（详情接口填充：谁/哪轮/赞成与否/理由/时间） */
    private List<ConceptVoteVO> votes;

    /** 全量时间线（详情接口填充，审计/老师视图数据源） */
    private List<ConceptTrace> traces;
}
