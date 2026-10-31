package org.javaup.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.javaup.dto.Result;
import org.javaup.service.OssService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * 管理端对象存储接口（/admin/oss）
 *
 * 接口清单：
 *   POST /admin/oss/upload  单文件上传（管理端电影/演员/影院/用户头像）
 */
@Slf4j
@RestController
@RequestMapping("/oss")
@RequiredArgsConstructor
public class AdminOssController {

    private final OssService ossService;

    /**
     * 单文件上传
     *
     * @param file  Spring MultipartFile
     * @param bizDir 业务目录（movie / actor / cinema / avatar...），默认 default
     * @return { "fileUrl": "https://..." }
     */
    @PostMapping("/upload")
    public Result<Map<String, String>> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "bizDir", defaultValue = "default") String bizDir) {
        log.info("[Admin] OSS 上传 bizDir={} original={} size={}", bizDir, file.getOriginalFilename(), file.getSize());
        String url = ossService.upload(file, bizDir);
        return Result.ok(Map.of("fileUrl", url));
    }
}
