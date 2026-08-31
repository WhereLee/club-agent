package com.club.agent.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 启动安全校验（fail-fast）：必需环境变量缺失时拒绝启动，并列出缺失项。
 * <p>
 * 安全配置（数据库密码 / JWT 密钥等）一律不留默认值——"配置不全 = 不可启动"，
 * 防止部署时忘了注入环境变量导致静默使用可猜测值。
 */
@Component
public class ConfigValidator implements ApplicationRunner {

    private static final String[] REQUIRED = {
            "SPRING_DATASOURCE_URL",
            "SPRING_DATASOURCE_USERNAME",
            "SPRING_DATASOURCE_PASSWORD",
            "JWT_SECRET",
    };

    /** prod 强约束：内部服务密钥缺失时静默降级为无密钥调用（鉴权旁路），必须 fail-fast */
    private static final String[] PROD_REQUIRED = {
            "AI_DRAFT_INTERNAL_SECRET",
            "AGENT_QA_INTERNAL_SECRET",
            "RAG_INTERNAL_KEY",
    };

    @Value("${spring.profiles.active:dev}")
    private String activeProfile;

    @Override
    public void run(ApplicationArguments args) {
        List<String> missing = new ArrayList<>();
        for (String key : REQUIRED) {
            String val = System.getenv(key);
            if (val == null || val.isBlank()) {
                missing.add(key);
            }
        }
        if ("prod".equalsIgnoreCase(activeProfile)) {
            for (String key : PROD_REQUIRED) {
                String val = System.getenv(key);
                if (val == null || val.isBlank()) {
                    missing.add(key);
                }
            }
            String storageMode = System.getenv("STORAGE_MODE");
            if (storageMode == null || !"cos".equalsIgnoreCase(storageMode)) {
                missing.add("STORAGE_MODE=cos");
            }
        }
        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                    "安全配置缺失，拒绝启动: " + String.join(" ", missing)
                            + " ——请通过环境变量注入（参考 scripts/start-dev.ps1）");
        }
    }
}
