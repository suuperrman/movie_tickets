package org.javaup.utils;

import cn.hutool.core.util.StrUtil;

/**
 * 敏感数据脱敏工具类。
 * <p>
 * 对手机号、身份证号、银行卡号、邮箱、姓名等敏感信息进行脱敏处理，
 * 防止数据泄露。
 * </p>
 */
public final class MaskUtil {

    private MaskUtil() {}

    /**
     * 手机号脱敏：保留前3位和后4位，中间用*代替
     * 示例：13812345678 → 138****5678
     */
    public static String maskPhone(String phone) {
        if (StrUtil.isBlank(phone) || phone.length() < 7) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    /**
     * 身份证号脱敏：保留前4位和后4位，中间用*代替
     * 示例：110101199001011234 → 1101**********1234
     */
    public static String maskIdCard(String idCard) {
        if (StrUtil.isBlank(idCard) || idCard.length() < 8) {
            return idCard;
        }
        return idCard.substring(0, 4) + "**********" + idCard.substring(idCard.length() - 4);
    }

    /**
     * 银行卡号脱敏：保留前4位和后4位，中间用*代替
     * 示例：6222021234567890123 → 6222***********0123
     */
    public static String maskBankCard(String bankCard) {
        if (StrUtil.isBlank(bankCard) || bankCard.length() < 8) {
            return bankCard;
        }
        return bankCard.substring(0, 4) + "***********" + bankCard.substring(bankCard.length() - 4);
    }

    /**
     * 邮箱脱敏：保留前2位和域名，中间用*代替
     * 示例：zhangsan@example.com → zh****@example.com
     */
    public static String maskEmail(String email) {
        if (StrUtil.isBlank(email) || !email.contains("@")) {
            return email;
        }
        String[] parts = email.split("@", 2);
        String prefix = parts[0];
        if (prefix.length() <= 2) {
            return prefix + "****@" + parts[1];
        }
        return prefix.substring(0, 2) + "****@" + parts[1];
    }

    /**
     * 姓名脱敏：保留姓氏，名字用*代替
     * 示例：张三 → 张*，张三丰 → 张**
     */
    public static String maskName(String name) {
        if (StrUtil.isBlank(name)) {
            return name;
        }
        if (name.length() == 1) {
            return name;
        }
        return name.charAt(0) + "*".repeat(name.length() - 1);
    }

    /**
     * 地址脱敏：保留前6位，后面用*代替
     * 示例：北京市朝阳区某某街道123号 → 北京市朝阳区********
     */
    public static String maskAddress(String address) {
        if (StrUtil.isBlank(address) || address.length() <= 6) {
            return address;
        }
        return address.substring(0, 6) + "********";
    }

    /**
     * 密码脱敏：固定返回 ******
     */
    public static String maskPassword(String password) {
        return "******";
    }
}
