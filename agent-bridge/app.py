# coding: utf-8
"""agent-bridge：跑在真实 openJiuwen agent-core 底座上的 HTTP 桥接服务。

- 底座：openjiuwen（PyPI 0.1.x）的 ReActAgent + Runner，真实执行（非 mock 协议）。
- 模型：自定义 Provider 插件 echo-bank-llmops（见 echo_model_client.py），
  验证底座插件机制支持行内二开；生产换成行内 LLMOps 网关 Provider 即可。
- 观测：启动时 init_observability + span_exporter_override 注入
  JsonOtlpHttpExporter，把 span 以 OTLP/HTTP JSON POST 到 agentops-platform
  （默认 http://localhost:8091/v1/otlp/v1/traces，X-Platform-Token 认证）。

端点：
- GET  /health      存活检查（平台 HealthEndpoint 用它）
- POST /query       业务入口：{"input": "...", "session": "..."} → SSE（text/event-stream）
- POST /            同 /query。平台 invoke 网关按 Deployment.instanceUrl 原样转发
                    （不追加路径），instanceUrl=http://localhost:8180 时落到根路径。

全部配置走环境变量（带默认值）：
  BRIDGE_PORT             默认 8180
  PLATFORM_OTLP_ENDPOINT  默认 http://localhost:8091/v1/otlp/v1/traces
  PLATFORM_TOKEN          默认 dev-machine-token
  AGENT_CODE              默认 smoke-agent（须等于平台侧 agent code：
                          平台按 resource 属性 service.name 匹配 agent code 落 span）
  AGENT_ENV               默认 PROD（写入 resource 属性 deployment.environment）
"""
from __future__ import annotations

import json
import os
import traceback
from typing import Any, AsyncIterator

import uvicorn
from fastapi import FastAPI
from fastapi.responses import JSONResponse, StreamingResponse
from pydantic import BaseModel

# 关键：import 即触发 EchoBankLlmopsModelClient.__init_subclass__ 自动注册
# （llm_echo-bank-llmops 进 ClientRegistry），须在创建 Model 之前完成。
import echo_model_client  # noqa: F401
from otlp_json_exporter import JsonOtlpHttpExporter

from openjiuwen.core.foundation.llm import ModelClientConfig, ModelRequestConfig
from openjiuwen.core.runner.runner import Runner
from openjiuwen.core.single_agent import AgentCard, ReActAgent, ReActAgentConfig
from openjiuwen.extensions.observability.config import ObservabilityConfig
from openjiuwen.extensions.observability.setup import force_flush_provider, init_observability

BRIDGE_PORT = int(os.environ.get("BRIDGE_PORT", "8180"))
PLATFORM_OTLP_ENDPOINT = os.environ.get(
    "PLATFORM_OTLP_ENDPOINT", "http://localhost:8091/v1/otlp/v1/traces")
PLATFORM_TOKEN = os.environ.get("PLATFORM_TOKEN", "dev-machine-token")
AGENT_CODE = os.environ.get("AGENT_CODE", "smoke-agent")
AGENT_ENV = os.environ.get("AGENT_ENV", "PROD")

app = FastAPI(title="agent-bridge", version="0.1.0")
_agent: ReActAgent | None = None


def _init_observability() -> None:
    """初始化底座观测栈，并把 span 导出指向 agentops-platform（OTLP/HTTP JSON）。

    两步：
    1. init_observability + span_exporter_override —— 共享 TracerProvider 挂上
       JsonOtlpHttpExporter（平台只收 OTLP/HTTP JSON）；
    2. harness 的 acquire_observability —— 打开单 Agent tracing 开关并安装
       run-root fallback。底座的 OtelCallbackHandler 只建 LLM/tool 子 span，
       Agent 根 span 归 host（本服务）所有，由 run_span 的 open/close 配对管理；
       provider 已存在时 acquire 只复用，不会覆盖上一步的 exporter。
    """
    config = ObservabilityConfig(
        enabled=True,
        service_name=AGENT_CODE,  # 平台按 service.name == agent code 归属 span
        sample_rate=1.0,
    )
    init_observability(
        config,
        span_exporter_override=JsonOtlpHttpExporter(
            endpoint=PLATFORM_OTLP_ENDPOINT,
            token=PLATFORM_TOKEN,
            extra_resource_attrs={"deployment.environment": AGENT_ENV},
        ),
    )
    from openjiuwen.harness.observability.setup import acquire_observability

    acquire_observability(config)


