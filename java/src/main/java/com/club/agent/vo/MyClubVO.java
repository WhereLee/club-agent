package com.club.agent.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

/**
 * 我的社团（当前用户视角）。
 */
@Data
public class MyClubVO {

    /** 雪花 ID 超出 JS 安全整数范围，序列化为字符串防前端精度丢失 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long clubId;

    private String clubName;

    private String clubDescription;

    private String teacherName;

    private String roleCode;

    private String roleName;

    /** 0=申请中 1=已通过 2=已拒绝 */
    private Integer status;
}
