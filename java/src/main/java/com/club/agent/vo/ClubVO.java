package com.club.agent.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 社团列表项。
 */
@Data
public class ClubVO {

    private Long id;

    private String name;

    private String description;

    private String teacherName;

    /** 在职成员数（不含申请中） */
    private Long memberCount;

    private LocalDateTime createdAt;
}
