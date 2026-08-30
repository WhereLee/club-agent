package com.club.agent.vo;

import lombok.Data;

import java.util.List;

/** 正式文件 VO：章节 + 分工（草稿仅发起人/管理层可见；发布后全员可见） */
@Data
public class ActivityFileVO {

    /** 章节（发起人按活动定制：时间地点流程预算……） */
    private List<SectionVO> sections;

    /** 分工项（职责描述 + 指派成员） */
    private List<DutyVO> duties;

    @Data
    public static class SectionVO {
        private String title;
        private String content;
    }

    @Data
    public static class DutyVO {
        private String description;

        /** 指派成员 id（JSON 数组字符串，如 [1,2]） */
        private String memberIds;

        /** 指派成员昵称（展示用，逗号分隔） */
        private String memberNames;
    }
}