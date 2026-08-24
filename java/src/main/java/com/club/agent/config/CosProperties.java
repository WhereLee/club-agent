package com.club.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 腾讯云 COS 配置（storage.mode=cos 时生效，密钥来自环境变量）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "cos")
public class CosProperties {

    private String secretId;
    private String secretKey;
    private String region;
    private String bucket;
    /** 自定义域名（空则拼接 COS 默认访问域名） */
    private String domain;
}
