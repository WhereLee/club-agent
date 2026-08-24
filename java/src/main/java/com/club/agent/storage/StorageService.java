package com.club.agent.storage;

import org.springframework.web.multipart.MultipartFile;

/**
 * 存储服务：统一上传入口，返回可访问 URL。
 * 实现：LocalStorageServiceImpl（开发）/ CosStorageServiceImpl（生产，腾讯云 COS）。
 */
public interface StorageService {

    /**
     * 上传文件。
     *
     * @param file    文件（大小/类型由实现层校验）
     * @param bizType 业务目录（avatar/activity 等）
     * @return 可访问的 URL
     */
    String upload(MultipartFile file, String bizType);
}
