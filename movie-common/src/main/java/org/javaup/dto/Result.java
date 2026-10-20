package org.javaup.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.javaup.enums.BaseCode;

/**
 * 统一响应结果（movie-common 公共模块，所有业务模块共用）
 *
 * @param <T> data 类型
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result<T> {
    private Boolean success;
    private String errorMsg;
    private T data;
    private Long total;

    public static <T> Result<T> ok() {
        Result<T> result = new Result<>();
        result.setSuccess(true);
        return result;
    }

    public static <T> Result<T> ok(T data) {
        Result<T> result = new Result<>();
        result.setSuccess(true);
        result.setData(data);
        return result;
    }

    public static <T> Result<T> ok(T data, Long total) {
        Result<T> result = new Result<>();
        result.setSuccess(true);
        result.setData(data);
        result.setTotal(total);
        return result;
    }

    public static <T> Result<T> fail() {
        Result<T> result = new Result<>();
        result.setSuccess(false);
        result.setErrorMsg("系统错误，请稍后重试!");
        return result;
    }

    public static <T> Result<T> fail(String errorMsg) {
        Result<T> result = new Result<>();
        result.setSuccess(false);
        result.setErrorMsg(errorMsg);
        return result;
    }

    public static <T> Result<T> fail(T data) {
        Result<T> result = new Result<>();
        result.setSuccess(false);
        result.setData(data);
        return result;
    }

    public static <T> Result<T> fail(BaseCode baseCode) {
        Result<T> result = new Result<>();
        result.setSuccess(false);
        result.setErrorMsg(baseCode.getMsg());
        return result;
    }

    public static <T> Result<T> fail(Integer code, String errorMsg) {
        Result<T> result = new Result<>();
        result.setSuccess(false);
        result.setErrorMsg(errorMsg);
        return result;
    }
}
