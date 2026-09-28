package com.bosc.agentops.common.api;

import com.bosc.agentops.common.context.RequestContext;

/**
 * 统一响应体：code=0 成功，非 0 为业务错误码。
 */
public class ApiResponse<T> {

    private int code;
    private String message;
    private T data;
    private String requestId;

    public ApiResponse() {
    }

    public ApiResponse(int code, String message, T data, String requestId) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.requestId = requestId;
    }

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(0, "success", data, currentRequestId());
    }

    public static <T> ApiResponse<T> error(int code, String message) {
        return new ApiResponse<>(code, message, null, currentRequestId());
    }

    private static String currentRequestId() {
        RequestContext.Context ctx = RequestContext.current();
        return ctx == null ? null : ctx.getRequestId();
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }
}
