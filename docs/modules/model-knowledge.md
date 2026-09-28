# 模块 03：模型与知识能力接入（model-knowledge）

## 摘要
平台侧管理「哪些模型/知识库可用、哪个项目被授权用哪个、用什么参数」；执行侧由 openJiuwen agent-core 的
自定义 Model Provider / Retriever 插件按平台下发的配置运行。令牌、配额、调用数据留在行内 LLMOps，平台不复制。

## 对应需求（能力清单 03）
- 对接行内统一 LLMOps 和模型网关；应用按项目配置已获授权的模型和调用参数；令牌/配额/调用数据由 LLMOps 统一管理。
- 对接行内既有知识库；Agent 按授权范围引用知识库；保留知识库、Agent 和调用权限之间的关联关系。

## 领域模型
- `ModelProvider`（平台级目录）：code、name、type（OPENAI_COMPAT/CUSTOM）、endpoint、auth_type（CREDENTIAL_REF/GATEWAY_PASSTHROUGH）、credential_ref（密钥引用，不存明文）、status。
- `ModelService`：provider_id、model_code（如 qwen-max）、display_name、capabilities JSON（chat/embedding/rerank）、default_params JSON、status。
- `ProjectModelGrant`：project_id、model_service_id、param_policy JSON（允许覆盖的参数白名单/上下限）、granted_by。→ 「按项目配置已获授权模型」的落点。
- `KnowledgeBase`：code、name、type（行内系统标识）、endpoint/连接配置 JSON、owner、status。
- `KnowledgeBaseGrant`：kb_id、project_id、scope JSON（授权范围：集合/标签过滤等）、granted_by。
- `KnowledgeBaseRef`：kb_id、project_id、agent_id（可空，空=项目级）、引用版本、created_by。→ 「保留知识库-Agent-调用权限关联关系」的落点（Agent 实体由 02 模块提供，本期 agent_id 先做弱引用字段）。

## 权限点
`model:read / model:manage / model:grant`（manage=平台级运营，grant=项目内授权配置）
`kb:read / kb:manage / kb:grant`
角色映射：OWNER/ADMIN 全量；DEVELOPER read + grant；OPERATOR read。

## 关键接口（REST，`/v1/api/...`）
- 模型目录：`/model-providers/create|update|disable|list`、`/model-services/create|update|disable|list`
- 项目授权：`/projects/{projectId}/model-grants/grant|revoke|list`；`effective-models`（项目当前可用模型+参数策略，供 SDK/底座拉取）
- 知识库：`/knowledge-bases/create|update|disable|list`、`/projects/{projectId}/kb-grants/grant|revoke|list`、`/kb-refs/bind|unbind|list`

## SPI（供其他模块依赖）
- `ModelAccessService`：`List<EffectiveModel> effectiveModels(projectId)`、`checkModelAllowed(projectId, modelCode)`
- `KnowledgeAccessService`：`checkKbAllowed(projectId, agentId, kbId)`、`listRefs(projectId, agentId)`

## 设计决策
- 凭据只存引用（credential_ref），明文密钥进行内密钥系统；平台表内不出现 secret。
- 参数策略（param_policy）在项目授权时冻结范围，Agent 声明登记（02 模块）时校验声明的参数是否在授权范围内。
- LLMOps 对接抽象为 ModelProvider 配置 + 类型适配；真实协议适配（签名/流式差异）发生在 openJiuwen 侧的 Provider 插件，不在本平台。

## 状态
首版完成（2026-09-27），15/15 测试通过（新增 7 个）。实现位置：`com.bosc.agentops.modelknowledge`（6 Controller / 9 Service / 6 表，V2__model_knowledge.sql）。

## 实现要点与偏差
- 参数合并：policy 为空→默认参数全放行；policy 非空→默认参数只保留白名单键，数值按 min/max 收敛，被剔键进 removedKeys（有测试证据）。
- 重复 grant 视为更新策略（改策略即重新授权，与「授权时冻结」一致）。
- checkKbAllowed 取严格解释：agentId 非空时必须存在 agent 级或项目级 ref 才放行（fail-closed），无 ref 不回落 grant。
- grant_scope 的运行时匹配（集合/标签过滤）未实现：SPI 无查询上下文，待 02 模块落地后补。
- 列名规避保留字：`provider_type`/`kb_type`/`grant_scope`。
- 平台级目录管理接口（model:manage/kb:manage）本期对所有认证用户放行（与 project:create 同级），接平台运营角色时需收紧——已列入主文档待办。
