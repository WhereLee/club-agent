package com.club.agent.vo;

import lombok.Data;

/**
 * 我的社团（当前用户视角）。
 */
@Data
public class MyClubVO {

    private Long clubId;

    private String clubName;

    private String clubDescription;

    private String teacherName;

    private String roleCode;

    private String roleName;

    /** 0=申请中 1=已通过 2=已拒绝 */
    private Integer status;
}
