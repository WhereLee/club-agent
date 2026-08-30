package com.club.agent.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/** 执行留痕列表项（管理层视图）：提交人 + 答案 + 打分（含 AI 预评） */
@Data
public class RecordMemberVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;

    private String nickname;

    private List<RecordVO.AnswerVO> answers;

    private LocalDateTime updatedAt;

    /** 是否已提交留痕 */
    private Boolean submitted;

    /** AI 预评分（未预评/未打分时为 null） */
    private Integer aiScore;

    /** AI 预评理由 */
    private String aiReason;

    /** 管理员最终分（未打分为 null） */
    private Integer score;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long scoreBy;

    private LocalDateTime scoreAt;
}
