# 模块 08：运行观测与运维保障（observability）

## 摘要
调用链路与审计的查询面（权限 + 脱敏）、OTel trace 接收与按链路查询、告警接入与处置动作、平台自身组件运维视图。

## 对应需求（能力清单 08）
- 记录 Agent、模型、Skill 和 MCP 的主要调用链路、异常及关键操作；支持定位异常调用节点并按权限查看输入输出和错误。
- 对关键外部调用和高风险操作保留审计记录；优先对接行内已有日志和监控能力，不重复建设底层监控平台。
- 平台核心组件清单及依赖关系、状态检查、异常告警；平台版本升级/配置变更/回退（本期提供组件登记与健康聚合）。
- 对接行内告警平台；逐步支持限流、暂停调用和下线等处置方式。

## 领域模型
- `TraceSpan`：id、trace_id、span_id、parent_span_id、agent_id、env、span_name、span_kind（LLM/TOOL/AGENT/WORKFLOW/OTHER）、
  start_time、end_time、status、attrs JSON（脱敏后）。来源：07 的 InvocationRecord 生成根 span；OTLP 接收端点接收底座推送。
- `AlertEvent`：id、source（行内告警平台标识）、alert_key、agent_id、level、title、detail JSON、status（FIRING/HANDLED/CLOSED）、created_at。
- `ComponentRegistry`：code、name、type（DATABASE/MQ/CACHE/SERVICE/EXTERNAL）、owner_team、health_endpoint、critical、description。
  平台核心组件清单 + 依赖关系（deps JSON 存依赖 code 列表）。
- 审计查询直接复用 foundation 的 audit_event 表，不另建。

## 关键接口（REST，`/v1/api/...`）
- 观测查询：`/observability/traces/query`（按 traceId/agentId/时间窗）、`/observability/traces/{traceId}`（span 列表还原调用链）、
  `/observability/audit/query`（按模块/操作人/资源/时间窗）。**全部按项目权限过滤 + 字段脱敏**（attrs/detail 中的敏感键
  password|secret|token|apiKey 等值替换为 ***，键名不区分大小写前缀匹配）。
- OTLP 接收：`POST /v1/otlp/v1/traces`（OTLP/HTTP JSON，解析 resourceSpans→spans 落库；agent 映射规则：resource attribute
  `service.name` 或 `agent.id`；无法映射的 span 丢弃并计数）。该端点不走管理面权限注解（机器通道），用配置 token 校验。
- 告警：`/alerts/webhook`（行内告警平台推送入口，机器通道 token 校验）、`/alerts/list|detail|handle`
  （handle=登记处置结果与说明）。
- 处置动作：`/actions/disable-route`（调 07 的路由置 DRAINED，停流量）、`/actions/revoke-caller`（吊销 CallerPolicy，暂停调用）。
  处置动作全部落审计且要求 `obs:action` 权限。
- 组件运维：`/components/register|update|list|health`（health 聚合：逐个探 health_endpoint 得 UP/DOWN/UNKNOWN 汇总返回）。

## 权限点
`obs:read / obs:alert / obs:action / obs:component`
角色映射：OWNER/ADMIN 全量；DEVELOPER read；OPERATOR read+alert+action+component。

## 设计决策
- 观测面只做「查询 + 轻量接收 + 处置」，底层监控/日志存储在行内既有系统；TraceSpan 表是查询加速与评测留痕的最小副本，
  不追求完整链路存储。
- 脱敏在写入与查询两层都做（写入时脱敏为主，查询时再兜底）。
- 处置动作不直接操作底座，只调本系统 07 模块的路由/策略接口——治理动作的唯一入口在 governance。

## 状态
首版完成（2026-09-27），累计 85/85 测试通过（本模块 10 个）。实现位置：`com.bosc.agentops.observability`（7 Controller / 7 Service / 3 表，V8__observability.sql）。

## 实现要点与偏差
- 机器通道（/v1/otlp/v1/traces、/v1/alerts/webhook）用 MachineTokenGuard（X-Platform-Token，常量时间比较；未配置 token 则 503 拒绝服务）。
- 为此给 foundation 的 PermissionRegistrationAuditor 增加了 `agentops.permission.audit-exclude-prefixes` 配置（命中前缀跳过告警）；
  已配置 `/v1/otlp,/v1/alerts/webhook,/v1/invoke,/v1/mcp-check,/v1/agent-auth`（含 07 运行时面，启动告警已消除）。
- 跨模块改动：governance 的 RouteService 新增公开 `drain` 方法（按 id 置 DRAINED，幂等，落 governance 侧审计）；revoke-caller 直接复用 CallerPolicyService.revoke。
- 查询权限以 obs:read 单点实现（V8 种子使四角色 obs:read 与 project:read 授予集一致，效果等同 OR；未来若有仅 project:read 的自定义角色需扩展切面）。
- 组件接口带 projectId 作权限 scope（让 obs:component 真正生效）；组件数据本身平台全局。
- span 时间以 Unix 纳秒原值存 BIGINT；traces 时间窗过滤落在 created_at。
- 脱敏两层：写入 TraceSpan 时脱敏 attrs；audit/query 返回时对 detail 再兜底（MaskingUtil，敏感键不区分大小写递归处理）。

## 统计聚合（2026-09-28 增补）
- `POST /v1/api/observability/stats/overview` {projectId?}：近 7 天 totalCalls/successRate/avgLatencyMs、dailyTrend、topAgents(top5)、spanKindDist、alertOpenCount；
  projectId 为空为平台级聚合（认证即可），非空时 service 层强制 obs:read 项目校验（非成员 40301）。
- dailyTrend.failed = total − success（含 FAILED+REJECTED），success+failed=total 可对账。

## 联调实证（2026-09-27，agent-bridge 真实联调）
- /v1/otlp/v1/traces 被真实 openJiuwen 观测栈（手写 JsonOtlpHttpExporter 经 init_observability 注入）验证可用：span 落库、归属映射（service.name→agent code）、traces/query 链路还原均通过。
- 注意：Python 官方 OTLP HTTP exporter 发 protobuf，平台只解析 JSON——接入方必须用 JSON 序列化 exporter（agent-bridge/otlp_json_exporter.py 可复用）。
- 底座侧须由宿主建 Agent 根 span，否则观测栈不导出任何 span（详见 modules/agent-bridge.md 关键经验）。
