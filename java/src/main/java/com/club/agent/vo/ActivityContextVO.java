package com.club.agent.vo;

import lombok.Data;

import java.util.List;

/**
 * 活动前置上下文 VO（get_activity_context 工具数据源）：概念批复结果 + 讨论群消息 + 问卷统计。
 */
@Data
public class ActivityContextVO {

    private ConceptVO concept;

    /** 讨论群消息（最近 N 条正序；截断策略在 Service） */
    private List<DiscussionVO> discussions;

    private SurveyStatVO survey;

    /** 讨论质量快照统计（endDiscussion 生成）：总条数/高质量条数/高频发言者 */
    private DiscussionStatVO discussionStats;

    @Data
    public static class ConceptVO {
        private String plannedTime;
        private String plannedLocation;
        private String content;
        private String aiBrief;
    }

    @Data
    public static class DiscussionVO {
        private String senderName;
        private String content;
    }

    @Data
    public static class SurveyStatVO {
        private Long totalSubmissions;
        private Long interested;
        private Long notInterested;

        /** 自定义题答案汇总（label + 答案要点拼接） */
        private List<CustomStatVO> customStats;
    }

    @Data
    public static class DiscussionStatVO {
        private Long totalMessages;
        private Long qualityMessages;
        private List<HighFreqMemberVO> highFreqMembers;
    }

    @Data
    public static class HighFreqMemberVO {
        private Long userId;
        private String nickname;
        private Integer msgCount;
        private Integer qualityCount;
    }

    @Data
    public static class CustomStatVO {
        private String label;
        private String summary;
    }
}