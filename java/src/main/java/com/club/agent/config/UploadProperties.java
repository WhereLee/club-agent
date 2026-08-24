package com.club.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 文件上传配置。
 */
@Data
@Component
@ConfigurationProperties(prefix = "upload")
public class UploadProperties {

    /** local 模式的本地存储目录 */
    private String localDir;

    /** 单文件大小上限（字节） */
    private long maxSize;

    /** 允许的图片扩展名 */
    private java.util.List<String> allowedTypes;
}
