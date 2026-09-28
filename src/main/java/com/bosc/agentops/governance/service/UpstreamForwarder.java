package com.bosc.agentops.governance.service;

import com.bosc.agentops.common.api.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.io.InputStream;
import java.io.OutputStream;

/**
 * 上游转发器：基于 RestClient（spring-web 自带，不新增依赖）把请求体转发到部署实例，
 * 并把上游响应（状态码/Content-Type/字节流）逐块写回客户端响应。
 * SSE（text/event-stream）与普通 JSON 走同一条流式拷贝路径：每次读入即写出并 flush，
 * 因此 SSE 帧逐条透传。超时按 CallerPolicy.timeoutMs（连接/读超时同值）中断。
 */
@Component
public class UpstreamForwarder {

    private static final Logger log = LoggerFactory.getLogger(UpstreamForwarder.class);

    /** 转发结果：upstreamStatus 为上游 HTTP 状态码；completed=false 表示流式传输中途断开 */
    public record ForwardResult(int upstreamStatus, boolean completed) {
    }

    /**
     * 执行转发。传输层失败（不可达/超时）：响应未提交时抛 GovRuntimeException(502)；
     * 响应已提交（SSE 流已开始）时无法改写状态码，记录日志并返回 completed=false。
     */
    public ForwardResult forward(String url, byte[] body, String contentType, int timeoutMs,
                                 jakarta.servlet.http.HttpServletResponse response) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeoutMs);
        factory.setReadTimeout(timeoutMs);
        RestClient client = RestClient.builder().requestFactory(factory).build();

        int[] upstreamStatus = new int[1];
        try {
            client.post()
                    .uri(url)
                    .contentType(contentType == null || contentType.isBlank()
                            ? MediaType.APPLICATION_JSON : MediaType.parseMediaType(contentType))
                    .body(body == null ? new byte[0] : body)
                    .exchange((request, upstream) -> {
                        upstreamStatus[0] = upstream.getStatusCode().value();
                        response.setStatus(upstreamStatus[0]);
                        MediaType upstreamContentType = upstream.getHeaders().getContentType();
                        if (upstreamContentType != null) {
                            response.setContentType(upstreamContentType.toString());
                        }
                        try (InputStream in = upstream.getBody()) {
                            OutputStream out = response.getOutputStream();
                            byte[] buffer = new byte[2048];
                            int n;
                            while ((n = in.read(buffer)) != -1) {
                                out.write(buffer, 0, n);
                                out.flush();
                            }
                        }
                        return null;
                    });
            return new ForwardResult(upstreamStatus[0], true);
        } catch (GovRuntimeException e) {
            throw e;
        } catch (Exception e) {
            if (response.isCommitted()) {
                // SSE 流已开始，无法再改写为错误响应：中断流并交由调用方落 FAILED 记录
                log.warn("上游流式传输中断: url={}, error={}", url, e.getMessage());
                return new ForwardResult(upstreamStatus[0], false);
            }
            response.reset();
            throw new GovRuntimeException(502, ErrorCode.UPSTREAM_ERROR,
                    ErrorCode.UPSTREAM_ERROR.getDefaultMessage() + ": " + rootMessage(e));
        }
    }

    private static String rootMessage(Throwable e) {
        Throwable cause = e;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause.getClass().getSimpleName() + ": " + cause.getMessage();
    }
}
