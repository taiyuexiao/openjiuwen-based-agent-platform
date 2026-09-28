# coding: utf-8
"""自定义模型 Provider 插件：echo-bank-llmops（行内二开验证用 echo 实现）。

机制说明（这是 openJiuwen agent-core 的标准插件点，不需要改底座任何代码）：
- 继承 ``BaseModelClient`` 并声明 ``__client_name__``，``__init_subclass__``
  会把本类自动注册进全局 ``ClientRegistry``（键为 ``llm_echo-bank-llmops``）。
  只要本模块被 import（app.py 顶部 import 本文件），注册即生效。
- Agent 侧 ``ModelClientConfig(client_provider="echo-bank-llmops")`` 时，
  ``create_model_client`` 在注册表中查到本类并以
  ``cls(model_config=..., model_client_config=...)`` 实例化。
  Agent 配置（ReActAgentConfig / prompt_template / 工具挂载）零改动。

生产替换为行内真实 LLMOps 网关 Provider 的步骤（同样不改底座）：
1. 新建 Provider 类继承 ``BaseModelClient``，把 ``__client_name__`` 改成网关名
   （如 ``"bank-llmops-gw"``），并在应用启动时 import 该模块完成注册；
2. ``invoke()`` / ``stream()`` 内部用 ``self._build_request_params(...)`` 组装
   OpenAI 兼容请求体，HTTP 调用行内 LLMOps 网关（地址/密钥走
   ``ModelClientConfig.api_base`` / ``api_key``，不入代码），把网关响应映射回
   ``AssistantMessage`` / ``AssistantMessageChunk``；
3. 保留下面的 ``trigger(LLMCallEvents.*)`` 调用——底座观测栈的
   ``chat {model}`` span 由这些回调事件驱动，换掉 HTTP 实现不影响链路采集；
4. Agent 仅需把 ``client_provider`` 换成新名字，其余配置不动。

本 echo 实现返回确定性内容（回显用户输入），用于端到端联调断言；
``ECHO_MOCK_TOOL_CALL=1`` 且请求带 tools 时可返回一个 mock tool_calls，
验证 ReAct 工具调用路径（本桥默认不挂工具，不会触发）。
"""
from __future__ import annotations

import os
from typing import Any, AsyncIterator, Dict, List, Optional, Union

from openjiuwen.core.foundation.llm.model_clients.base_model_client import BaseModelClient
from openjiuwen.core.foundation.llm.output_parsers.output_parser import BaseOutputParser
from openjiuwen.core.foundation.llm.schema.generation_response import (
    AudioGenerationResponse,
    ImageGenerationResponse,
    VideoGenerationResponse,
)
from openjiuwen.core.foundation.llm.schema.message import (
    AssistantMessage,
    BaseMessage,
    UsageMetadata,
    UserMessage,
)
from openjiuwen.core.foundation.llm.schema.message_chunk import AssistantMessageChunk
from openjiuwen.core.foundation.llm.schema.tool_call import ToolCall
from openjiuwen.core.foundation.tool import ToolInfo
from openjiuwen.core.runner.callback import trigger
from openjiuwen.core.runner.callback.events import LLMCallEvents

_STREAM_CHUNK_SIZE = 8  # 流式输出时每帧的字符数，制造可观察的多帧 SSE


