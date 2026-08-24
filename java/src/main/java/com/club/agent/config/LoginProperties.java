package com.club.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 登录安全配置：连续失败锁定（防暴力破解）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "login")
public class LoginProperties {

    /** 连续失败 N 次锁定 */
    private int failMaxCount;

    /** 锁定分钟数 */
    private int lockMinutes;
}
