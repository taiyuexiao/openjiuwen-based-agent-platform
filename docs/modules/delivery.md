# 模块 06：制品交付与托管运行（delivery）

## 摘要
把「开发好的 AgentVersion」变成「受控、可追溯的生产交付物」：制品登记、部署目标管理、发布流程（含门禁）、
部署执行（经执行器 SPI 对接 openJiuwen agent-runtime / 容器平台）、回滚。

## 对应需求（能力清单 06）
- 代码仓库、制品仓库和投产流程的必要集成；可追溯制品关联代码、配置和版本。
- 研发与生产环境隔离，生产仅接收经验证的固化制品和已关联的评测结果。
- 与行内投产/审批/ITSM 流程衔接：投产信息准备、制品校验、状态同步、部署执行和回滚留痕。
- 部署目标 = 环境 + K8s 集群 + 命名空间 + 基础资源配置 + 可部署 Agent 等级；项目可配置默认/可选目标；
  发布时选择实际目标；目标切换受权限和投产流程控制并留痕。
- Agent 分级结论由外部评审形成，平台记录、校验并应用（限制可选部署目标、映射副本数/健康检查/资源限制/发布策略/故障切换）。

## 领域模型
- `Artifact`：id、agent_id、agent_version、code_commit、image_digest（或 jar 坐标）、config_digest、
  evaluation_ref（评测报告关联，占位字段，评测平台对接后填充）、built_at、created_by。制品不可变。
- `DeployTarget`：id、code、env（DEV/SIT/UAT/PROD）、cluster、namespace、base_resource JSON、
  allowed_agent_levels JSON（如 ["P1","P2"]）、status。
- `ProjectDeployTarget`：project_id、target_id、is_default（项目可选/默认部署目标）。
- `AgentLevel`：agent_id、level（P0/P1/P2/P3）、source（评审来源说明）、confirmed_by、confirmed_at、effective —— 分级结论记录。
- `Release`：id、agent_id、agent_version、artifact_id、target_id、level_snapshot、status
  （DRAFT → GATED → APPROVED → DEPLOYING → RUNNING；失败 FAILED；回滚 ROLLED_BACK）、
  gate_result JSON、approval_ref（行内审批单号，占位）、created_by、timestamps。
- `Deployment`：id、release_id、target_id、executor（SUBPROCESS/DOCKER/K8S/HOSTED_EXTERNAL）、
  instance_url、replicas、status（PENDING/RUNNING/FAILED/STOPPED）、health_status、started_at。

## 发布门禁（ReleaseService.gate，进入 APPROVED 前必须全过）
1. 归属门禁：AppBindingGate.validateForRelease(agentId) 通过（09 模块 SPI）。
2. 制品完整：code_commit、image_digest、config_digest 非空；evaluation_ref 非空（本期仅要求存在关联标识，
   评测平台对接后再校验其状态）。
3. 等级有效：存在 effective 的 AgentLevel 记录。
4. 目标合法：所选 target 属于项目的可选目标集合 且 target.env=PROD 时 level 必须在 target.allowed_agent_levels 内。
失败聚合成 gate_result JSON 落库，Release 停留 GATED 状态（可修复后重新 gate）。

## 执行器 SPI
- `DeploymentExecutor`：`deploy(release, target) -> DeploymentResult`、`stop(deploymentId)`、`healthCheck(deploymentId)`。
- 本期实现：`LocalStubExecutor`（默认，生成伪 instance_url、模拟健康检查通过，用于全流程贯通）；
  `OpenJiuwenRuntimeExecutor` 的客户端骨架（HTTP 调 agent-runtime 的 /api/v1/agents/deploy，非生产验证前标注未启用）。

## 关键接口（REST，`/v1/api/...`）
- `/artifacts/register|detail|list`；`/deploy-targets/create|update|disable|list`；`/projects/{pid}/deploy-targets/attach|detach|set-default|list`
- `/agent-levels/confirm|disable|list`
- `/releases/create|gate|approve|deploy|rollback|detail|list`（approve 记录 approval_ref；deploy 触发执行器；rollback 生成指向上一 RUNNING 组合的新 Release 并执行）
- `/deployments/detail|list|stop`

## 权限点
`delivery:read / delivery:artifact / delivery:target / delivery:release / delivery:approve / delivery:deploy`
角色映射：OWNER 全量；ADMIN 除 approve 外全量；DEVELOPER read+artifact+release；OPERATOR read+deploy（运维执行）。
approve 单独成点，与 release 分离（拟办分离）。

## 设计决策
- 发布状态机唯一事实源在平台（Release 表），执行器只执行动作；避免与底座出现两个状态源。
- 回滚 = 新 Release 指向旧（artifact+config+target）组合重新走 gate+deploy，全程留痕，不做原地修改。
- 部署目标切换 = 新 Release 选新 target；旧 Deployment 停掉。切换记录天然落在 Release/Deployment 历史中。

## 状态
首版完成（2026-09-27），累计 62/62 测试通过（本模块 15 个）。实现位置：`com.bosc.agentops.delivery`（6 Controller / 5 Service / DeploymentExecutor SPI 双实现，V6__delivery.sql）。

## 实现要点与偏差
- 六张表统一 `delivery_` 前缀（防 release/deployment 多方言歧义）；等级列名 `level_value`（关键字规避），API 字段仍叫 level。
- 状态机 DRAFT→GATED→APPROVED→DEPLOYING→RUNNING/FAILED/ROLLED_BACK，非法迁移 40902；gate 只检查不放行，结果聚合落 gate_result JSON。
- 拟办分离落实：approve 需 GATED + gate 全过 + approvalRef 非空；种子中 ADMIN 无 approve 权限。
- 回滚 = 新 Release 快照旧组合（rollback_of 留痕）重走 gate→approve→deploy；新部署 RUNNING 后同 Agent 旧 RUNNING 部署自动 stop（目标切换留痕）。
- HOSTED 部署不走执行器（executor=HOSTED_EXTERNAL、health=UNKNOWN），Release 仍置 RUNNING——否则回滚前置对 HOSTED 永不可达。
- 非 HOSTED 的 Artifact 登记要求对应 AgentVersion 已存在（可追溯约束增强）。
- agent-levels 的 confirm/disable 归 delivery:target 权限点（level 未单设权限点，属部署治理配置面）。
- 执行器由 `agentops.delivery.executor=local|openjiuwen` 路由；openjiuwen 未配 runtime-base-url 时构造即 fail-fast。
- 未做（本期不做）：evaluation_ref 的状态校验（评测平台对接后补）、执行器异步健康检查轮询。
