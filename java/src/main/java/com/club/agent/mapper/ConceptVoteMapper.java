package com.club.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.club.agent.entity.ConceptVote;
import com.club.agent.vo.ConceptVoteVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 概念投票 Mapper（复杂查询走 XML：resources/mapper/ConceptVoteMapper.xml）。
 */
@Mapper
public interface ConceptVoteMapper extends BaseMapper<ConceptVote> {

    /** 概念投票记录（join 投票人昵称，按轮次/时间正序） */
    List<ConceptVoteVO> selectVotesByConcept(@Param("conceptId") Long conceptId);
}
