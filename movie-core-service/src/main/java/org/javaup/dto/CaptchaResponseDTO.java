package org.javaup.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 验证码响应 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CaptchaResponseDTO {

    /**
     * 验证码 ID（前端提交时带上）
     */
    private String captchaId;

    /**
     * 验证码图片（Base64 编码）
     */
    private String captchaImage;

    /**
     * 是否需要验证码（连续失败 3 次后才需要）
     */
    private Boolean required;
}
