package com.club.agent.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

/**
 * 社团详情：社团信息 + 当前用户的身份状态。
 */
@Data
public class ClubDetailVO {

    /** 雪花 ID 超出 JS 安全整数范围，序列化为字符串防前端精度丢失 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    private String name;

    private String description;

    private String teacherName;

    private Long memberCount;

    /** 当前用户在该社团的状态：-1=无关系 0=申请中 1=已加入 2=已拒绝 */
    private Integer myStatus;

    /** 当前用户的角色编码（已加入时才有） */
    private String myRoleCode;

    /** 当前用户的角色名 */
    private String myRoleName;

    /** 当前用户的 membership id（离职/退出用） */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long myMembershipId;
}
