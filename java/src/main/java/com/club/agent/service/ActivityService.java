package com.club.agent.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.club.agent.entity.ConceptSession;
import com.club.agent.vo.ActivityVO;

import java.time.LocalDateTime;

/**
 * 活动域（块 A：状态机主表 + 概念转活动 + 取消；块 B：问卷/讨论状态推进）。
 * 状态：1=公示中 2=问卷中 3=讨论中 4=已发布 5=已取消；概念通过自动创建（公示中）。
 * 状态机收口：所有状态推进（CAS）与留痕只在本类，业务域（SurveyService 等）调推进方法。
 */
public interface ActivityService {

    /** 概念通过批复 → 创建活动（公示中）+ 留痕 + 全员公示通知（teacherReview 同事务调用） */
    void createFromConcept(ConceptSession concept);

    /** 活动列表（本社团全员可见：club:member 注解层校验；分页 + 状态筛选） */
    IPage<ActivityVO> list(Long clubId, long page, long size, Integer status);

    /** 活动详情（含时间线 traces） */
    ActivityVO detail(Long clubId, Long activityId);

    /** 取消（发起人本人；必填理由 → 已取消 + 留痕 + 全员通知附理由；任意非终态可取消） */
    void cancel(Long clubId, Long activityId, Long userId, String reason);

    /** 块 B：发布问卷（公示中→问卷中，CAS + trace survey_publish；发起人校验调用方做） */
    void startSurvey(Long clubId, Long activityId, Long userId, LocalDateTime deadline);

    /** 块 B：结束问卷开启讨论（问卷中→讨论中，CAS + trace discuss_start） */
    void startDiscuss(Long clubId, Long activityId, Long userId);

    /** 块 D：正式文件发布（讨论中→已发布，CAS + trace file_publish；活动确定，讨论群只读） */
    void publish(Long clubId, Long activityId, Long userId);

    /** 结束讨论（讨论中且未关闭）：群转只读 + 讨论质量快照（频率统计），解锁正式文件撰写 */
    void endDiscussion(Long clubId, Long activityId, Long userId);

    /** 开始报名（已发布 → 报名中，需先设置报名截止时间） */
    void startSignup(Long clubId, Long activityId, Long userId, java.time.LocalDateTime deadline);

    /** 开始执行（报名中 → 执行中，可选设置留痕截止时间） */
    void startExecution(Long clubId, Long activityId, Long userId, LocalDateTime recordDeadline);

    /** 结束执行（执行中 → 留痕中） */
    void completeExecution(Long clubId, Long activityId, Long userId);

    /** 关闭留痕（留痕中 → 总结中，进入后自动生成活动总结）；超时扫描与手动共用 */
    void closeRecords(Long clubId, Long activityId, Long userId, boolean system);

    /** 归档（总结中 → 已归档）：前置校验总结已生成；归档后只读但允许重生成总结 */
    void archive(Long clubId, Long activityId, Long userId);
}