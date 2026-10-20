package org.javaup.utils;

import cn.hutool.captcha.CaptchaUtil;
import cn.hutool.captcha.LineCaptcha;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

import static org.javaup.utils.AuthRedisConstants.*;

/**
 * 图形验证码工具类
 * 利用 Hutool 的 LineCaptcha 生成简单的干扰线验证码
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CaptchaGenerator {

    private final StringRedisTemplate stringRedisTemplate;

    private static final int WIDTH = 120;
    private static final int HEIGHT = 40;
    private static final int CODE_COUNT = 4;

    /**
     * 生成图形验证码
     *
     * @return [captchaId, base64Image]
     */
    public String[] generate() {
        LineCaptcha captcha = CaptchaUtil.createLineCaptcha(WIDTH, HEIGHT, CODE_COUNT, 20);
        String code = captcha.getCode().toLowerCase();

        // 生成唯一 captchaId
        String captchaId = IdUtil.fastSimpleUUID();

        // 存入 Redis（code 统一转小写比较）
        stringRedisTemplate.opsForValue().set(
                CAPTCHA_KEY + captchaId,
                code,
                CAPTCHA_TTL_SECONDS,
                TimeUnit.SECONDS
        );

        // 转为 Base64
        String base64Image = toBase64(captcha);
        log.debug("生成验证码 captchaId={}, code={}", captchaId, code);

        return new String[]{captchaId, base64Image};
    }

    /**
     * 验证验证码（一次性使用，验证后立即删除）
     *
     * @param captchaId 验证码 ID
     * @param userCode  用户输入的验证码
     * @return true 验证通过
     */
    public boolean verify(String captchaId, String userCode) {
        if (StrUtil.isBlank(captchaId) || StrUtil.isBlank(userCode)) {
            return false;
        }
        String key = CAPTCHA_KEY + captchaId;
        String realCode = stringRedisTemplate.opsForValue().get(key);
        if (realCode == null) {
            return false;
        }
        // 一次性使用，无论成功失败都删除
        stringRedisTemplate.delete(key);
        return realCode.equalsIgnoreCase(userCode);
    }

    /**
     * 将验证码图片转为 Base64 字符串
     */
    private String toBase64(LineCaptcha captcha) {
        try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            captcha.write(os);
            byte[] data = os.toByteArray();
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(data);
        } catch (IOException e) {
            log.error("验证码图片生成失败", e);
            throw new RuntimeException("验证码生成失败");
        }
    }
}
