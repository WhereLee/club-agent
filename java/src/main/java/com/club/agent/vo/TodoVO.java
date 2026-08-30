package com.club.agent.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 待办审批项：当前用户（老师/管理层）可见的待审批成员申请。
 */
@Data
public class TodoVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long clubId;

    private String clubName;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long membershipId;

    private String username;

    private String nickname;

    private LocalDateTime appliedAt;
}