def _build_agent() -> ReActAgent:
    """用自定义 Provider（client_provider=echo-bank-llmops）装配真实 ReActAgent。"""
    model_client_config = ModelClientConfig(
        client_provider="echo-bank-llmops",
        # echo Provider 不校验这两项；生产 LLMOps Provider 时这里填网关地址/凭证
        api_key="echo",
        api_base="http://localhost:0/echo",
        verify_ssl=False,
    )
    model_config = ModelRequestConfig(model="echo-bank-model", temperature=0.0)
    agent_card = AgentCard(id="smoke_agent", description="agentops 平台端到端联调 echo Agent")
    config = ReActAgentConfig(
        model_client_config=model_client_config,
        model_config_obj=model_config,
        prompt_template=[
            {"role": "system", "content": "你是行内 AgentOps 平台联调用 echo 助手，直接回答即可。"}
        ],
        max_iterations=3,
    )
    return ReActAgent(card=agent_card).configure(config)


@app.on_event("startup")
async def _startup() -> None:
    global _agent
    _init_observability()
    _agent = _build_agent()


@app.on_event("shutdown")
async def _shutdown() -> None:
    force_flush_provider()


@app.get("/health")
async def health() -> JSONResponse:
    return JSONResponse({"status": "UP"})


class QueryReq(BaseModel):
    input: str = ""
    session: str = "default"


def _sse_frame(payload: Any) -> str:
    return f"data: {json.dumps(payload, ensure_ascii=False, default=str)}\n\n"


async def _run_agent_sse(req: QueryReq) -> AsyncIterator[str]:
    """Runner 流式执行 ReActAgent，把 OutputSchema chunk 逐帧转 SSE。

    用 open_agent_run_span / close_agent_run_span 包住整个 run：底座观测栈的
    LLM/tool span 需要 Agent 根 span 作父上下文，否则一个 span 都不会产生
    （根 span 归 host 所有，这是底座单 Agent 观测的约定）。
    """
    from openjiuwen.harness.observability.run_span import (
        close_agent_run_span,
        open_agent_run_span,
    )

    session_id = req.session or "default"
    index = 0
    answer = ""
    error: BaseException | None = None
    run_span = open_agent_run_span(session_id=session_id, mode="query")
    try:
        async for chunk in Runner.run_agent_streaming(
                _agent,
                {"query": req.input, "conversation_id": session_id},
                session=session_id,
        ):
            frame = {
                "type": getattr(chunk, "type", "output"),
                "index": getattr(chunk, "index", index),
                "payload": getattr(chunk, "payload", chunk),
            }
            if frame["type"] == "answer" and isinstance(frame["payload"], dict):
                answer = str(frame["payload"].get("output") or answer)
            index += 1
            yield _sse_frame(frame)
        force_flush_provider()
        yield _sse_frame({"type": "finished", "index": index, "finished": True,
                          "session": session_id, "payload": {"result_type": "answer"}})
    except Exception as exc:  # 出错也以 SSE 帧收尾，避免平台侧读超时
        traceback.print_exc()
        error = exc
        yield _sse_frame({"type": "error", "index": index, "finished": True,
                          "payload": {"error": f"{type(exc).__name__}: {exc}"}})
    finally:
        close_agent_run_span(run_span, session_id=session_id,
                             output=answer if answer else None, exception=error)


@app.post("/query")
async def query(req: QueryReq) -> StreamingResponse:
    return StreamingResponse(_run_agent_sse(req), media_type="text/event-stream")


@app.post("/")
async def query_root(req: QueryReq) -> StreamingResponse:
    # 平台 invoke 网关把请求原样转发到 Deployment.instanceUrl（不追加路径），
    # instanceUrl 为 http://localhost:8180 时落到根路径，因此与 /query 同处理。
    return StreamingResponse(_run_agent_sse(req), media_type="text/event-stream")


if __name__ == "__main__":
    uvicorn.run(app, host="0.0.0.0", port=BRIDGE_PORT, log_level="info")
