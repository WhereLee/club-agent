package com.club.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.club.agent.common.ResultCode;
import com.club.agent.config.RagClientFactory;
import com.club.agent.entity.ActivityFileLib;
import com.club.agent.entity.SysUser;
import com.club.agent.exception.BizException;
import com.club.agent.mapper.ActivityFileLibMapper;
import com.club.agent.mapper.SysUserMapper;
import com.club.agent.service.ActivityFileLibService;
import com.club.agent.service.ActivityOwnership;
import com.club.agent.storage.StorageService;
import com.club.agent.util.RedisKeys;
import com.club.agent.vo.ActivityFileLibVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 活动资料库实现（双项目集成任务5）。
 *
 * 校验链：文档扩展名白名单 + 50MB 上限（与 rag 解析器支持格式/上限对齐）
 * → StorageService 落盘 → 落表 → 推 rag（失败仅标记，不阻断——资料已落库，可重试入库）。
 * 删除：软删（status=0）+ rag 侧失效（尽力而为，D8 语义：检索过滤 status=1 立即不可见）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ActivityFileLibServiceImpl implements ActivityFileLibService {

    /** 与 rag 解析管线支持格式对齐（org_api.ALLOWED_EXTS 同集） */
    private static final Set<String> LIB_EXTS = Set.of(
            "txt", "md", "pdf", "docx", "xlsx", "pptx", "png", "jpg", "jpeg", "webp");
    /** 与 rag 网关文件域上限一致 */
    private static final long MAX_FILE_SIZE = 50L * 1024 * 1024;

    private final ActivityFileLibMapper fileLibMapper;
    private final SysUserMapper sysUserMapper;
    private final StorageService storageService;
    private final RagClientFactory ragClientFactory;
    private final ActivityOwnership ownership;
    private final StringRedisTemplate redisTemplate;

    /** 懒同步节流窗口：同一记录在窗口内最多查一次 rag（列表可被全员高频触发，30s 超时/条会拖垮列表） */
    private static final Duration SYNC_THROTTLE = Duration.ofSeconds(30);

    /** 知识服务总开关：false 时上传拒绝（不伪造成功），检索侧退化为纯 SQL 经验源 */
    @Value("${rag.enabled:true}")
    private boolean ragEnabled;

    @Override
    public ActivityFileLibVO upload(Long clubId, Long activityId, Long userId, MultipartFile file) {
        if (!ragEnabled) {
            throw new BizException(ResultCode.BIZ_FILE_LIB_RAG_DISABLED);
        }
        if (file == null || file.isEmpty()) {
            throw new BizException("文件不能为空");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BizException(ResultCode.BIZ_FILE_TOO_LARGE);
        }
        String filename = sanitize(file.getOriginalFilename());
        String ext = extOf(filename);
        if (ext == null || !LIB_EXTS.contains(ext)) {
            throw new BizException(ResultCode.BIZ_FILE_TYPE_ERROR);
        }
        if (activityId != null) {
            ownership.getOwned(clubId, activityId);   // 活动归属校验（跨社团/不存在 → 1036）
        }

        // 1) 落盘 + 落表（成功后再推 rag，保证本地记录是事实源）
        String storageUrl = storageService.upload(file, "filelib", LIB_EXTS);
        ActivityFileLib lib = new ActivityFileLib();
        lib.setId(IdWorker.getId());
        lib.setClubId(clubId);
        lib.setActivityId(activityId);
        lib.setUploaderId(userId);
        lib.setFilename(filename);
        lib.setFileSize(file.getSize());
        lib.setStorageUrl(storageUrl);
        lib.setRagStatus(ActivityFileLib.RAG_PENDING);
        lib.setStatus(ActivityFileLib.STATUS_VALID);
        lib.setCreatedAt(LocalDateTime.now());
        fileLibMapper.insert(lib);

        // 2) 推 rag 入库（异步解析；失败标记 failed——资料已在本地，不阻断主流程）
        try {
            long ragFileId = ragClientFactory.ingestFile(
                    file, filename, clubId, activityId == null ? "club_lib" : "activity_lib");
            lib.setRagFileId(ragFileId);
            lib.setRagStatus(ActivityFileLib.RAG_PARSING);
            fileLibMapper.updateById(lib);
        } catch (Exception e) {
            log.warn("资料推 rag 入库失败（本地已落库，状态 failed）: club={} file={} err={}",
                    clubId, filename, e.getMessage());
            lib.setRagStatus(ActivityFileLib.RAG_FAILED);
            fileLibMapper.updateById(lib);
        }
        return toVO(lib, nickname(userId));
    }

    @Override
    public List<ActivityFileLibVO> list(Long clubId) {
        List<ActivityFileLib> rows = fileLibMapper.selectList(new LambdaQueryWrapper<ActivityFileLib>()
                .eq(ActivityFileLib::getClubId, clubId)
                .eq(ActivityFileLib::getStatus, ActivityFileLib.STATUS_VALID)
                .orderByDesc(ActivityFileLib::getCreatedAt));
        rows.forEach(r -> syncRagStatus(r, clubId));
        Map<Long, String> names = rows.stream()
                .map(ActivityFileLib::getUploaderId).distinct()
                .collect(Collectors.toMap(Function.identity(), this::nickname));
        return rows.stream().map(r -> toVO(r, names.get(r.getUploaderId()))).toList();
    }

    /** 懒同步：parsing 中的记录查 rag 解析结果回填（避免状态永久停留；尽力而为）。
     *  节流：Redis setIfAbsent + 30s TTL，窗口内重复访问跳过（N+1 外部调用降频）；
     *  失败不置 key，下个窗口重试。 */
    private void syncRagStatus(ActivityFileLib r, Long clubId) {
        if (!ActivityFileLib.RAG_PARSING.equals(r.getRagStatus()) || r.getRagFileId() == null) {
            return;
        }
        String throttleKey = RedisKeys.FILE_LIB_SYNC + r.getId();
        if (!Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(throttleKey, "1", SYNC_THROTTLE))) {
            return;  // 窗口内已查过（或正在查），跳过本轮
        }
        try {
            String st = ragClientFactory.queryParseStatus(r.getRagFileId(), clubId);
            if (ActivityFileLib.RAG_SUCCESS.equals(st) || ActivityFileLib.RAG_PARTIAL.equals(st)
                    || ActivityFileLib.RAG_FAILED.equals(st)) {
                r.setRagStatus(st);
                fileLibMapper.updateById(r);
            }
        } catch (Exception e) {
            log.warn("rag 解析状态同步失败（保持 parsing）: lib={} err={}", r.getId(), e.getMessage());
        }
    }

    @Override
    public void delete(Long clubId, Long userId, Long libId) {
        ActivityFileLib lib = fileLibMapper.selectById(libId);
        if (lib == null || !lib.getClubId().equals(clubId)
                || lib.getStatus() == null || lib.getStatus() != ActivityFileLib.STATUS_VALID) {
            throw new BizException(ResultCode.BIZ_FILE_LIB_NOT_FOUND);
        }
        // 软删 + 存储清理（本地）+ rag 侧失效（尽力而为）
        lib.setStatus(ActivityFileLib.STATUS_DELETED);
        lib.setRagStatus(ActivityFileLib.RAG_VOIDED);
        fileLibMapper.updateById(lib);
        storageService.delete(lib.getStorageUrl());
        if (lib.getRagFileId() != null) {
            try {
                ragClientFactory.deactivateFile(lib.getRagFileId(), clubId);
            } catch (Exception e) {
                log.warn("rag 侧失效失败（本地已删，检索窗口内短暂可见）: lib={} err={}",
                        libId, e.getMessage());
            }
        }
    }

    private ActivityFileLibVO toVO(ActivityFileLib r, String uploaderName) {
        ActivityFileLibVO vo = new ActivityFileLibVO();
        vo.setId(r.getId());
        vo.setActivityId(r.getActivityId());
        vo.setFilename(r.getFilename());
        vo.setFileSize(r.getFileSize());
        vo.setStorageUrl(r.getStorageUrl());
        vo.setRagStatus(r.getRagStatus());
        vo.setUploaderName(uploaderName);
        vo.setCreatedAt(r.getCreatedAt());
        return vo;
    }

    private String nickname(Long userId) {
        SysUser u = sysUserMapper.selectById(userId);
        return u == null ? "" : u.getNickname();
    }

    /** 文件名净化：去路径、限长（防超长/路径注入，与文件域惯例一致） */
    private String sanitize(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BizException("文件名不能为空");
        }
        String name = raw.replace("\\", "/");
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        name = name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        if (name.isBlank()) {
            throw new BizException("文件名不能为空");
        }
        return name.length() > 200 ? name.substring(0, 200) : name;
    }

    private String extOf(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return null;
        }
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
