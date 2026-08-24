package com.club.agent.storage;

import com.club.agent.common.ResultCode;
import com.club.agent.config.UploadProperties;
import com.club.agent.exception.BizException;
import org.springframework.web.multipart.MultipartFile;

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

    /** 文件校验：非空 / 大小上限 / 扩展名白名单 */
    protected void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException("文件不能为空");
        }
        if (file.getSize() > uploadProperties.getMaxSize()) {
            throw new BizException(ResultCode.BIZ_FILE_TOO_LARGE);
        }
        String ext = getExtension(file.getOriginalFilename());
        if (ext == null || !uploadProperties.getAllowedTypes().contains(ext)) {
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
