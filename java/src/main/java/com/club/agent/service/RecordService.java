package com.club.agent.service;

import com.club.agent.dto.RecordSubmitDTO;
import com.club.agent.vo.RecordMemberVO;
import com.club.agent.vo.RecordVO;

import java.util.List;

/**
 * 执行留痕服务（块 G）：复用动态表单引擎（type=record）。
 * 提交（留痕中 + 截止前，一人一份可覆盖）；我的视图；管理层列表。
 */
public interface RecordService {

    /** 提交/修改留痕（截止后 1047；必填缺失 1050） */
    void submit(Long clubId, Long activityId, Long userId, RecordSubmitDTO dto);

    /** 成员视图：模板字段 + 本人已提交内容（回显） */
    RecordVO mine(Long clubId, Long activityId, Long userId);

    /** 管理层列表：已提交成员 + 答案 */
    List<RecordMemberVO> list(Long clubId, Long activityId);
}
