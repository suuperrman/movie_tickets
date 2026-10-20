package org.javaup.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 会话信息 VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionVO {

    /**
     * 会话 ID（即 Refresh Token ID）
     */
    private String sessionId;

    /**
     * 设备类型（User-Agent 解析简化）
     */
    private String deviceType;

    /**
     * 登录 IP
     */
    private String ip;

    /**
     * 登录时间
     */
    private String loginTime;

    /**
     * 是否当前会话
     */
    private Boolean current;
}
