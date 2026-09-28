# 模块 09：企业集成与行内规范（enterprise-integration）

## 摘要
Agent 与行内权威应用资产（CMDB）的归属绑定、权威信息同步与快照、投产前归属校验门禁（供 06 模块调用）；
ITSM 集成的连接器骨架。

## 对应需求（能力清单 09）
- 新建 Agent 时通过应用唯一编号从行内应用资产/CMDB 选择主归属应用。
- 自动同步应用名称、Owner、业务域和应用分级等权威信息，不以平台手工配置替代。
- 保存主归属应用、来源系统、同步时间和应用分级快照。
- 无有效归属应用或归属信息未完成校验的 Agent，不得进入生产投产流程（门禁 SPI 供 06 调用）。
- 归属应用绑定稳定后逐步扩展 ITSM 集成；本期只做连接器骨架与变更记录表。

## 领域模型
- `AppBinding`：agent_id（唯一）、app_code（行内应用唯一编号）、source_system、sync_status（SYNCED/STALE/FAILED/UNVERIFIED）、
  synced_at、snapshot JSON（应用名称/Owner/业务域/应用分级，同步时刻快照）、bound_by、created_at。
- `CmdbConnector` SPI：`fetchApp(appCode) -> CmdbApp`（名称/owner/业务域/分级/状态）。本期实现 `MockCmdbConnector`
  （配置内置若干应用），生产替换为行内 CMDB HTTP 连接器。
- `ItsmChangeRecord`：change_no、agent_id、change_type、payload JSON、status、created_at（本期仅建表与登记接口，流程对接后期）。

## 权限点
`binding:read / binding:manage`（绑定/重同步/解绑）；角色映射：OWNER/ADMIN manage，DEVELOPER/OPERATOR read。

## 关键接口（REST，`/v1/api/...`）
- `/bindings/bind`（agentId + appCode → 调 CMDB 拉权威信息落快照，sync_status=SYNCED）
- `/bindings/resync`（重新同步刷新快照与 synced_at）
- `/bindings/unbind`、`/bindings/detail|list`
- `/itsm/changes/record|list`（登记占位）

## SPI（供其他模块）
- `AppBindingGate`：`GateResult validateForRelease(agentId)` —— 存在绑定 且 sync_status=SYNCED 且快照含分级 且应用状态正常
  → pass；否则 fail 并给出全部原因。06 的投产检查必须调用它。
- 定时重同步（可选）：@Scheduled 扫描 STALE/FAILED 重试同步，本期提供开关配置，默认关闭。

## 设计决策
- 权威信息只从 CMDB 来，平台不提供手工编辑快照的接口；CMDB 不可用时绑定操作失败（fail-closed），不允许绕过。
- 快照与同步时间必须保存：投产校验用快照说话，避免投产时刻 CMDB 抖动影响发布，但 STALE（超过阈值未重同步）视为无效。
- ITSM 本期刻意只做登记：真实审批流衔接属 06 的投产流程，避免两个模块重复建状态机。

## 状态
首版完成（2026-09-27），累计 47/47 测试通过（本模块 11 个）。实现位置：`com.bosc.agentops.enterprise`（2 Controller / CmdbConnector SPI + Mock 实现 / AppBindingGate SPI，V5__enterprise_integration.sql）。

## 实现要点与偏差
- STALE 语义双来源：库内 sync_status 仅在 resync 发现应用 DISABLED 时写 STALE；时间维度 STALE 不改库，在 detail/list 响应（effectiveSyncStatus）与门禁中按 synced_at + stale-threshold-hours（默认 24h）动态计算。
- resync 失败路径返回 code=0 + warning（操作成功记录了同步结果；用 warning 区分「平台故障」与「权威源异常」），CMDB 查无置 FAILED、应用 DISABLED 置 STALE，旧快照保留。
- MockCmdbConnector 暴露 putApp/removeApp 供测试模拟 CMDB 变更；生产换 HTTP 连接器后应移除。
- 定时重同步本期未做，`agentops.cmdb.resync-job-enabled` 配置项已预留（默认关）。
