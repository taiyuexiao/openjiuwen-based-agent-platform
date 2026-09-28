package com.bosc.agentops.governance.controller;

import com.bosc.agentops.governance.service.InvokeGatewayService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * 统一调用入口（运行时面，机器身份认证，不走管理面 @RequirePermission 体系）。
 * 成功时上游响应（含 SSE 流）直接透传；失败由 RuntimePlaneExceptionHandler
 * 映射为真实 HTTP 状态码 + 统一错误结构。
 */
@RestController
public class RuntimeInvokeController {

    private final InvokeGatewayService invokeGatewayService;

    public RuntimeInvokeController(InvokeGatewayService invokeGatewayService) {
        this.invokeGatewayService = invokeGatewayService;
    }

    @PostMapping("/v1/invoke/{agentCode}/{env}")
    public void invoke(@PathVariable String agentCode,
                       @PathVariable String env,
                       @RequestHeader(value = "X-Caller-Id", required = false) String callerId,
                       @RequestHeader(value = "X-Caller-Type", required = false) String callerType,
                       @RequestHeader(value = "X-Agent-Version", required = false) String agentVersion,
                       @RequestHeader(value = "Authorization", required = false) String authorization,
                       @RequestHeader(value = "Content-Type", required = false) String contentType,
                       @RequestBody(required = false) byte[] body,
                       HttpServletResponse response) {
        invokeGatewayService.invoke(agentCode, env, callerId, callerType, agentVersion,
                authorization, contentType, body, response);
    }
}
