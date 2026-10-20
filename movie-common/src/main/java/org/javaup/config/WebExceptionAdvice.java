package org.javaup.config;

import jakarta.servlet.http.HttpServletRequest;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.javaup.dto.Result;
import org.javaup.exception.ArgumentError;
import org.javaup.exception.AuthBusinessException;
import org.javaup.exception.MovieFrameException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 全局异常处理器（movie-common 公共模块，所有业务模块共用）
 *
 * 处理链路：
 * - MovieFrameException → 业务异常（含 AuthBusinessException，因为继承关系）
 * - MethodArgumentNotValidException → @Valid 参数校验异常
 * - Throwable → 兜底，返回"系统错误"
 */
@Slf4j
@RestControllerAdvice
public class WebExceptionAdvice {

    /**
     * 业务异常（包含所有继承 MovieFrameException 的异常）
     * 包括：AuthBusinessException（认证）、MovieFrameException（通用业务）
     */
    @ExceptionHandler(value = MovieFrameException.class)
    public Result<String> businessExceptionHandler(HttpServletRequest request, MovieFrameException ex) {
        log.error("业务异常 code={}, msg={} method={} uri={} query={}",
                ex.getCode(), ex.getMessage(),
                request.getMethod(), request.getRequestURL(), request.getQueryString(), ex);
        return Result.fail(ex.getCode(), ex.getMessage());
    }

    /**
     * 参数验证异常（@Valid / @Validated）
     */
    @SneakyThrows
    @ExceptionHandler(value = MethodArgumentNotValidException.class)
    public Result<List<ArgumentError>> validExceptionHandler(HttpServletRequest request, MethodArgumentNotValidException ex) {
        log.error("参数验证异常 method={} uri={} msg={}", request.getMethod(), request.getRequestURL(), ex.getMessage(), ex);
        BindingResult bindingResult = ex.getBindingResult();
        List<ArgumentError> argumentErrorList = bindingResult.getFieldErrors().stream()
                .map(fieldError -> {
                    ArgumentError ae = new ArgumentError();
                    ae.setArgumentName(fieldError.getField());
                    ae.setMessage(fieldError.getDefaultMessage());
                    return ae;
                }).collect(Collectors.toList());
        return Result.fail(argumentErrorList);
    }

    /**
     * 拦截未捕获异常（兜底）
     */
    @ExceptionHandler(value = Throwable.class)
    public Result<String> defaultErrorHandler(HttpServletRequest request, Throwable throwable) {
        log.error("全局异常 method={} uri={} query={}", request.getMethod(), request.getRequestURL(), request.getQueryString(), throwable);
        return Result.fail();
    }
}
