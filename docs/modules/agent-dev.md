# 模块 02：智能体开发与调试（agent-dev）

## 摘要
高码 Agent 的平台侧登记与生命周期：Agent 实体与版本、声明清单（模型/提示词/Skill/MCP/知识库依赖）的登记与授权校验、
三类历史接入方式（原生/适配/托管）的纳管边界、脚手架模板。

## 对应需求（能力清单 02）
- 首期提供 Python SDK（由 openJiuwen agent-core + 行内薄封装承担，本平台负责登记其声明）。
- Agent 配置对象统一声明模型、提示词、Skill、MCP、记忆等核心配置，平台能够识别并管理这些声明信息。
- 支持在代码中引用已发布的 Skill 和 MCP 工具，无需自行处理连接、认证和调用协议。
- 提供项目脚手架和参考样例；支持本地/研发环境调试；支持查看调用过程（观测由 08 承担）。
- 历史项目：原生接入/适配接入/运行托管三类边界明确；无法适配且无法部署的保留原运行方式不纳管。

## 领域模型
- `Agent`：id、code、name、project_id、access_mode（NATIVE/ADAPTED/HOSTED）、description、status（ACTIVE/ARCHIVED）。
- `AgentVersion`：agent_id、version（x.y.z）、declaration JSON（声明清单，见下）、status（DRAFT/REGISTERED）、registered_by/at。
  版本不可变：REGISTERED 后 declaration 不再改。
- declaration 结构（SDK 构建/注册时上报）：
  `{model: {modelCode, params}, prompt: {templateName/content摘要}, skills: [{assetId, version}], mcpTools: [{assetId, version}], knowledgeBases: [{kbId}], memory: {type, config摘要}}`
- `ScaffoldTemplate`：code、name、language、description、files JSON（路径→内容模板）。本期静态预置两个 Python 模板
  （react-agent-basic、workflow-basic，内容引用 openJiuwen 用法）。

## 权限点
`agent:read / agent:create / agent:update / agent:register`（登记版本）`/ agent:archive`
角色映射：OWNER/ADMIN 全量；DEVELOPER read+create+update+register；OPERATOR read。

## 关键接口（REST，`/v1/api/...`）
- `/agents/create|update|archive|detail|list`
- `/agents/{id}/versions/register|detail|list` —— register 时执行「声明登记校验」（见下）
- `/scaffolds/list|detail`

## 声明登记校验（register 的核心逻辑）
登记时逐项校验声明依赖的授权有效性，任一不通过则拒绝登记并返回全部失败项：
1. model：经 ModelAccessService.checkModelAllowed(projectId, modelCode)，且声明参数在项目 param_policy 允许范围内
   （复用 03 的合并逻辑，出现 removedKeys 非空即拒绝）。
2. skills / mcpTools：经 AssetAccessService.checkUsable(assetId, version, projectId)。
3. knowledgeBases：经 KnowledgeAccessService.checkKbAllowed(projectId, agentId, kbId)。
4. 校验通过后，把每项依赖写入 AssetReference / KnowledgeBaseRef（幂等），完成「平台识别并管理声明」。

## 设计决策
- 三类接入边界：NATIVE=完整声明校验+轨迹观测；ADAPTED=声明校验保留但允许声明为空（登记时标注能力降级）；
  HOSTED=不登记声明，只记录运行入口与健康检查地址，平台仅承诺服务调用/生命周期/基础日志，文档与 API 响应中明确标注。
- 平台不实现 Agent 执行：调试与运行由 openJiuwen 底座承担；06 模块的制品与发布以 AgentVersion 为输入。
- 脚手架只提供模板元数据与文件内容，不做远程代码生成服务。

## 状态
首版完成（2026-09-27）；2026-09-28 增补 Agent 广场与可见性（累计 106/106 测试通过）。实现位置：`com.bosc.agentops.agentdev`（V4 + V9__square_and_admin.sql）。

## 广场与可见性（2026-09-28 增补）
- agent 表加 `visibility`（PROJECT/PUBLIC，默认 PROJECT）；`POST /v1/api/agents/publish|unpublish`（权限 agent:update）。
- `POST /v1/api/agent-square/list`（平台级，认证即可）：仅返回 PUBLIC + ACTIVE 的 Agent，含 projectName/latestVersion/versionCount/accessMode，支持关键词过滤。

## 实现要点与偏差
- 声明登记校验：收集全部失败项一次性返回 40910（message 含每项原因）；通过后写引用记录（AssetReference 幂等 add；KBRef 先查重再 bind）。
- 为此给 ModelAccessService 新增了 `removedParamKeys(projectId, modelCode, declaredParams)`（原 SPI 不暴露 param_policy；内部复用 03 的 mergeParams）。这是 02 对既有模块的唯一改动。
- HOSTED 的 runtime_endpoint/health_endpoint 存在 agent 表（托管模式不登记版本，入口是 Agent 级属性）；create 缺端点 40001；register 直接 40902。
- ADAPTED 空声明登记成功且 capabilityDegraded=true（列存 agent_version，detail 返回体现）。
- 脚手架用启动初始化（ApplicationRunner 幂等写入）而非 SQL 迁移——多行 Python 放 Java 文本块可维护性远优于 SQL 转义；模板内容取自 agent-core 真实示例。
- NATIVE「声明必填」实现为 declaration 非空对象；model 段出现才校验，不强制必填。
