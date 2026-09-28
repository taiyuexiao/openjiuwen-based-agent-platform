package com.bosc.agentops.observability.entity;

/**
 * span 类型：LLM / TOOL / AGENT / WORKFLOW / OTHER。
 * OTLP 接收时由 span attribute agentops.span.kind 显式指定；未指定时根 span 记 AGENT，其余记 OTHER。
 */
public enum SpanKind {
    LLM,
    TOOL,
    AGENT,
    WORKFLOW,
    OTHER
}
