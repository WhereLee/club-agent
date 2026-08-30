package com.club.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.club.agent.entity.ExperienceEntry;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ExperienceEntryMapper extends BaseMapper<ExperienceEntry> {

    /**
     * Agent 检索：本社团（含全社团通用）有效经验 +
     * 该发起人专属的 thinking_pattern；q 可空（空 = 按时间倒序取最近）。
     */
    List<ExperienceEntry> selectForAgent(@Param("clubId") Long clubId,
                                         @Param("userId") Long userId,
                                         @Param("q") String q,
                                         @Param("limit") int limit);

    /** B1：数据水位——本社团（含通用）非思考角度的经验总数 */
    int countForAgent(@Param("clubId") Long clubId);
}
