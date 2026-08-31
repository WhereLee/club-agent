package com.club.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.club.agent.config.RagClientFactory;
import com.club.agent.entity.Activity;
import com.club.agent.entity.ActivitySummary;
import com.club.agent.entity.ExperienceEntry;
import com.club.agent.mapper.ActivityMapper;
import com.club.agent.mapper.ActivitySummaryMapper;
import com.club.agent.mapper.ExperienceEntryMapper;
import com.club.agent.service.SummaryRagSyncService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 总结报告入 rag 实现（J1）。
 *
 * 触发点（两个，均为人确认后的定稿）：
 * 1. 归档成功（状态 8→9）：首次入库
 * 2. 归档后重生成成功：软删旧文件 + 重推新文件（rag_file_id 更新）
 *
 * 失败策略：尽力而为——rag 推送失败仅告警，不阻断归档/重生成主流程（报告仍在业务表）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SummaryRagSyncServiceImpl implements SummaryRagSyncService {

    private final ActivityMapper activityMapper;
    private final ActivitySummaryMapper summaryMapper;
    private final ExperienceEntryMapper experienceEntryMapper;
    private final RagClientFactory ragClientFactory;
    private final ObjectMapper objectMapper;

    /** 知识服务总开关：与活动资料库一致（关闭时不推送，仅告警） */
    @Value("${rag.enabled:true}")
    private boolean ragEnabled;

    @Async("aiExecutor")
    @Override
    public void syncToRag(Long clubId, Long activityId) {
        if (!ragEnabled) {
            log.warn("rag.enabled=false，跳过总结报告入库: activity={}", activityId);
            return;
        }
        try {
            doSync(clubId, activityId);
        } catch (Exception e) {
            log.warn("总结报告入 rag 失败（不阻断主流程）: activity={} err={}", activityId, e.getMessage());
        }
    }

    private void doSync(Long clubId, Long activityId) {
        ActivitySummary s = summaryMapper.selectOne(new LambdaQueryWrapper<ActivitySummary>()
                .eq(ActivitySummary::getActivityId, activityId));
        if (s == null || !ActivitySummary.STATUS_SUCCESS.equals(s.getStatus())
                || !StringUtils.hasText(s.getReport())) {
            return;  // 未定稿不入库
        }
        Activity a = activityMapper.selectById(activityId);
        if (a == null || !a.getClubId().equals(clubId)) {
            return;
        }
        byte[] md = renderMarkdown(a, s, lessons(activityId)).getBytes(StandardCharsets.UTF_8);
        // 幂等替换：先软删旧文件（尽力而为，404 幂等跳过），再推新文件
        if (s.getRagFileId() != null) {
            try {
                ragClientFactory.deactivateFile(s.getRagFileId(), clubId);
            } catch (Exception e) {
                log.warn("旧总结报告软删失败（继续重推）: activity={} err={}", activityId, e.getMessage());
            }
        }
        long fileId = ragClientFactory.ingestBytes(md, filename(a), clubId, "summary");
        s.setRagFileId(fileId);
        s.setUpdatedAt(LocalDateTime.now());
        summaryMapper.updateById(s);
        log.info("总结报告已入 rag: activity={} ragFileId={}", activityId, fileId);
    }

    /** 文件名：活动简述 + 固定后缀（净化非法字符，.md 属 rag 白名单） */
    static String filename(Activity a) {
        return ("活动总结-" + brief(a) + ".md").replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    /** 活动简述（与 ActivityServiceImpl.briefOf 同构：时间地点优先，否则内容前 20 字） */
    private static String brief(Activity a) {
        String t = a.getPlannedTime();
        String loc = a.getPlannedLocation();
        if (StringUtils.hasText(t) && StringUtils.hasText(loc)) {
            return t + " " + loc;
        }
        String c = a.getContent();
        if (!StringUtils.hasText(c)) {
            return "活动" + a.getId();
        }
        return c.length() > 20 ? c.substring(0, 20) : c;
    }

    /** 渲染检索友好的 Markdown：标题 + 基本信息 + 指标 + 正文 + 沉淀经验 */
    String renderMarkdown(Activity a, ActivitySummary s, List<ExperienceEntry> lessons) {
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(brief(a)).append(" 活动总结报告\n\n");
        if (StringUtils.hasText(a.getPlannedTime())) {
            sb.append("- 计划时间：").append(a.getPlannedTime()).append('\n');
        }
        if (StringUtils.hasText(a.getPlannedLocation())) {
            sb.append("- 计划地点：").append(a.getPlannedLocation()).append('\n');
        }
        if (s.getGeneratedAt() != null) {
            sb.append("- 总结生成时间：").append(s.getGeneratedAt()).append('\n');
        }
        try {
            Map<String, Object> report = objectMapper.readValue(s.getReport(), new TypeReference<>() {
            });
            Object metrics = report.get("metrics");
            if (metrics instanceof Map<?, ?> m && !m.isEmpty()) {
                sb.append("\n## 结构化指标\n\n");
                appendMetrics(sb, m, "");
            }
            Object text = report.get("report_text");
            if (text != null && StringUtils.hasText(String.valueOf(text))) {
                sb.append("\n## 总结正文\n\n").append(text).append('\n');
            }
        } catch (Exception e) {
            log.warn("总结报告 JSON 解析失败（仅推原始文本）: activity={}", a.getId());
            sb.append("\n## 总结正文\n\n").append(s.getReport()).append('\n');
        }
        if (!lessons.isEmpty()) {
            sb.append("\n## 沉淀经验\n");
            for (ExperienceEntry le : lessons) {
                sb.append("\n### ").append(nvl(le.getTitle())).append('\n')
                        .append(nvl(le.getContent())).append('\n');
            }
        }
        return sb.toString();
    }

    /** 指标递归平铺（嵌套维度展开为 "维度.子项: 值"，检索分块后语义完整） */
    private void appendMetrics(StringBuilder sb, Map<?, ?> m, String prefix) {
        for (Map.Entry<?, ?> e : m.entrySet()) {
            String key = prefix + e.getKey();
            Object v = e.getValue();
            if (v instanceof Map<?, ?> sub) {
                appendMetrics(sb, sub, key + ".");
            } else {
                sb.append("- ").append(key).append("：").append(v).append('\n');
            }
        }
    }

    private List<ExperienceEntry> lessons(Long activityId) {
        return experienceEntryMapper.selectList(new LambdaQueryWrapper<ExperienceEntry>()
                .eq(ExperienceEntry::getActivityId, activityId)
                .eq(ExperienceEntry::getStatus, ExperienceEntry.STATUS_VALID));
    }

    private static String nvl(String s) {
        return s == null ? "" : s;
    }
}
