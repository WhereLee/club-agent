package com.club.agent.storage;

import com.club.agent.common.ResultCode;
import com.club.agent.config.UploadProperties;
import com.club.agent.exception.BizException;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collection;
import java.util.Locale;

/**
 * 存储服务抽象基类：公共的文件校验（大小/类型）。
 * 存储实现按 storage.mode 切换：local（本地磁盘）/ cos（腾讯云 COS）。
 */
public abstract class AbstractStorageService implements StorageService {

    protected final UploadProperties uploadProperties;

    protected AbstractStorageService(UploadProperties uploadProperties) {
        this.uploadProperties = uploadProperties;
    }

    /** 文件校验：非空 / 大小上限 / 扩展名白名单（默认配置） */
    protected void validate(MultipartFile file) {
        validate(file, uploadProperties.getAllowedTypes());
    }

    /** 文件校验（业务方指定白名单，双项目集成：文档类型 ≠ 头像图片类型） */
    protected void validate(MultipartFile file, Collection<String> allowedExts) {
        if (file == null || file.isEmpty()) {
            throw new BizException("文件不能为空");
        }
        if (file.getSize() > uploadProperties.getMaxSize()) {
            throw new BizException(ResultCode.BIZ_FILE_TOO_LARGE);
        }
        String ext = getExtension(file.getOriginalFilename());
        if (ext == null || !allowedExts.contains(ext)) {
            throw new BizException(ResultCode.BIZ_FILE_TYPE_ERROR);
        }
    }

    /** 提取小写扩展名（不含点）；非法文件名返回 null */
    protected String getExtension(String filename) {
        if (filename == null || filename.isBlank()) {
            return null;
        }
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return null;
        }
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
