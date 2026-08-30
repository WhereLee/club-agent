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

    /** 任期届数（管理层在职时=当前届；离职后保留，配合 formerRoleCode 展示第X任） */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long termNo;

    /** 前任管理层职务（离职后保留：president/vice_president） */
    private String formerRoleCode;

    /** 0=申请中 1=已通过 2=已拒绝 */
    private Integer status;

    private LocalDateTime appliedAt;
}
