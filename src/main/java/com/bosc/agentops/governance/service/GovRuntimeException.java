package com.bosc.agentops.governance.service;

import com.bosc.agentops.common.api.ErrorCode;

/**
 * 运行时面（/v1/invoke 等机器入口）错误：携带真实 HTTP 状态码 + 业务码，
 * 由 RuntimePlaneExceptionHandler 映射为统一错误结构（不走管理面 200+code 约定，
 * 机器调用方（HiAgent 等）依赖标准 HTTP 状态做异常映射）。
 */
public class GovRuntimeException extends RuntimeException {

    private final int httpStatus;
    private final ErrorCode errorCode;

    public GovRuntimeException(int httpStatus, ErrorCode errorCode, String message) {
        super(message);
        this.httpStatus = httpStatus;
        this.errorCode = errorCode;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
