package com.club.agent.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.club.agent.dto.ClubCreateDTO;
import com.club.agent.vo.ClubDetailVO;
import com.club.agent.vo.ClubVO;

/**
 * 社团服务：创建 / 列表 / 详情 / 编辑。
 */
public interface ClubService {

    /** 创建社团（仅指导老师，名称唯一） */
    ClubVO create(ClubCreateDTO dto, Long operatorId);

    /** 修改社团信息（老师/社长；名称唯一排除自身） */
    ClubVO update(Long clubId, ClubCreateDTO dto);

    /** 社团分页列表（登录可见） */
    IPage<ClubVO> list(long page, long size);

    /** 社团详情 + 当前用户身份状态 */
    ClubDetailVO detail(Long clubId, Long userId);
}
