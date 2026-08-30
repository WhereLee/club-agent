package com.club.agent.dto;

import lombok.Data;

import java.util.List;

/** 正式文件保存/发布载荷：章节 + 分工（分工仅发布时必填） */
@Data
public class ActivityFileDTO {

    /** 章节列表（至少 1 章） */
    private List<Section> sections;

    /** 分工项列表（发布时至少 1 项） */
    private List<Duty> duties;

    @Data
    public static class Section {
        private String title;
        private String content;
    }

    @Data
    public static class Duty {
        private String description;

        /** 指派成员 id 列表 */
        private List<Long> memberIds;
    }
}