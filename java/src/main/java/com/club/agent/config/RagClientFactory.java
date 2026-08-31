package com.club.agent.config;

import com.club.agent.dto.RagRetrieveResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;

/**
 * RAG 知识服务客户端（双项目集成）：调 rag 主服务 /api/org/*（入库/软删/纯检索）。
 *
 * 约定（与 rag 侧 org_api 对齐）：
 * - 鉴权头 X-Internal-Key（= rag 的 INTERNAL_API_KEY）；请求不携带 X-User-Id
 *   （org 语义，也避免触发 rag 的网关签名校验分支）
 * - 入库为异步：返回 file_id 后解析在 rag worker 中进行，可按需查 /files/{id}/status
 * - 软删尽力而为：rag 侧 404 视为已失效（不阻断 club 侧删除主流程）
 * - 检索为检索器模式：只返回 chunks，生成由概念 Agent 负责（方案 D1）
 */
@Slf4j
@Component
public class RagClientFactory {

    private final RestClient.Builder restClientBuilder;

    @Value("${rag.base-url:http://127.0.0.1:8090}")
    private String baseUrl;

    @Value("${rag.timeout-seconds:30}")
    private int timeoutSeconds;

    @Value("${rag.internal-key:}")
    private String internalKey;

    private volatile RestClient client;

    public RagClientFactory(RestClient.Builder restClientBuilder) {
        this.restClientBuilder = restClientBuilder;
    }

    /** DCL 懒初始化：统一超时 + 内部密钥头（模式同 PythonClientFactory） */
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
                    if (StringUtils.hasText(internalKey)) {
                        b.defaultHeader("X-Internal-Key", internalKey);
                    } else {
                        log.warn("rag.internal-key 未配置：调用 rag 不带内部密钥"
                                + "（生产必须通过 RAG_INTERNAL_KEY 注入，且与 rag 侧 INTERNAL_API_KEY 一致）");
                    }
                    c = b.build();
                    client = c;
                }
            }
        }
        return c;
    }

    /** 推送活动文件入 rag 知识库（异步解析）。返回 rag 侧 file_id。 */
    public long ingestFile(MultipartFile file, String filename, long orgId, String bizType) {
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new IllegalStateException("读取上传文件失败", e);
        }
        return ingestBytes(bytes, filename, orgId, bizType);
    }

    /** 推送程序生成的文件内容入 rag 知识库（如总结报告渲染的 Markdown）。返回 rag 侧 file_id。 */
    public long ingestBytes(byte[] bytes, String filename, long orgId, String bizType) {
        MultipartBodyBuilder mb = new MultipartBodyBuilder();
        mb.part("file", new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return filename;
            }
        });
        mb.part("org_id", orgId);
        mb.part("biz_type", bizType == null ? "" : bizType);
        @SuppressWarnings("unchecked")
        Map<String, Object> resp = get().post().uri("/api/org/ingest")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(mb.build())
                .retrieve().body(Map.class);
        return ((Number) resp.get("file_id")).longValue();
    }

    /** 软删 rag 侧文件（检索立即不可见）。尽力而为：404 视为已失效不抛错。 */
    public void deactivateFile(long fileId, long orgId) {
        try {
            get().post().uri("/api/org/files/{id}/deactivate", fileId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("org_id", orgId))
                    .retrieve().toBodilessEntity();
        } catch (HttpClientErrorException.NotFound e) {
            log.warn("rag 文件不存在或已失效（软删幂等跳过）: fileId={} orgId={}", fileId, orgId);
        }
    }

    /** 纯检索（检索器模式）：返回 org 空间命中 chunks（含来源文件名/页码/章节路径）。 */
    public RagRetrieveResult retrieve(String query, long orgId, int topK) {
        return get().post().uri("/api/org/retrieve")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("query", query, "org_id", orgId, "top_k", topK))
                .retrieve().body(RagRetrieveResult.class);
    }

    /** 解析状态查询（懒同步用）：返回 pending/parsing/success/partial/failed/queued。 */
    @SuppressWarnings("unchecked")
    public String queryParseStatus(long fileId, long orgId) {
        Map<String, Object> resp = get().get()
                .uri("/api/org/files/{id}/status?org_id={org}", fileId, orgId)
                .retrieve().body(Map.class);
        return resp == null ? null : (String) resp.get("status");
    }
}
