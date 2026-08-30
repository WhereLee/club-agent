package com.club.agent.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 概念投票视图（详情页展示：谁/哪轮/赞成与否/理由/时间）。
 */
@Data
public class ConceptVoteVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long conceptId;

    /** 1=首次投票 2=复议 */
    private Integer round;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long voterId;

    /** 投票人昵称 */
    private String voterNickname;

    /** 1=赞成 0=拒绝 */
    private Integer result;

    /** 必填理由（留痕主体） */
    private String comment;

    private LocalDateTime createdAt;
}
