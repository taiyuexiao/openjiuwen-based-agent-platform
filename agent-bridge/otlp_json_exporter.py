# coding: utf-8
"""OTLP/HTTP JSON Span 导出器（手写序列化，非 protobuf）。

为什么需要它：opentelemetry-python 官方的 ``OTLPSpanExporter``（HTTP）默认把
span 编成 **protobuf** 字节流（Content-Type: application/x-protobuf），而
agentops-platform 的 ``POST /v1/otlp/v1/traces`` 只解析 **OTLP/HTTP JSON**
（resourceSpans → scopeSpans → spans，camelCase，KeyValue 数组）。
因此这里继承 ``SpanExporter`` 自己把 ``ReadableSpan`` 序列化成 OTLP JSON。

产物的字段形状对齐 OTLP JSON Mapping（与平台 TraceIngestService 的解析一一对应）：
- traceId / spanId / parentSpanId：小写 hex 字符串；
- startTimeUnixNano / endTimeUnixNano：字符串（int64 的 JSON 规范形态，平台两种都收）；
- attributes：[{"key": k, "value": {"stringValue"|"intValue"|"doubleValue"|"boolValue"|"arrayValue": v}}]；
- status.code：0/1/2 整数（UNSET/OK/ERROR）。

resource attributes 由 SDK 侧提供（service.name 来自 init_observability 的
ObservabilityConfig.service_name）；本导出器在序列化时合并 ``extra_resource_attrs``
（如 deployment.environment=PROD）——底座的 ObservabilityRuntime 只接受
service_name，环境标签在这里补上最稳妥。
"""
from __future__ import annotations

import json
import sys
import threading
from typing import Any, Dict, Mapping, Optional, Sequence, Tuple

import requests
from opentelemetry.sdk.trace import ReadableSpan
from opentelemetry.sdk.trace.export import SpanExporter, SpanExportResult


class JsonOtlpHttpExporter(SpanExporter):
    """把 ReadableSpan 以 OTLP/HTTP JSON POST 到 agentops-platform。"""

    def __init__(
            self,
            endpoint: str,
            token: str,
            *,
            extra_resource_attrs: Optional[Mapping[str, Any]] = None,
            timeout_seconds: float = 5.0,
    ) -> None:
        self._endpoint = endpoint
        self._token = token
        self._extra_resource_attrs = dict(extra_resource_attrs or {})
        self._timeout = timeout_seconds
        self._session = requests.Session()
        self._lock = threading.Lock()
        self._shutdown = False

    # ------------------------------------------------------------------
    # SpanExporter 接口
    # ------------------------------------------------------------------
    def export(self, spans: Sequence[ReadableSpan]) -> SpanExportResult:
        if self._shutdown:
            return SpanExportResult.FAILURE
        if not spans:
            return SpanExportResult.SUCCESS
        try:
            payload = self._encode_request(spans)
        except Exception as exc:  # 序列化失败不能拖垮业务线程
            print(f"[otlp-json] encode failed: {exc!r}", file=sys.stderr, flush=True)
            return SpanExportResult.FAILURE
        try:
            resp = self._session.post(
                self._endpoint,
                data=payload,
                headers={
                    "Content-Type": "application/json",
                    "X-Platform-Token": self._token,
                },
                timeout=self._timeout,
            )
            ok = 200 <= resp.status_code < 300
            print(
                f"[otlp-json] exported {len(spans)} span(s) -> {resp.status_code} {resp.text[:200]}",
                file=sys.stderr,
                flush=True,
            )
            return SpanExportResult.SUCCESS if ok else SpanExportResult.FAILURE
        except Exception as exc:
            print(f"[otlp-json] POST {self._endpoint} failed: {exc!r}", file=sys.stderr, flush=True)
            return SpanExportResult.FAILURE

    def shutdown(self) -> None:
        with self._lock:
            self._shutdown = True
        self._session.close()

    def force_flush(self, timeout_millis: int = 30000) -> bool:
        return True

    # ------------------------------------------------------------------
    # ReadableSpan → OTLP JSON 序列化
    # ------------------------------------------------------------------
    def _encode_request(self, spans: Sequence[ReadableSpan]) -> bytes:
        # 按 (resource, instrumentation scope) 分组，一个 payload 可装多 span
        groups: Dict[Tuple[int, int], Dict[str, Any]] = {}
        order: list = []
        for span in spans:
            resource_attrs = dict(span.resource.attributes)
            resource_attrs.update(self._extra_resource_attrs)
            scope = span.instrumentation_scope
            key = (id(span.resource), id(scope))
            group = groups.get(key)
            if group is None:
                group = {
                    "resource": {"attributes": self._encode_attrs(resource_attrs)},
                    "scope": {
                        "name": scope.name if scope else "",
                        "version": scope.version if scope else "",
                    },
                    "spans": [],
                }
                groups[key] = group
                order.append(group)
            group["spans"].append(self._encode_span(span))

        request = {
            "resourceSpans": [
                {
                    "resource": g["resource"],
                    "scopeSpans": [{"scope": g["scope"], "spans": g["spans"]}],
                }
                for g in order
            ]
        }
        return json.dumps(request, ensure_ascii=False).encode("utf-8")

    def _encode_span(self, span: ReadableSpan) -> Dict[str, Any]:
        ctx = span.context
        node: Dict[str, Any] = {
            "traceId": f"{ctx.trace_id:032x}",
            "spanId": f"{ctx.span_id:016x}",
            "name": span.name,
            "kind": span.kind.value if span.kind else 1,
            "startTimeUnixNano": str(span.start_time or 0),
            "endTimeUnixNano": str(span.end_time or 0),
            "attributes": self._encode_attrs(span.attributes or {}),
            "status": self._encode_status(span),
        }
        if span.parent is not None:
            node["parentSpanId"] = f"{span.parent.span_id:016x}"
        if span.events:
            node["events"] = [
                {
                    "timeUnixNano": str(event.timestamp or 0),
                    "name": event.name,
                    "attributes": self._encode_attrs(event.attributes or {}),
                }
                for event in span.events
            ]
        return node

    @staticmethod
    def _encode_status(span: ReadableSpan) -> Dict[str, Any]:
        status = span.status
        node: Dict[str, Any] = {"code": status.status_code.value if status else 0}
        if status and status.description:
            node["message"] = status.description
        return node

    @classmethod
    def _encode_attrs(cls, attrs: Mapping[str, Any]) -> list:
        return [{"key": str(k), "value": cls._encode_value(v)} for k, v in attrs.items()]

    @classmethod
    def _encode_value(cls, value: Any) -> Dict[str, Any]:
        if isinstance(value, bool):
            return {"boolValue": value}
        if isinstance(value, int):
            # OTLP JSON 规范：64 位整型用字符串承载（平台解析器两种形态都兼容）
            return {"intValue": str(value)}
        if isinstance(value, float):
            return {"doubleValue": value}
        if isinstance(value, (list, tuple)):
            return {"arrayValue": {"values": [cls._encode_value(v) for v in value]}}
        if isinstance(value, bytes):
            return {"stringValue": value.hex()}
        return {"stringValue": str(value)}
