package com.club.agent.config;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RagClientFactory 单测（任务4）：DCL 单例 + 内部密钥头注入语义。
 * 请求链路（multipart/JSON）由任务5/6 的端到端冒烟覆盖，此处只守工厂契约。
 */
class RagClientFactoryTest {

    private RestClient.Builder stubBuilder() {
        RestClient.Builder builder = mock(RestClient.Builder.class);
        when(builder.baseUrl(anyString())).thenReturn(builder);
        when(builder.requestFactory(any())).thenReturn(builder);
        when(builder.defaultHeader(anyString(), anyString())).thenReturn(builder);
        when(builder.build()).thenReturn(mock(RestClient.class));
        return builder;
    }

    @Test
    void get_is_singleton_and_injects_internal_key_header() {
        RestClient.Builder builder = stubBuilder();
        RagClientFactory factory = new RagClientFactory(builder);
        ReflectionTestUtils.setField(factory, "baseUrl", "http://127.0.0.1:8090");
        ReflectionTestUtils.setField(factory, "timeoutSeconds", 30);
        ReflectionTestUtils.setField(factory, "internalKey", "test-key");

        RestClient c1 = factory.get();
        RestClient c2 = factory.get();

        assertThat(c1).isSameAs(c2);              // DCL：多次获取同一实例
        verify(builder, times(1)).build();        // 只构建一次
        verify(builder).defaultHeader(eq("X-Internal-Key"), eq("test-key"));
    }

    @Test
    void get_without_key_builds_client_without_header() {
        RestClient.Builder builder = stubBuilder();
        RagClientFactory factory = new RagClientFactory(builder);
        ReflectionTestUtils.setField(factory, "baseUrl", "http://127.0.0.1:8090");
        ReflectionTestUtils.setField(factory, "timeoutSeconds", 30);
        ReflectionTestUtils.setField(factory, "internalKey", "");

        assertThat(factory.get()).isNotNull();
        verify(builder, never()).defaultHeader(anyString(), anyString());
    }
}
