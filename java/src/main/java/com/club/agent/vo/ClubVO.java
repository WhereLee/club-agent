package com.club.agent.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 社团列表项。
 */
@Data
public class ClubVO {

    /** 雪花 ID 超出 JS 安全整数范围，序列化为字符串防前端精度丢失 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    private String name;

    private String description;

    private String teacherName;

    /** 在职成员数（不含申请中） */
    private Long memberCount;

    private LocalDateTime createdAt;
}
