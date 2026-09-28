package com.bosc.agentops.common.api;

/**
 * 业务错误码。code=0 保留给成功。
 */
public enum ErrorCode {

    PARAM_INVALID(40001, "参数校验失败"),
    UNAUTHORIZED(40101, "未认证或凭证无效"),
    FORBIDDEN(40301, "无权限执行该操作"),
    NOT_FOUND(40401, "资源不存在"),
    DUPLICATE(40901, "资源已存在"),
    STATE_CONFLICT(40902, "状态冲突，操作不被允许"),
    DECLARATION_INVALID(40910, "声明登记校验未通过"),
    RATE_LIMITED(42901, "调用频率超限"),
    UPSTREAM_ERROR(50201, "上游服务不可达"),
    INTERNAL_ERROR(50000, "系统内部错误");

    private final int code;
    private final String defaultMessage;

    ErrorCode(int code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    public int getCode() {
        return code;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }
}
