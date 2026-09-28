package com.bosc.agentops.governance.controller;

import com.bosc.agentops.common.api.ApiResponse;
import com.bosc.agentops.common.api.ErrorCode;
import com.bosc.agentops.governance.service.GovRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 运行时面（/v1/invoke）异常映射：真实 HTTP 状态码 + 统一错误结构 {code,message,data,requestId}。
 * 与管理面（HTTP 200 + 业务码）区分：机器调用方（HiAgent、Agent 间调用）依赖标准状态码做异常映射。
 * 需显式最高优先级，否则全局 GlobalExceptionHandler 的 Exception 兜底会抢先处理。
 */
@RestControllerAdvice(assignableTypes = RuntimeInvokeController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RuntimePlaneExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(RuntimePlaneExceptionHandler.class);

    @ExceptionHandler(GovRuntimeException.class)
    public ResponseEntity<ApiResponse<Void>> handleGovRuntime(GovRuntimeException e) {
        return ResponseEntity.status(e.getHttpStatus())
                .body(ApiResponse.error(e.getErrorCode().getCode(), e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnknown(Exception e) {
        log.error("运行时面未处理异常", e);
        return ResponseEntity.status(500)
                .body(ApiResponse.error(ErrorCode.INTERNAL_ERROR.getCode(),
                        ErrorCode.INTERNAL_ERROR.getDefaultMessage()));
    }
}
