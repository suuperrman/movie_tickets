package org.javaup.exception;

import org.javaup.enums.BaseCode;

/**
 * 认证模块业务异常
 * 继承 MovieFrameException，自动被全局异常处理器 WebExceptionAdvice 捕获
 */
public class AuthBusinessException extends MovieFrameException {

    public AuthBusinessException(BaseCode baseCode) {
        super(baseCode);
    }

    public AuthBusinessException(Integer code, String message) {
        super(code, message);
    }

    public AuthBusinessException(String message) {
        super(message);
    }
}
