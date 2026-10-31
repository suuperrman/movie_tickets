package org.javaup.service;

import org.springframework.web.multipart.MultipartFile;

/**
 * 对象存储服务接口
 */
public interface OssService {

    /**
     * 上传文件
     *
     * @param file   Spring MultipartFile
     * @param bizDir 业务子目录（如 movie / actor / cinema / avatar）
     * @return 文件可访问 URL
     */
    String upload(MultipartFile file, String bizDir);

    /**
     * 上传文件（不做业务目录区分，走配置的 prefix）
     */
    String upload(MultipartFile file);

    /**
     * 删除对象（后台管理用）
     *
     * @param objectKey 对象 key（不含 bucket 前缀）
     */
    void delete(String objectKey);
}
