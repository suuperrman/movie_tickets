package org.javaup.config;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 阿里云 OSS 客户端配置
 *
 * @see OssProperties
 */
@Configuration
@EnableConfigurationProperties(OssProperties.class)
public class OssConfig {

    /**
     * 单例 OSS 客户端
     * SDK 3.x 使用 OSSClientBuilder.build(endpoint, accessKeyId, accessKeySecret)
     * 生产建议换 STS 临时凭证，但当前用 AccessKey 简单场景够用
     */
    @Bean(destroyMethod = "shutdown")
    public OSS ossClient(OssProperties props) {
        return new OSSClientBuilder().build(props.getEndpoint(),
                props.getAccessKeyId(), props.getAccessKeySecret());
    }
}
