package com.club.agent.controller;

import com.club.agent.annotation.ClubPermission;
import com.club.agent.annotation.Log;
import com.club.agent.annotation.RateLimiter;
import com.club.agent.annotation.RepeatSubmit;
import com.club.agent.common.R;
import com.club.agent.service.ActivityFileLibService;
import com.club.agent.util.SecurityUtils;
import com.club.agent.vo.ActivityFileLibVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 活动资料库（双项目集成任务5）：管理层上传活动资料入 rag 知识库，全员可读列表，管理层删除。
 * 上传/删除 = activity:manage（管理层 + 老师）；列表 = club:member（全员只读）。
 */
@Tag(name = "活动资料库（双项目集成）")
@RestController
@RequestMapping("/clubs")
@RequiredArgsConstructor
public class ActivityFileLibController {

    private final ActivityFileLibService fileLibService;

    @PostMapping("/{clubId}/file-lib/upload")
    @ClubPermission(clubId = "#clubId", permission = "activity:manage")
    @Log(module = "活动资料", operation = "上传资料")
    @RateLimiter(limit = 10, windowSeconds = 60)
    @RepeatSubmit(intervalSeconds = 3)
    @Operation(summary = "上传活动资料（管理层；activityId 可选=社团级通用资料；入 rag 知识库异步解析）")
    public R<ActivityFileLibVO> upload(@PathVariable Long clubId,
                                       @RequestParam(required = false) Long activityId,
                                       @RequestParam("file") MultipartFile file) {
        return R.ok(fileLibService.upload(clubId, activityId, SecurityUtils.getUserId(), file));
    }

    @GetMapping("/{clubId}/file-lib")
    @ClubPermission(clubId = "#clubId", permission = "club:member")
    @Operation(summary = "资料列表（本社团全员可见，时间倒序；量级小不分页）")
    public R<List<ActivityFileLibVO>> list(@PathVariable Long clubId) {
        return R.ok(fileLibService.list(clubId));
    }

    @DeleteMapping("/{clubId}/file-lib/{libId}")
    @ClubPermission(clubId = "#clubId", permission = "activity:manage")
    @Log(module = "活动资料", operation = "删除资料")
    @Operation(summary = "删除资料（管理层；软删 + rag 侧失效）")
    public R<Void> delete(@PathVariable Long clubId, @PathVariable Long libId) {
        fileLibService.delete(clubId, SecurityUtils.getUserId(), libId);
        return R.ok();
    }
}
