package com.club.agent.vo;

import lombok.Data;

import java.util.List;

/**
 * 经验检索结果（D2 冷启动返回空 items；D3 接 experience_entry 表；B1 加数据水位）。
 */
@Data
public class ExperienceSearchVO {

    private List<Item> items;

    /** B1：数据水位——本社团（含通用）非思考角度的经验总数，供 Agent 判断经验丰富度 */
    private Integer similarActivityCount;

    @Data
    public static class Item {
        private String category;
        private String title;
        private String content;
    }
}
