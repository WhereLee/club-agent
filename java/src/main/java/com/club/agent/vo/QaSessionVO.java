package com.club.agent.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

/** 问答会话（管理层视图）。雪花 id 字符串序列化（K40：JS Number 精度丢失）。 */
@Data
public class QaSessionVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    private String title;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
