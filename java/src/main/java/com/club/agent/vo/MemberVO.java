package com.club.agent.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 社团成员（含待审批申请）。
 */
@Data
public class MemberVO {

    /** 雪花 ID 超出 JS 安全整数范围，序列化为字符串防前端精度丢失 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long membershipId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;

    private String username;

    private String nickname;

    private String roleCode;

    private String roleName;

    /** 0=申请中 1=已通过 2=已拒绝 */
    private Integer status;

    private LocalDateTime appliedAt;
}
