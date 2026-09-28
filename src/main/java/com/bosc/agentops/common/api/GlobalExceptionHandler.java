package com.bosc.agentops.common.api;

import com.bosc.agentops.common.context.RequestContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

/**
 * 全局异常处理：业务异常 / 参数校验异常 / 未知异常统一映射为 {code,message,data,requestId}。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BizException.class)
    public ApiResponse<Void> handleBiz(BizException e) {
        return ApiResponse.error(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(BindException.class)
    public ApiResponse<Void> handleBind(BindException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(GlobalExceptionHandler::formatFieldError)
                .collect(Collectors.joining("; "));
        return ApiResponse.error(ErrorCode.PARAM_INVALID.getCode(),
                ErrorCode.PARAM_INVALID.getDefaultMessage() + ": " + detail);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ApiResponse<Void> handleMethodValidation(HandlerMethodValidationException e) {
        return ApiResponse.error(ErrorCode.PARAM_INVALID.getCode(),
                ErrorCode.PARAM_INVALID.getDefaultMessage() + ": " + e.getMessage());
    }

    @ExceptionHandler({MissingServletRequestParameterException.class,
            MissingServletRequestPartException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class})
    public ApiResponse<Void> handleBadRequest(Exception e) {
        return ApiResponse.error(ErrorCode.PARAM_INVALID.getCode(),
                ErrorCode.PARAM_INVALID.getDefaultMessage() + ": " + e.getMessage());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ApiResponse<Void> handleMaxUploadSize(MaxUploadSizeExceededException e) {
        return ApiResponse.error(ErrorCode.PARAM_INVALID.getCode(),
                ErrorCode.PARAM_INVALID.getDefaultMessage() + ": 文件大小超过上限 20MB");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ApiResponse<Void> handleNotFound(NoResourceFoundException e) {
        return ApiResponse.error(ErrorCode.NOT_FOUND.getCode(), ErrorCode.NOT_FOUND.getDefaultMessage());
    }

    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleUnknown(Exception e) {
        String requestId = RequestContext.current() == null ? null : RequestContext.current().getRequestId();
        log.error("未处理异常, requestId={}", requestId, e);
        return ApiResponse.error(ErrorCode.INTERNAL_ERROR.getCode(), ErrorCode.INTERNAL_ERROR.getDefaultMessage());
    }

    private static String formatFieldError(FieldError fe) {
        return fe.getField() + " " + (fe.getDefaultMessage() == null ? "invalid" : fe.getDefaultMessage());
    }
}
