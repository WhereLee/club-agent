package com.club.agent.storage;

import com.club.agent.config.UploadProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 本地磁盘存储（开发模式）：uploads/{bizType}/{yyyyMMdd}/{uuid}.{ext}，
 * 通过 /uploads/** 静态映射访问（见 WebConfig）。
 */
@Service
@ConditionalOnProperty(name = "storage.mode", havingValue = "local", matchIfMissing = true)
public class LocalStorageServiceImpl extends AbstractStorageService {

    public LocalStorageServiceImpl(UploadProperties uploadProperties) {
        super(uploadProperties);
    }

    @Override
    public String upload(MultipartFile file, String bizType) {
        validate(file);
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
}
