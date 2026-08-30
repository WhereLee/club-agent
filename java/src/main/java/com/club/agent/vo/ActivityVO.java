package com.club.agent.vo;

import com.club.agent.entity.ActivityTrace;
import lombok.Data;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;


import java.time.LocalDateTime;
import java.util.List;

/** 活动视图：列表/详情（详情含时间线 traces） */
@Data
public class ActivityVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long clubId;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long conceptId;

    /** 发起人（雪花 id 字符串化由前端处理） */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;

    /** 发起人昵称 */
    private String requesterNickname;

    /** 1=公示中 2=问卷中 3=讨论中 4=已发布 5=已取消 */
    private Integer status;

    private String plannedTime;
    private String plannedLocation;
    private String content;
    private String cancelReason;

    /** 报名截止时间 */
    private java.time.LocalDateTime signupDeadline;

    /** 执行留痕提交截止时间 */
    private java.time.LocalDateTime recordDeadline;

    /** 讨论关闭时间 */
    private java.time.LocalDateTime discussionClosedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** 详情返回：全量时间线（时间升序） */
    private List<ActivityTrace> traces;
}