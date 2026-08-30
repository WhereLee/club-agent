package com.club.agent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 社团管理 Agent 启动类。
 * @EnableScheduling：概念审批 36h 超时自动作废扫描任务。
 */
@EnableScheduling
@SpringBootApplication
public class ClubAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(ClubAgentApplication.class, args);
    }
}
