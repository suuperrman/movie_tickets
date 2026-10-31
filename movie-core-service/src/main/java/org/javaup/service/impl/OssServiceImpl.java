package org.javaup.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.aliyun.oss.OSS;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.OSSException;
import com.aliyun.oss.ClientException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.javaup.config.OssProperties;
import org.javaup.exception.MovieFrameException;
import org.javaup.service.OssService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Set;

/**
 * 阿里云 OSS 服务实现
 *
 * 对象 key 规则：
 *   {prefix}/{bizDir}/yyyy/MM/dd/{雪花ID}{.ext}
 *   例：oss/movie/2026/10/21/1892039487589237462.jpg
 *
 * 文件 URL 规则：
 *   优先 domain → {domain}/{key}
 *   否则       → https://{bucketName}.{endpoint_host}/{key}
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OssServiceImpl implements OssService {

    private final OSS ossClient;
    private final OssProperties props;

    /**
     * 允许的扩展名白名单（唯一权威）
     *
     * 安全说明：
     *   以后缀为唯一判断依据，完全忽略 file.getContentType()。
     *   原因：content-type 前端可伪造，攻击者可以把 .html/.svg 改后缀成 .jpg 绕过。
     *   白名单只开放图片 + 视频 + PDF，绝不开放 .html / .svg / .exe / .jsp / .php。
     */
    private static final Set<String> ALLOWED_EXTS = Set.of(
            "jpg", "jpeg", "png", "gif", "webp", "bmp", "tiff",
            "mp4", "mov",
            "pdf"
    );

    /** 后缀 → MIME 映射表（与 ALLOWED_EXTS 一一对应） */
    private static final Map<String, String> EXT_MIME_MAP = Map.ofEntries(
            Map.entry("jpg", "image/jpeg"),
            Map.entry("jpeg", "image/jpeg"),
            Map.entry("png", "image/png"),
            Map.entry("gif", "image/gif"),
            Map.entry("webp", "image/webp"),
            Map.entry("bmp", "image/bmp"),
            Map.entry("tiff", "image/tiff"),
            Map.entry("mp4", "video/mp4"),
            Map.entry("mov", "video/quicktime"),
            Map.entry("pdf", "application/pdf")
    );

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    // ==================== 1. 上传（带业务目录） ====================

    @Override
    public String upload(MultipartFile file, String bizDir) {
        validate(file);

        String ext = getExt(file.getOriginalFilename());
        String key = buildKey(bizDir, ext);

        try (InputStream is = file.getInputStream()) {
            ObjectMetadata meta = new ObjectMetadata();
            meta.setContentLength(file.getSize());
            // contentType 只从后缀白名单查，绝不信前端传的 content-type
            meta.setContentType(resolveContentTypeByExt(ext));
            // 缓存 30 天（雪花 ID 每次上传都唯一，不存在覆盖旧文件的问题）
            meta.setCacheControl("max-age=2592000, public");

            ossClient.putObject(props.getBucketName(), key, is, meta);
            String url = buildUrl(key);
            log.info("[OSS] 上传成功 key={} size={} url={}", key, file.getSize(), url);
            return url;
        } catch (OSSException | ClientException e) {
            log.error("[OSS] 上传失败 endpoint={} bucket={} code={} msg={}",
                    props.getEndpoint(), props.getBucketName(),
                    e instanceof OSSException ? ((OSSException) e).getErrorCode() : "-",
                    e.getMessage(), e);
            throw new MovieFrameException("文件上传失败，请稍后重试");
        } catch (Exception e) {
            log.error("[OSS] 上传异常", e);
            throw new MovieFrameException("文件上传失败，请稍后重试");
        }
    }

    // ==================== 2. 上传（不带业务目录） ====================

    @Override
    public String upload(MultipartFile file) {
        return upload(file, null);
    }

    // ==================== 3. 删除 ====================

    @Override
    public void delete(String objectKey) {
        try {
            ossClient.deleteObject(props.getBucketName(), objectKey);
            log.info("[OSS] 删除成功 key={}", objectKey);
        } catch (OSSException | ClientException e) {
            log.error("[OSS] 删除失败 key={} msg={}", objectKey, e.getMessage(), e);
            throw new MovieFrameException("文件删除失败，请稍后重试");
        }
    }

    // ==================== 内部辅助 ====================

    /**
     * 校验文件大小 + 类型（**后缀白名单为唯一依据**，忽略 content-type）
     *
     * 双重大小校验：
     *   1. Spring Boot multipart 配置（application.yml）在 HTTP 层先拦一遍 → 超限直接返回 413
     *   2. 这里再拦一遍，防御绕过 multipart 直接写 InputStream 的攻击
     */
    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new MovieFrameException("上传文件不能为空");
        }
        // 大小校验（max-size-mb 默认 10MB）
        long maxBytes = props.getMaxSizeMb() * 1024L * 1024L;
        if (file.getSize() > maxBytes) {
            throw new MovieFrameException("文件大小超过 " + props.getMaxSizeMb() + " MB 限制");
        }
        // 类型校验：**只信后缀，完全忽略 file.getContentType()**
        String ext = getExt(file.getOriginalFilename()).toLowerCase();
        if (!ALLOWED_EXTS.contains(ext)) {
            log.warn("[OSS] 拒绝上传非法后缀 ext={} filename={}", ext, file.getOriginalFilename());
            throw new MovieFrameException("不支持的文件类型：." + ext + "，仅允许图片/视频/PDF");
        }
    }

    /** 构造对象 key（bizDir 空值强制兜底 default，绝不生成 null/xxx 路径） */
    private String buildKey(String bizDir, String ext) {
        StringBuilder sb = new StringBuilder();
        if (StrUtil.isNotBlank(props.getPrefix())) {
            sb.append(trimSlash(props.getPrefix())).append("/");
        }
        // bizDir 为空统一归入 default 目录，避免 key 结构混乱
        String dir = StrUtil.isNotBlank(bizDir) ? trimSlash(bizDir) : "default";
        sb.append(dir).append("/");
        sb.append(LocalDate.now().format(DATE_FMT)).append("/");
        sb.append(IdUtil.getSnowflakeNextId());
        if (StrUtil.isNotBlank(ext)) {
            sb.append(".").append(ext);
        }
        return sb.toString();
    }

    /** 生成完整访问 URL */
    private String buildUrl(String key) {
        if (StrUtil.isNotBlank(props.getDomain())) {
            String domain = trimSlash(props.getDomain());
            return domain + "/" + key;
        }
        // 默认 https://{bucket}.{host}/{key}
        String endpoint = props.getEndpoint();
        // endpoint 形如 https://oss-cn-beijing.aliyuncs.com → host 是 oss-cn-beijing.aliyuncs.com
        String host = endpoint.replaceFirst("^https?://", "");
        String proto = endpoint.startsWith("https") ? "https" : "http";
        return proto + "://" + props.getBucketName() + "." + host + "/" + key;
    }

    private static String trimSlash(String s) {
        return StrUtil.removePrefix(StrUtil.removeSuffix(s, "/"), "/");
    }

    private static String getExt(String filename) {
        if (StrUtil.isBlank(filename)) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot + 1) : "";
    }

    /**
     * 根据后缀查 MIME（**只信扩展名，绝不信 file.getContentType()**）
     *
     * 已经 validate 过后缀在白名单里，这里直接从 EXT_MIME_MAP 取值即可。
     */
    private static String resolveContentTypeByExt(String ext) {
        return EXT_MIME_MAP.getOrDefault(ext.toLowerCase(), "application/octet-stream");
    }
}
