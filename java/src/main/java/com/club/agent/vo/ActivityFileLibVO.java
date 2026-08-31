package com.club.agent.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 活动资料库列表项（双项目集成任务5）。
 * Long 型雪花 ID 序列化为字符串（超 JS 安全整数，前端需回传 id 发 DELETE；项目惯例）。
 */
@Data
public class ActivityFileLibVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long activityId;
    private String filename;
    private Long fileSize;
    private String storageUrl;
    /** rag 入库状态：pending/parsing/success/partial/failed/voided */
    private String ragStatus;
    private String uploaderName;
    private LocalDateTime createdAt;
}
