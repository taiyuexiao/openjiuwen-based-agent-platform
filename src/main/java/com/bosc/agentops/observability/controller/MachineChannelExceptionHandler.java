package com.bosc.agentops.observability.controller;

import com.bosc.agentops.common.api.ApiResponse;
import com.bosc.agentops.common.api.ErrorCode;
import com.bosc.agentops.observability.support.MachineChannelException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 机器通道（OTLP 接收 / 告警 webhook）异常映射：真实 HTTP 状态码 + 统一错误结构。
 * 需显式最高优先级，否则 GlobalExceptionHandler 的 Exception 兜底会抢先处理。
 */
@RestControllerAdvice(assignableTypes = {OtlpController.class, AlertWebhookController.class})
@Order(Ordered.HIGHEST_PRECEDENCE)
public class MachineChannelExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(MachineChannelExceptionHandler.class);

    @ExceptionHandler(MachineChannelException.class)
    public ResponseEntity<ApiResponse<Void>> handleMachineChannel(MachineChannelException e) {
        return ResponseEntity.status(e.getHttpStatus())
                .body(ApiResponse.error(e.getErrorCode().getCode(), e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnknown(Exception e) {
        log.error("机器通道未处理异常", e);
        return ResponseEntity.status(500)
                .body(ApiResponse.error(ErrorCode.INTERNAL_ERROR.getCode(),
                        ErrorCode.INTERNAL_ERROR.getDefaultMessage()));
    }
}
