package com.club.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 验证码配置。
 */
@Data
@Component
@ConfigurationProperties(prefix = "captcha")
public class CaptchaProperties {

    /** 开关：false 时登录跳过验证码校验（开发/自动化测试用；生产保持 true） */
    private boolean enabled = true;

    /** 有效期（分钟） */
    private int expireMinutes;
}
