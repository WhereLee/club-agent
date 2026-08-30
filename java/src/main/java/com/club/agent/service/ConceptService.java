package com.club.agent.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.club.agent.dto.ConceptDraftDTO;
import com.club.agent.dto.ConceptReviewDTO;
import com.club.agent.dto.ConceptVoteDTO;
import com.club.agent.vo.ConceptVO;

/**
 * 概念：发起 → 起草 → 提交 → 投票/复议 → 老师批复 → 通过/作废。
 * 规则：发起人不投票；另外两位管理层各一票（必填理由）；出现拒绝票进入一轮复议；
 * 复议再拒立即作废；两票赞成进入老师批复；每个阶段 36h 超时自动作废（定时扫描）。
 */
public interface ConceptService {

    /** 发起概念（校验唯一性；创建起草中会话） */
    ConceptVO create(Long clubId, Long userId, ConceptDraftDTO dto);

    /** 社团概念列表（管理层可见，分页 + 状态筛选；userId 用于返回"我是否已投"） */
    IPage<ConceptVO> list(Long clubId, Long userId, long page, long size, Integer status);

    /** 概念详情（含发起人昵称） */
    ConceptVO detail(Long clubId, Long id);

    /** 保存草稿（发起者本人，起草中；@Version 乐观锁防并发覆盖） */
    ConceptVO saveDraft(Long clubId, Long id, Long userId, ConceptDraftDTO dto);

    /** 提交（四项必填，状态 1→2，deadline=提交+36h） */
    ConceptVO submit(Long clubId, Long id, Long userId);

    /** 撤回（发起人，审批链任意节点 2/3/4 → 起草中，投票失效） */
    ConceptVO withdraw(Long clubId, Long id, Long userId);

    /** 放弃（发起人，任意非终局状态 → 作废） */
    ConceptVO abandon(Long clubId, Long id, Long userId);

    /** 管理层投票（发起人不投；两票齐后按结果推进状态机：全赞成→待老师批复；首轮拒绝→复议；复议再拒→作废） */
    ConceptVO vote(Long clubId, Long id, Long userId, ConceptVoteDTO dto);

    /** 老师批复（该社团指导老师；通过→已通过并通知管理层；否决→作废并通知管理层） */
    ConceptVO teacherReview(Long clubId, Long id, Long userId, ConceptReviewDTO dto);

    /** 发起人离职：作废其在该社团的全部活跃概念（resign_void 留痕 + 通知现任管理层） */
    void voidActiveOnResign(Long clubId, Long userId);
}
