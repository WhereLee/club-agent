package com.club.agent.vo;

import lombok.Data;

import java.util.List;

/**
 * 社团上下文（AI 起草工具 get_club_context 的数据源）。
 */
@Data
public class ClubContextVO {

    private Long clubId;

    private String clubName;

    private String description;

    /** 当前管理层名单 */
    private List<ManagerVO> managers;

    /** 往届已通过概念（最近 5 条，冷启动经验替代） */
    private List<PastConceptVO> pastConcepts;

    @Data
    public static class ManagerVO {
        private Long userId;
        private String nickname;
        private String roleCode;
    }

    @Data
    public static class PastConceptVO {
        private Long id;
        private String plannedTime;
        private String plannedLocation;
        private String content;
    }
}
