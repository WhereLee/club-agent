package com.club.agent.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 社团成员（含待审批申请）。
 */
@Data
public class MemberVO {

    private Long membershipId;

    private Long userId;

    private String username;

    private String nickname;

    private String roleCode;

    private String roleName;

    /** 0=申请中 1=已通过 2=已拒绝 */
    private Integer status;

    private LocalDateTime appliedAt;
}
