package com.club.agent.vo;

import lombok.Builder;
import lombok.Data;

/**
 * 验证码响应：uuid 作为登录时的 captchaKey，图片为 base64（前端直接渲染）。
 */
@Data
@Builder
public class CaptchaVO {

    /** 验证码标识（登录时回传） */
    private String captchaKey;

    /** 图片 base64（不含 data:image 前缀） */
    private String imgBase64;
}
