# 模块 07：Agent 服务发布与访问治理（governance）

## 摘要
Agent 服务的统一访问入口与治理：服务目录、按 Agent/环境/版本的路由（代理到 06 的部署实例）、调用方鉴权与审计、
Agent 访问 MCP 的运行时权限校验（PEP）、HiAgent 调用适配、Agent 间调用治理。

## 对应需求（能力清单 07）
- Agent 发布后通过统一访问入口提供标准 HTTP + SSE 接口；按 Agent、环境和版本路由至目标运行服务；
  部署完成后生成/同步路由配置；统一管理服务标识、访问地址、认证方式、版本、路由配置和访问记录。
- Agent 调用 MCP 统一经过访问控制入口；按 Tool 校验运行时调用权限。
- HiAgent 通过标准接口调用已发布高码 Agent（不直接访问底层运行服务），映射同步/流式/异常。
- 已登记 Agent 之间的服务发现、身份认证、权限校验、限流/超时/熔断。

## 领域模型
- `ServiceRoute`：id、agent_id、env、agent_version、deployment_id、route_revision（递增）、status（ACTIVE/DRAINED）。
  由 06 部署成功后生成/更新（本期由 governance 提供 SPI 供 delivery 调用，或定时同步；实现时选其一并记录）。
- `CallerPolicy`：caller_type（USER/HIAGENT/AGENT）、caller_id（用户/系统/Agent code）、agent_id、env、
  rate_limit_per_min、timeout_ms、status。调用方访问 Agent 的授权与流控策略。
- `McpInvokePolicy`：agent_id、asset_id（MCP_TOOL）、env、allowed（true/false）。Agent → Tool 的运行时权限。
- `InvocationRecord`：trace_id、caller_type、caller_id、agent_id、env、route_id、status、latency_ms、error、created_at
  （访问记录，08 模块观测查询的数据源）。

## 关键接口
### 管理面（`/v1/api/...`）
- `/routes/sync|list|detail`（sync：从 delivery 的 RUNNING 部署生成/更新路由；版本切换时旧路由 DRAINED）
- `/caller-policies/grant|revoke|list`
- `/mcp-policies/grant|revoke|list`
- `/service-directory/list`（对调用方暴露的服务目录：标识/环境/版本/地址/认证方式）

### 运行时面（网关入口）
- `POST /v1/invoke/{agentCode}/{env}`：统一调用入口。流程：认证（Bearer）→ CallerPolicy 校验（身份、限流、超时配置）→
  路由解析（agentCode+env+当前 ACTIVE 路由，version 可经 header `X-Agent-Version` 指定）→ 转发到 deployment.instance_url
  （支持 SSE 透传：Content-Type text/event-stream 时流式转发）→ 落 InvocationRecord。异常映射为统一错误结构。
- `POST /v1/mcp-check`：MCP 调用鉴权 PEP 接口（agent 运行时或未来 MCP 网关调用）：入参 agentId/toolAssetId/env →
  allowed + reason。另提供 `/v1/agent-auth/check`：Agent 间调用鉴权（callerAgentCode → calleeAgentCode 是否有 CallerPolicy）。

## 权限点（管理面）
`gov:read / gov:route / gov:policy`
角色映射：OWNER/ADMIN 全量；DEVELOPER read；OPERATOR read+route。

## 设计决策
- 运行时面（/v1/invoke、/v1/mcp-check、/v1/agent-auth）不走管理面权限注解，走 CallerPolicy 机器身份；两个面分开认证。
- 发布成功 ≠ 立即开流量：路由 ACTIVE 由 routes/sync 显式产生，生产环境按行内流程在审批通过后同步（本期 sync 为手动触发接口）。
- 限流用内存滑动窗口实现（单实例语义，明确标注；多实例需换 Redis，列入待办）。
- HiAgent 适配：HiAgent 作为 caller_type=HIAGENT 走同一个 /v1/invoke，异常映射与 SSE 透传即满足其交互要求；
  不重建 HiAgent 任何能力。
- SSE 透传：用 RestTemplate/WebClient 流式消费上游并写回响应；超时按 CallerPolicy.timeout_ms 中断。

## 状态
首版完成（2026-09-27），累计 75/75 测试通过（本模块 13 个）。实现位置：`com.bosc.agentops.governance`（7 Controller / 9 Service / 4 表，V7__governance.sql）。

## 实现要点与偏差
- 运行时面（/v1/invoke、/v1/mcp-check、/v1/agent-auth）与管理面认证天然分离：AuthFilter 只拦 /v1/api/**。运行时面机器身份 = X-Caller-Id/X-Caller-Type 头 + CallerPolicy + 可选共享密钥（policy.sharedToken 非空才强制 Bearer）。
- 运行时面返回真实 HTTP 状态码 + 统一错误结构（HiAgent 等机器调用方依赖标准状态码）；管理面保持 200+业务码。RuntimePlaneExceptionHandler 需 @Order(HIGHEST_PRECEDENCE)，否则被全局兜底抢先（调试中修掉的实际问题）。
- SSE 与普通 JSON 共用一条流式转发路径（RestClient 逐块 flush）；超时按 CallerPolicy.timeout_ms。
- 限流为内存滑动窗口（按 policy 维度，单实例语义；多实例换 Redis 列入主文档待办）。
- routes/sync 幂等（deployment_id 唯一约束支撑），且额外校验请求版本与 RUNNING 部署所属 Release 版本一致（防路由指错版本）。
- 调用各阶段落 InvocationRecord（traceId 经 X-Trace-Id 回传），供 08 观测查询。
- 已知告警：PermissionRegistrationAuditor 会对运行时面 3 个 Controller 打「未注册权限点」warn——两个面认证分离的预期表现，后续可在 auditor 中配置排除前缀。

## 联调实证（2026-09-27，agent-bridge 真实联调）
- UpstreamForwarder 原样 POST 到 Deployment.instanceUrl 根路径（不追加路径）——接入方服务需在根路径挂处理（agent-bridge 已在 `POST /` 兼容）。
- InvocationRecord 的 traceId（UUID）与 OTel hex traceId 双轨未关联；精确串联需把 X-Trace-Id 以 traceparent 透传上游（已列主文档待办）。
- InvocationRecord 目前无管理面查询接口（冒烟脚本只能断言到 HTTP 层），已列待办。
