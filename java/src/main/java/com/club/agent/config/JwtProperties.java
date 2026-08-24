package com.club.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * JWT 配置（密钥来自环境变量，ConfigValidator 启动时校验）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    /** 签名密钥（HS256 要求 >= 32 字节） */
    private String secret;

    /** 过期时间（分钟） */
    private long expireMinutes;
}
