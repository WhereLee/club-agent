package com.club.agent.storage;

import org.springframework.web.multipart.MultipartFile;

import java.util.Set;

/**
 * 存储服务：统一上传入口，返回可访问 URL。
 * 实现：LocalStorageServiceImpl（开发）/ CosStorageServiceImpl（生产，腾讯云 COS）。
 */
public interface StorageService {

    /**
     * 上传文件（默认白名单：配置 upload.allowed-types，头像场景）。
     *
     * @param file    文件（大小/类型由实现层校验）
     * @param bizType 业务目录（avatar/activity 等）
     * @return 可访问的 URL
     */
    String upload(MultipartFile file, String bizType);

    /**
     * 上传文件（业务方指定扩展名白名单，双项目集成：活动资料库文档类型 ≠ 头像图片类型）。
     *
     * @param file         文件（大小由配置校验，扩展名用 allowedExts）
     * @param bizType      业务目录（filelib 等）
     * @param allowedExts  允许的扩展名（小写不带点）
     * @return 可访问的 URL
     */
    String upload(MultipartFile file, String bizType, Set<String> allowedExts);

    /**
     * 删除文件（尽力而为：失败仅告警，不影响主流程；对象不存在视为成功）。
     *
     * @param url upload 返回的 URL（本地 /uploads/... 相对路径 / COS 完整域名 URL）
     */
    void delete(String url);
}
