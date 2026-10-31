package org.javaup.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 阿里云 OSS 配置属性
 *
 * 映射 yml 节点：aliyun.oss.*
 * 敏感凭证通过环境变量覆盖，yml 里只写占位符：
 *   ${OSS_ACCESS_KEY_ID} / ${OSS_ACCESS_KEY_SECRET}
 */
@Slf4j
@Data
@ConfigurationProperties(prefix = "aliyun.oss")
public class OssProperties {

    /** OSS endpoint，如 https://oss-cn-beijing.aliyuncs.com */
    private String endpoint;

    /** AccessKeyId（敏感，生产必须用环境变量覆盖） */
    private String accessKeyId;

    /** AccessKeySecret（敏感，生产必须用环境变量覆盖） */
    private String accessKeySecret;

    /** Bucket 名称 */
    private String bucketName;

    /** Bucket 所在地域 */
    private String region;

    /** 自定义 CDN / Bucket 外网域名（可选） */
    private String domain = "";

    /** 上传文件路径前缀（可选，按业务分目录） */
    private String prefix = "";

    /** 最大上传大小（MB），默认 10 */
    private Integer maxSizeMb = 10;

    /**
     * 启动时脱敏打印配置，避免 AK/SK 泄露
     */
    @PostConstruct
    public void logSanitized() {
        log.info("[OSS] endpoint={}, bucket={}, region={}, domain={}, prefix={}, maxSizeMb={}",
                endpoint, bucketName, region, domain, prefix, maxSizeMb);
        // AK/SK 各只打印前 4 位 + **** 后 4 位
        log.info("[OSS] accessKeyId={}, accessKeySecret={}",
                mask(accessKeyId, 4), mask(accessKeySecret, 4));
    }

    private static String mask(String raw, int showLen) {
        if (raw == null || raw.length() <= showLen * 2) return "****";
        return raw.substring(0, showLen) + "****" + raw.substring(raw.length() - showLen);
    }
}
