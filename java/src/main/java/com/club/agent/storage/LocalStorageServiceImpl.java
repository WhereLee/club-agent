package com.club.agent.storage;

import com.club.agent.config.UploadProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import java.util.UUID;

/**
 * 本地磁盘存储（开发模式）：uploads/{bizType}/{yyyyMMdd}/{uuid}.{ext}，
 * 通过 /uploads/** 静态映射访问（见 WebConfig）。
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "storage.mode", havingValue = "local", matchIfMissing = true)
public class LocalStorageServiceImpl extends AbstractStorageService {

    public LocalStorageServiceImpl(UploadProperties uploadProperties) {
        super(uploadProperties);
    }

    @Override
    public String upload(MultipartFile file, String bizType) {
        validate(file);
        return doUpload(file, bizType);
    }

    @Override
    public String upload(MultipartFile file, String bizType, Set<String> allowedExts) {
        validate(file, allowedExts);
        return doUpload(file, bizType);
    }

    private String doUpload(MultipartFile file, String bizType) {
        String ext = getExtension(file.getOriginalFilename());
        String date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String filename = UUID.randomUUID().toString().replace("-", "") + "." + ext;

        Path dir = Paths.get(uploadProperties.getLocalDir(), bizType, date);
        try {
            Files.createDirectories(dir);
            file.transferTo(dir.resolve(filename));
        } catch (IOException e) {
            throw new com.club.agent.exception.BizException(
                    com.club.agent.common.ResultCode.BIZ_UPLOAD_FAIL);
        }
        return "/uploads/" + bizType + "/" + date + "/" + filename;
    }

    @Override
    public void delete(String url) {
        if (url == null || !url.startsWith("/uploads/")) {
            return;
        }
        try {
            Path file = Paths.get(uploadProperties.getLocalDir(), url.substring("/uploads/".length()));
            Files.deleteIfExists(file);
        } catch (IOException e) {
            log.warn("本地文件删除失败: url={}, err={}", url, e.getMessage());
        }
    }
}
