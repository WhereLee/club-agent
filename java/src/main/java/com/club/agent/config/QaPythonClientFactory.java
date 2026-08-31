package com.club.agent.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * 管理层问答 Python 服务（agent_qa）客户端工厂：模式同 PythonClientFactory。
 * 独立配置块（qa.*）：问答服务与起草服务并列部署，端口/超时/密钥各自管理。
 */
@Slf4j
@Component
public class QaPythonClientFactory {

    private final RestClient.Builder restClientBuilder;

    @Value("${qa.base-url:http://127.0.0.1:8095}")
    private String baseUrl;

    @Value("${qa.timeout-seconds:120}")
    private int timeoutSeconds;

    @Value("${qa.internal-secret:}")
    private String internalSecret;

    private volatile RestClient client;

    public QaPythonClientFactory(RestClient.Builder restClientBuilder) {
        this.restClientBuilder = restClientBuilder;
    }

    /** DCL 懒初始化：读超时放宽给 LLM（一轮 ReAct 可能多次检索）+ 内部密钥头 */
    public RestClient get() {
        RestClient c = client;
        if (c == null) {
            synchronized (this) {
                c = client;
                if (c == null) {
                    SimpleClientHttpRequestFactory rf = new SimpleClientHttpRequestFactory();
                    rf.setConnectTimeout(Duration.ofSeconds(5));
                    rf.setReadTimeout(Duration.ofSeconds(timeoutSeconds));
                    RestClient.Builder b = restClientBuilder.baseUrl(baseUrl).requestFactory(rf);
                    if (StringUtils.hasText(internalSecret)) {
                        b.defaultHeader("X-Internal-Secret", internalSecret);
                    } else {
                        log.warn("qa.internal-secret 未配置：调用问答服务不带内部密钥"
                                + "（生产必须通过 AGENT_QA_INTERNAL_SECRET 注入）");
                    }
                    c = b.build();
                    client = c;
                }
            }
        }
        return c;
    }
}
