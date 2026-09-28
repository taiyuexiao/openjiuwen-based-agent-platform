# agent-bridge：openJiuwen agent-core 真实底座 ↔ agentops-platform 联调桥

对二次调研结论的工程验证载体：

1. **底座可运行真实 Agent**：本服务的 `/query` 由 openJiuwen agent-core（PyPI `openjiuwen` 0.1.x）
   的 `ReActAgent` + `Runner` 流式执行，SSE 输出；不是手写协议 mimic。
2. **插件机制支持行内二开**：`echo_model_client.py` 是一个自定义模型 Provider 插件——
   继承底座 `BaseModelClient`、声明 `__client_name__ = "echo-bank-llmops"` 即被
   `ClientRegistry` 自动注册（`llm_echo-bank-llmops`）；Agent 侧
   `ModelClientConfig(client_provider="echo-bank-llmops")` 即路由到它，底座源码零改动。
3. **OTLP trace 与平台观测栈兼容**：`otlp_json_exporter.py` 的 `JsonOtlpHttpExporter`
   （继承 opentelemetry-sdk `SpanExporter`，手写 `ReadableSpan` → OTLP/HTTP JSON 序列化——
   官方 HTTP exporter 发 protobuf，平台只解析 JSON）经
   `init_observability(span_exporter_override=...)` 注入底座观测栈，
   span 实时 POST 到平台 `POST /v1/otlp/v1/traces`。

## 启动

```bash
cd agentops-platform/agent-bridge
/opt/homebrew/bin/python3.12 -m venv .venv
.venv/bin/pip install -r requirements.txt
.venv/bin/python app.py            # 默认监听 8180
```

验证：`curl localhost:8180/health` → `{"status":"UP"}`；
`curl -N -X POST localhost:8180/query -H 'Content-Type: application/json' -d '{"input":"你好","session":"s1"}'` 看到 SSE 帧流。

## 环境变量

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `BRIDGE_PORT` | `8180` | 服务端口 |
| `PLATFORM_OTLP_ENDPOINT` | `http://localhost:8091/v1/otlp/v1/traces` | 平台 OTLP/HTTP JSON 接收地址 |
| `PLATFORM_TOKEN` | `dev-machine-token` | 平台机器通道 `X-Platform-Token` |
| `AGENT_CODE` | `smoke-agent` | 须等于平台侧 agent code（平台按 resource 属性 `service.name` 匹配归属） |
| `AGENT_ENV` | `PROD` | 写入 resource 属性 `deployment.environment` |
| `ECHO_MOCK_TOOL_CALL` | 空 | 置 `1` 且请求带 tools 时 echo Provider 返回一个 mock `tool_calls`（验证 ReAct 工具路径） |

## SSE 帧约定

`POST /query`（及 `POST /`，平台 invoke 网关按 instanceUrl 原样转发、不追加路径，故两根路径同处理）：

- 每个 `OutputSchema` chunk 一帧：`data: {"type":"llm_output","index":0,"payload":{"content":"...","result_type":"answer"}}`
- 末帧：`data: {"type":"finished","finished":true,...}`；出错时 `{"type":"error","finished":true,...}`。

## 换成行内真实 LLMOps Provider 的步骤

1. 新建 Provider 类继承 `BaseModelClient`，改 `__client_name__`（如 `"bank-llmops-gw"`），
   应用启动时 import 该模块完成自动注册（同 `echo_model_client.py` 的机制）；
2. `invoke()` / `stream()` 内用 `self._build_request_params(...)` 组装 OpenAI 兼容请求体，
   HTTP 调用行内 LLMOps 网关（地址/密钥由 `ModelClientConfig.api_base/api_key` 注入），
   响应映射回 `AssistantMessage` / `AssistantMessageChunk`；
3. 保留 `trigger(LLMCallEvents.LLM_INPUT / LLM_OUTPUT)` 调用——底座观测栈的
   `chat {model}` span 由这些回调事件驱动；
4. Agent 侧只把 `client_provider` 换成新名字，`ReActAgentConfig`/prompt/工具零改动。
