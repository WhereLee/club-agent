package com.club.agent.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * knife4j / springdoc 文档信息。
 */
@Configuration
public class Knife4jConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI().info(new Info()
                .title("社团管理 Agent API")
                .description("活动全流程管理：职责留痕 + 经验复用 + 跨届传承")
                .version("0.1.0"));
    }
}
