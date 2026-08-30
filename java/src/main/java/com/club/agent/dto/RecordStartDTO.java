package com.club.agent.dto;

import lombok.Data;

import java.time.LocalDateTime;

/** 开始执行（可选）：执行留痕截止时间（不传=不设截止，由发起人手动关闭） */
@Data
public class RecordStartDTO {

    private LocalDateTime deadline;
}
