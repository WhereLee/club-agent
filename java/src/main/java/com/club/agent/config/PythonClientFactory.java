package com.club.agent.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Python 服务客户端统一工厂（S5/Q2 收口）：
 * - 两处 Service 各自懒初始化 RestClient 的写法统一为 DCL（消除重复）；
 * - 统一携带内部共享密钥头 X-Internal-Secret（Java 发出、Python 校验，防部署失误绑到 0.0.0.0 时失守）；
 * - secret 未配置时 warn 并仍可调用（prod 必须注入，缺失时部署需收紧）。
 */
@Slf4j
@Component
public class PythonClientFactory {

    private final RestClient.Builder restClientBuilder;

    @Value("${ai.draft.base-url:http://127.0.0.1:8094}")
    private String aiBaseUrl;

    @Value("${ai.draft.timeout-seconds:120}")
    private int aiTimeoutSeconds;

    @Value("${ai.draft.internal-secret:}")
    private String internalSecret;

    private volatile RestClient client;

    public PythonClientFactory(RestClient.Builder restClientBuilder) {
        this.restClientBuilder = restClientBuilder;
    }

    /** DCL 懒初始化：统一超时（读超时放宽给 LLM）+ 内部密钥头 */
    public RestClient get() {
        RestClient c = client;
        if (c == null) {
            synchronized (this) {
                c = client;
                if (c == null) {
                    SimpleClientHttpRequestFactory rf = new SimpleClientHttpRequestFactory();
                    rf.setConnectTimeout(Duration.ofSeconds(5));
                    rf.setReadTimeout(Duration.ofSeconds(aiTimeoutSeconds));
                    RestClient.Builder b = restClientBuilder.baseUrl(aiBaseUrl).requestFactory(rf);
                    if (StringUtils.hasText(internalSecret)) {
                        b.defaultHeader("X-Internal-Secret", internalSecret);
                    } else {
                        log.warn("ai.draft.internal-secret 未配置：调用 Python 不带内部密钥"
                                + "（生产必须通过 AI_DRAFT_INTERNAL_SECRET 注入，否则信任边界单薄）");
                    }
                    c = b.build();
                    client = c;
                }
            }
        }
        return c;
    }
}
