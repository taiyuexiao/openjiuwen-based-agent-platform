package com.bosc.agentops.observability.support;

import com.bosc.agentops.common.api.ErrorCode;

/**
 * 机器通道异常：带真实 HTTP 状态码（机器调用方依赖标准状态码）。
 */
public class MachineChannelException extends RuntimeException {

    private final int httpStatus;
    private final ErrorCode errorCode;

    public MachineChannelException(int httpStatus, ErrorCode errorCode, String message) {
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
