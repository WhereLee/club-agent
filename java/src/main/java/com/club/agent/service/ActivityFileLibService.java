package com.club.agent.service;

import com.club.agent.vo.ActivityFileLibVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 活动资料库（双项目集成任务5）：管理层上传活动资料 → StorageService 落盘 + 落表
 * → 推 rag 知识库（org 空间，异步解析）；软删时同步失效 rag 侧（检索立即不可见）。
 */
public interface ActivityFileLibService {

    /**
     * 上传活动资料（管理层）。
     * 流程：文档白名单/大小校验 → StorageService 落盘 → 落表 → 推 rag 入库（失败标记 failed 不阻断）。
     *
     * @param clubId     社团（Controller 层 @ClubPermission 已校验成员身份）
     * @param activityId 关联活动（可空 = 社团级通用资料；非空时校验归属）
     * @param userId     上传人（当前登录用户）
     * @return 落库后的资料记录
     */
    ActivityFileLibVO upload(Long clubId, Long activityId, Long userId, MultipartFile file);

    /** 资料列表（本社团全员可读，按时间倒序）。 */
    List<ActivityFileLibVO> list(Long clubId);

    /**
     * 软删资料（管理层）：本地置删 + rag 侧失效（尽力而为）。
     */
    void delete(Long clubId, Long userId, Long libId);
}
