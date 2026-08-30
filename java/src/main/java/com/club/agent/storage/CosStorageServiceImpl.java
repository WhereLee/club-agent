package com.club.agent.storage;

import com.club.agent.common.ResultCode;
import com.club.agent.config.CosProperties;
import com.club.agent.config.UploadProperties;
import com.club.agent.exception.BizException;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.exception.CosClientException;
import com.qcloud.cos.model.ObjectMetadata;
import com.qcloud.cos.region.Region;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 腾讯云 COS 存储（生产模式）：cos/{bizType}/{yyyyMMdd}/{uuid}.{ext}，
 * 返回 URL = 自定义域名（配置了 domain）/ COS 默认访问域名。
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "storage.mode", havingValue = "cos")
public class CosStorageServiceImpl extends AbstractStorageService {

    private final CosProperties cosProperties;
    private COSClient cosClient;

    public CosStorageServiceImpl(CosProperties cosProperties, UploadProperties uploadProperties) {
        super(uploadProperties);
        this.cosProperties = cosProperties;
    }

    /** 懒初始化：密钥缺失直接拒绝启动（fail-fast） */
    @PostConstruct
    public void init() {
        if (blank(cosProperties.getSecretId()) || blank(cosProperties.getSecretKey())) {
            throw new IllegalStateException("storage.mode=cos 但 COS_SECRET_ID/COS_SECRET_KEY 未配置");
        }
        cosClient = new COSClient(
                new BasicCOSCredentials(cosProperties.getSecretId(), cosProperties.getSecretKey()),
                new ClientConfig(new Region(cosProperties.getRegion())));
        log.info("COS 客户端初始化完成: bucket={}, region={}", cosProperties.getBucket(), cosProperties.getRegion());
    }

    @Override
    public String upload(MultipartFile file, String bizType) {
        validate(file);
        String ext = getExtension(file.getOriginalFilename());
        String date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String key = bizType + "/" + date + "/"
                + UUID.randomUUID().toString().replace("-", "") + "." + ext;

        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(file.getSize());
        metadata.setContentType(file.getContentType() == null ? "application/octet-stream" : file.getContentType());
        try {
            cosClient.putObject(cosProperties.getBucket(), key, file.getInputStream(), metadata);
        } catch (CosClientException | IOException e) {
            log.error("COS 上传失败: key={}, err={}", key, e.getMessage());
            throw new BizException(ResultCode.BIZ_UPLOAD_FAIL);
        }
        return buildUrl(key);
    }

    @Override
    public void delete(String url) {
        String key = extractKey(url);
        if (key == null) {
            return;
        }
        try {
            cosClient.deleteObject(cosProperties.getBucket(), key);
        } catch (CosClientException e) {
            log.warn("COS 删除失败: key={}, err={}", key, e.getMessage());
        }
    }

    /** 从 URL 提取对象 key（自定义域名前缀 / COS 默认域名两种形态） */
    private String extractKey(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        String domain = cosProperties.getDomain();
        if (!blank(domain) && url.startsWith(domain + "/")) {
            return url.substring(domain.length() + 1);
        }
        String bucketHost = cosProperties.getBucket() + ".cos." + cosProperties.getRegion() + ".myqcloud.com/";
        int idx = url.indexOf(bucketHost);
        if (idx >= 0) {
            return url.substring(idx + bucketHost.length());
        }
        return null;
    }

    private String buildUrl(String key) {
        if (!blank(cosProperties.getDomain())) {
            return cosProperties.getDomain() + "/" + key;
        }
        return "https://" + cosProperties.getBucket()
                + ".cos." + cosProperties.getRegion() + ".myqcloud.com/" + key;
    }

    private boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
