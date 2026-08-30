package com.club.agent.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步线程池：日志落库等旁路操作走异步，不阻塞主请求。
 * - logExecutor：毫秒级日志任务，CallerRunsPolicy 队列满时调用线程执行——日志宁可慢一拍也不丢；
 * - aiExecutor：AI 长任务（LLM 调用 40-120s）独立小池，AbortPolicy 拒绝 + 失败落库待调度重试（C3 隔离，
 *   避免 LLM 调用占满日志线程池后 CallerRunsPolicy 把请求线程拖住 120s）。
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean("logExecutor")
    public Executor logExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("log-async-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }

    @Bean("aiExecutor")
    public Executor aiExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("ai-async-");
        // LLM 调用并发无需大（防打满 Hikari）；拒绝即抛 TaskRejectedException，由调用方落失败行待调度重试
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.initialize();
        return executor;
    }
}
