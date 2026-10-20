package org.javaup.enums;

import lombok.Getter;

/**
 * @program: movie-ticket
 * @description: 接口返回code码
 * @author: xiong
 **/
public enum BaseCode {
    /**
     * 基础code码
     */
    SUCCESS(0, "OK"),

    // ========== 秒杀业务 (10xxx) ==========
    SECKILL_VOUCHER_NOT_EXIST(10001, "秒杀优惠券不存在"),
    SECKILL_VOUCHER_NOT_BEGIN(10002, "秒杀优惠券未开始"),
    SECKILL_VOUCHER_IS_OVER(10003, "秒杀优惠券已结束"),
    SECKILL_VOUCHER_STOCK_NOT_EXIST(10004, "秒杀优惠券库存不存在"),
    SECKILL_VOUCHER_STOCK_INSUFFICIENT(10005, "秒杀优惠券库存不足"),
    SECKILL_VOUCHER_CLAIM(10006, "秒杀优惠券已领取"),
    SECKILL_RATE_LIMIT_IP_EXCEEDED(10007, "请求过于频繁，请稍后再试"),
    SECKILL_RATE_LIMIT_USER_EXCEEDED(10008, "操作过于频繁，请稍后再试"),
    SECKILL_VOUCHER_ORDER_NOT_EXIST(10009, "优惠券订单不存在"),
    AFTER_SECKILL_VOUCHER_REMAIN_STOCK_NOT_NEGATIVE_NUMBER(10010, "修改后的剩余库存不能为负数"),
    VOUCHER_UNAVAILABLE(10011, "优惠券已下架"),
    VOUCHER_EXPIRED(10012, "优惠券已过期"),
    VOUCHER_ORDER_EXIST(10013, "优惠券订单已存在"),
    VOUCHER_ORDER_CANCEL(10014, "优惠券订单已取消"),

    // ========== 用户业务 (20xxx) ==========
    USER_NOT_EXIST(20000, "用户不存在"),
    USER_ALREADY_PURCHASE(20001, "用户已经购买"),

    // ========== 认证模块 (40xxx) ==========
    PARAM_ERROR(40000, "参数错误"),
    CAPTCHA_REQUIRED(40001, "请先完成图形验证码"),
    CAPTCHA_INVALID(40002, "验证码错误或已过期"),

    AUTH_FAILED(40100, "用户名或密码错误"),
    TOKEN_EXPIRED(40101, "令牌已过期"),
    TOKEN_INVALID(40102, "令牌无效"),
    TOKEN_REVOKED(40103, "令牌已吊销"),
    REFRESH_TOKEN_REPLAYED(40104, "登录状态已变更，请重新登录"),
    PASSWORD_CHANGED(40105, "密码已修改，请重新登录"),

    ACCOUNT_LOCKED(40300, "账号已被锁定，请稍后重试"),
    ACCOUNT_DISABLED(40301, "账号已被禁用"),

    LOGIN_RATE_LIMITED(42900, "登录尝试过于频繁，请稍后再试"),
    IP_RATE_LIMITED(42901, "当前 IP 请求过于频繁"),

    USER_ALREADY_EXIST(50001, "该手机号已注册"),
    EMAIL_NOT_MATCH(50002, "邮箱与注册邮箱不匹配"),
    RESET_TOKEN_INVALID(50003, "重置令牌无效或已使用"),
    RESET_TOKEN_EXPIRED(50004, "重置令牌已过期"),
    OLD_PASSWORD_WRONG(50005, "原密码错误"),
    ;

    @Getter
    private final Integer code;

    private String msg = "";

    BaseCode(Integer code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    public String getMsg() {
        return this.msg == null ? "" : this.msg;
    }

    public static String getMsg(Integer code) {
        for (BaseCode re : BaseCode.values()) {
            if (re.code.intValue() == code.intValue()) {
                return re.msg;
            }
        }
        return "";
    }

    public static BaseCode getRc(Integer code) {
        for (BaseCode re : BaseCode.values()) {
            if (re.code.intValue() == code.intValue()) {
                return re;
            }
        }
        return null;
    }
}
