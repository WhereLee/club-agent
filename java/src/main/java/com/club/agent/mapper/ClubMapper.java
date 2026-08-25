package com.club.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.club.agent.entity.Club;
import com.club.agent.vo.ClubVO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 社团 Mapper（复杂查询走 XML：resources/mapper/ClubMapper.xml）。
 */
@Mapper
public interface ClubMapper extends BaseMapper<Club> {

    /** 社团分页列表（带老师名/成员数） */
    IPage<ClubVO> selectClubPage(IPage<ClubVO> page);
}
