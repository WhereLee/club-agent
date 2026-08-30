package com.club.agent.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Java 端单次线性 AI（块 H）：Spring AI 直连 Xiaomi MiMo（OpenAI 兼容协议）。
 * 与 Python 侧 LangGraph 流转型 Agent 形成双端 AI 范式：
 * Python = 带流转/有状态（对话式 Agent）；Java = 单次线性/无状态（建议提炼、留痕预评）。
 * key/base-url 走 .env 注入（LLM_* 环境变量）。
 */
@Configuration
public class AiConfig {

    @Value("${ai.llm.base-url}")
    private String baseUrl;

    @Value("${ai.llm.api-key}")
    private String apiKey;

    @Value("${ai.llm.model}")
    private String model;

    @Bean
    public OpenAiApi openAiApi() {
        // MiMo OpenAI 兼容端点含 /v1；Spring AI 默认 completionsPath 也是 /v1/chat/completions
        // → baseUrl 去掉 /v1 后缀，避免双 /v1 导致 404（openresty 实测）
        String url = baseUrl;
        if (url != null && url.endsWith("/v1")) {
            url = url.substring(0, url.length() - 3);
        }
        return OpenAiApi.builder().baseUrl(url).apiKey(apiKey).build();
    }

    @Bean
    public OpenAiChatModel openAiChatModel(OpenAiApi api) {
        return OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(OpenAiChatOptions.builder().model(model).temperature(0.2).build())
                .build();
    }

    @Bean
    public ChatClient chatClient(OpenAiChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
    }
}
