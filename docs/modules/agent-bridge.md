# 模块：openJiuwen 底座桥接服务（agent-bridge）

## 摘要
`agent-bridge/`：基于真实 openJiuwen agent-core（PyPI 0.1.18.post1）的薄适配服务，验证并承载
「底座运行真实 Agent + 行内插件二开 + OTel trace 回传平台」三项能力。生产形态是每类 Agent 一个这样的服务（或并入底座部署）。

## 状态
首版完成并联调通过（2026-09-27）。证据：经平台网关 `/v1/invoke/smoke-agent/PROD` 拿到真实 SSE 流（echo 回显 + finished 帧 + X-Trace-Id），
平台 traces/query 按 agent+env 查到 3 个同 traceId 的 span（AGENT 根 span → llm.call → context.window.commit），层级完整。

## 组成
- `echo_model_client.py`：自定义模型 Provider 插件。继承 BaseModelClient、声明 `__client_name__="echo-bank-llmops"` 自动注册（ClientRegistry 机制实测有效）。
  **换行内 LLMOps 网关时只需替换此类的 invoke/stream 内部 HTTP 调用，Agent 配置零改动。**
- `otlp_json_exporter.py`：JsonOtlpHttpExporter（SpanExporter 子类）。注意：Python 官方 OTLP HTTP exporter 发 protobuf，
  平台只解析 JSON，故手写序列化；经 `init_observability(span_exporter_override=...)` 注入（公开扩展点）。
- `app.py`：FastAPI(8180)：`GET /health`、`POST /query` 与 `POST /`（SSE）。
- 配置全走环境变量（平台地址、机器 token、agent code、端口）。Python ≥3.11（agent-core 硬性要求），`.venv` 已建好可复跑。

## 关键经验（接底座观测栈必读）
1. **底座不自动建 Agent 根 span**：OtelCallbackHandler 只建 LLM/tool 子 span 且需要父上下文；根 span 须由宿主用
   `open_agent_run_span`/`close_agent_run_span` 配对产生。不建根 span 会导致零 span 导出——这是二开接观测最容易踩的坑。
2. **LLM span 由回调事件驱动**：自定义 Provider 必须触发 `LLMCallEvents.LLM_INPUT/LLM_OUTPUT`（参照官方 OpenAI client），否则 chat span 缺字段。
3. **平台 invoke 转发不追加路径**：UpstreamForwarder 原样 POST 到 Deployment.instanceUrl 根路径，所以 bridge 在 `POST /` 也挂了 query 处理。
4. **traceId 双轨**：平台 InvocationRecord 用 UUID，OTel 用 hex traceId，二者未关联；span 归属靠 resource 属性 `service.name`=agent code 映射。
   若需 invoke↔trace 精确串联，需平台把 X-Trace-Id 以 traceparent 透传上游（列入主文档待办）。

## 复跑
`cd agent-bridge && .venv/bin/python app.py`；平台侧按 scripts/README.md 或 docs/modules/delivery.md 的 HOSTED 通路登记部署（instance_url=http://localhost:8180）。