class EchoBankLlmopsModelClient(BaseModelClient):
    """确定性 echo 模型客户端（模拟行内 LLMOps 网关 Provider）。

    类定义时 ``__client_name__`` 触发自动注册，之后
    ``client_provider="echo-bank-llmops"`` 即路由到本类。
    """

    __client_name__ = "echo-bank-llmops"
    __client_type__ = "llm"

    def _validate_config(self):
        # echo Provider 不连真实网关，跳过 BaseModelClient 对 api_key/api_base 的强制校验。
        # 真实 LLMOps Provider 通常保留默认校验（api_base 指网关地址，api_key 指网关凭证）。
        return

    # ------------------------------------------------------------------
    # 内部工具
    # ------------------------------------------------------------------
    @staticmethod
    def _last_user_text(messages: Union[str, List[BaseMessage], List[dict]]) -> str:
        """取最后一条用户消息文本，用于回显。"""
        if isinstance(messages, str):
            return messages
        for msg in reversed(messages or []):
            if isinstance(msg, dict):
                role = msg.get("role")
                content = msg.get("content")
            else:
                role = getattr(msg, "role", None)
                content = getattr(msg, "content", None)
            if role == "user" and isinstance(content, str) and content:
                return content
        return ""

    def _echo_text(self, messages: Union[str, List[BaseMessage], List[dict]]) -> str:
        user_text = self._last_user_text(messages)
        model_name = self.model_config.model_name or "echo-model"
        return f"[{model_name}@echo-bank-llmops] 收到并回显: {user_text}"

    @staticmethod
    def _usage(prompt_text: str, completion_text: str) -> UsageMetadata:
        return UsageMetadata(
            model_name="echo-model",
            input_tokens=max(1, len(prompt_text) // 4),
            output_tokens=max(1, len(completion_text) // 4),
            total_tokens=max(2, (len(prompt_text) + len(completion_text)) // 4),
        )

    def _mock_tool_calls(self, tools: Union[List[ToolInfo], List[dict], None]) -> Optional[List[ToolCall]]:
        """可选 mock tool_calls：ECHO_MOCK_TOOL_CALL=1 且请求确实带 tools 时，
        对第一个工具构造一次确定性调用（验证 ReAct 工具路径用）。"""
        if os.environ.get("ECHO_MOCK_TOOL_CALL", "").lower() not in ("1", "true", "yes"):
            return None
        if not tools:
            return None
        first = tools[0]
        if isinstance(first, dict):
            name = first.get("function", {}).get("name") or first.get("name") or "mock_tool"
        else:
            name = getattr(first, "name", "mock_tool")
        return [ToolCall(id="call_echo_mock_0", type="function", name=name, arguments="{}", index=0)]

    async def _trigger_input(self, messages, tools, temperature, top_p, max_tokens, is_stream: bool):
        # 底座观测栈（extensions/observability 的 OtelCallbackHandler）监听这些事件
        # 生成 `chat {model}` span；自定义 Provider 必须触发，否则链路缺 LLM 段。
        await trigger(
            LLMCallEvents.LLM_INPUT,
            model_name=self.model_config.model_name,
            model_provider=self.model_client_config.client_provider,
            messages=messages,
            tools=tools,
            temperature=temperature,
            top_p=top_p,
            max_tokens=max_tokens,
            is_stream=is_stream,
        )

    async def _trigger_output(self, *, is_stream: bool, response: str, usage: UsageMetadata,
                              tool_calls: Optional[List[ToolCall]]):
        await trigger(
            LLMCallEvents.LLM_OUTPUT,
            model_name=self.model_config.model_name,
            model_provider=self.model_client_config.client_provider,
            is_stream=is_stream,
            response=response,
            reasoning_content=None,
            usage=usage,
            tool_calls=tool_calls,
        )

    # ------------------------------------------------------------------
    # BaseModelClient 抽象方法实现
    # ------------------------------------------------------------------
    async def invoke(
            self,
            messages: Union[str, List[BaseMessage], List[dict]],
            *,
            tools: Union[List[ToolInfo], List[dict], None] = None,
            temperature: Optional[float] = None,
            top_p: Optional[float] = None,
            model: str = None,
            max_tokens: Optional[int] = None,
            stop: Union[Optional[str], None] = None,
            output_parser: Optional[BaseOutputParser] = None,
            timeout: float = None,
            **kwargs: Any,
    ) -> AssistantMessage:
        """同步调用：返回完整 echo 响应（生产实现：此处 HTTP 调 LLMOps 网关）。"""
        await self._trigger_input(messages, tools, temperature, top_p, max_tokens, is_stream=False)

        tool_calls = self._mock_tool_calls(tools)
        content = "" if tool_calls else self._echo_text(messages)
        usage = self._usage(self._last_user_text(messages), content)
        message = AssistantMessage(
            content=content,
            tool_calls=tool_calls,
            usage_metadata=usage,
            finish_reason="tool_calls" if tool_calls else "stop",
            response_model=self.model_config.model_name or "echo-model",
        )
        await self._trigger_output(is_stream=False, response=content, usage=usage, tool_calls=tool_calls)
        return message

    async def stream(
            self,
            messages: Union[str, List[BaseMessage], List[dict]],
            *,
            tools: Union[List[ToolInfo], List[dict], None] = None,
            temperature: Optional[float] = None,
            top_p: Optional[float] = None,
            model: str = None,
            max_tokens: Optional[int] = None,
            stop: Union[Optional[str], None] = None,
            output_parser: Optional[BaseOutputParser] = None,
            timeout: float = None,
            **kwargs: Any,
    ) -> AsyncIterator[AssistantMessageChunk]:
        """流式调用：把 echo 文本切分成多个 chunk 逐帧吐出（生产实现：转发网关 SSE）。"""
        await self._trigger_input(messages, tools, temperature, top_p, max_tokens, is_stream=True)

        tool_calls = self._mock_tool_calls(tools)
        content = "" if tool_calls else self._echo_text(messages)
        for offset in range(0, len(content), _STREAM_CHUNK_SIZE):
            yield AssistantMessageChunk(content=content[offset:offset + _STREAM_CHUNK_SIZE])
        if tool_calls:
            yield AssistantMessageChunk(content="", tool_calls=tool_calls)
        usage = self._usage(self._last_user_text(messages), content)
        # 最后一帧携带 finish_reason 与 usage（OpenAI 流式约定），
        # ReActAgent 用 ``+`` 累加 chunk 时据此得到完整响应。
        yield AssistantMessageChunk(
            content="",
            usage_metadata=usage,
            finish_reason="tool_calls" if tool_calls else "stop",
        )
        await self._trigger_output(is_stream=True, response=content, usage=usage, tool_calls=tool_calls)

    # ---- 多模态能力 echo Provider 不支持，显式报“未实现”（真实网关按需实现） ----
    async def generate_image(self, messages: List[UserMessage], **kwargs: Any) -> ImageGenerationResponse:
        raise NotImplementedError("echo-bank-llmops 不支持图像生成")

    async def generate_speech(self, messages: List[UserMessage], **kwargs: Any) -> AudioGenerationResponse:
        raise NotImplementedError("echo-bank-llmops 不支持语音合成")

    async def generate_video(self, messages: List[UserMessage], **kwargs: Any) -> VideoGenerationResponse:
        raise NotImplementedError("echo-bank-llmops 不支持视频生成")
