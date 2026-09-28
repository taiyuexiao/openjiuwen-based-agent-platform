# 模块 04：Skill 与 MCP 资产管理（asset-hub）

## 摘要
Skill 与 MCP 服务/工具的统一资产模型：创建、版本、发布、下线、项目作用域与跨项目授权、引用关系记录；
另提供历史 HTTP API（OpenAPI）注册并转换为工具定义的能力。

## 对应需求（能力清单 04）
- Skill 创建/修改/查询/发布/下线与基础版本管理；Agent 可通过 SDK 引用已发布 Skill。
- MCP 服务注册托管、工具配置/发布/更新/下线；Agent 按授权选择已托管 MCP 服务与工具。
- 资产绑定项目空间或共享范围，默认不全局可见；区分创建/修改/发布/下线/授权引用权限并保留责任人；
  跨项目复用须经责任人授权，记录 项目/Agent/版本/资产版本 引用关系；MCP 运行时调用权限由 07 模块治理。
- 历史 HTTP API 注册并转换为工具定义；基于 API 文件（OpenAPI）维护工具定义。

## 领域模型
- `Asset`：id、code、name、asset_type（SKILL / MCP_SERVICE / MCP_TOOL / HTTP_API）、owner_project_id、owner_user_id（责任人）、
  visibility（PROJECT/SHARED）、status（DRAFT/PUBLISHED/OFFLINE）、description。
  说明：MCP_TOOL 归属某个 MCP_SERVICE（parent_asset_id）；HTTP_API 是历史接口登记表，可转换为 MCP_TOOL。
- `AssetVersion`：asset_id、version（x.y.z）、definition JSON（Skill 的清单/MCP 服务连接配置/工具 schema）、
  status（PUBLISHED/OFFLINE，版本不可变：发布后 definition 不再改）、published_by/at。
- `AssetGrant`：asset_id、to_project_id、granted_by、created_at。跨项目复用的显式授权记录。
- `AssetReference`：asset_id、asset_version、project_id、agent_id（弱引用，02 模块落地后对齐）、created_by。
  记录「谁在用哪个版本」，供下线影响分析与追溯。
- 可见性规则：PROJECT=仅归属项目；SHARED=需 AssetGrant 授权的项目可见可用。下线（OFFLINE）禁止新引用，已有引用保留可运行。

## 权限点
`asset:read / asset:create / asset:update / asset:publish / asset:offline / asset:grant / asset:reference`
角色映射：OWNER/ADMIN 全量；DEVELOPER read+create+update+publish+reference；OPERATOR read。
责任人与平台 ADMIN 才可 offline/grant（在 service 层二次校验，不止角色）。

## 关键接口（REST，`/v1/api/...`）
- 资产：`/assets/create|update|publish|offline|detail|list`、`/assets/{id}/versions/publish|list`
- 授权与引用：`/assets/{id}/grants/grant|revoke|list`、`/assets/{id}/references/add|remove|list`
- HTTP 转换：`/http-apis/register`（提交 OpenAPI JSON/YAML → 解析出工具定义草案）、`/http-apis/{id}/convert`（生成 MCP_TOOL 资产草稿）、`/http-apis/{id}/tools/list`

## SPI（供其他模块）
- `AssetAccessService`：`checkUsable(assetId, version, projectId)`（可见性+授权+状态判定，02 声明登记与 07 运行治理都用）；
  `listReferences(assetId)`（下线前影响分析）。

## 设计决策
- 版本不可变：发布后修改只能发新版本；引用必须锁定到版本，杜绝「引用漂移」。
- 运行时调用鉴权不在本模块（07 的 MCP 网关职责）；本模块只管「能不能被发现、能不能被引用」。
- OpenAPI 解析本期支持 OpenAPI 3.x JSON（YAML 可后补）；生成的工具定义必须人工确认后才发布（draft → publish 两步）。
- 与 skillhub 的关系：本模块是行内资产唯一事实源；skillhub 若引入仅作分发渠道，不持有授权语义。

## 状态
首版完成（2026-09-27），2026-09-28 增补上传与草稿发布（累计 106/106 测试通过）。实现位置：`com.bosc.agentops.assethub`（6 Controller / 9 Service / 4 表，V3__asset_hub.sql）。

## 上传与草稿发布（2026-09-28 增补）
- `POST /v1/api/assets/upload`（multipart）：接受 .md/.zip/.json/.yaml/.txt（≤20MB），code 已存在则加 DRAFT 版本，否则建 DRAFT 资产+版本；
  文件落盘 `{agentops.asset-upload.dir}/{assetId}/{version}/{filename}`，definition 记录 filename/size/sha256/storedPath。
- `POST /v1/api/assets/versions/publish-draft` {assetId, version, projectId}：DRAFT 版本发布为 PUBLISHED（published_by/at 落库）；
  资产仍为 DRAFT 时联动置 PUBLISHED；MCP_TOOL 发布仍强制要求 parent MCP_SERVICE。
- AssetVersionStatus 新增 DRAFT；checkUsable 仍只认 PUBLISHED，草稿天然不可用。

## 实现要点与偏差
- OpenAPI 原文存为 HTTP_API 资产的 1.0.0 版本 definition（版本不可变，天然存档）；convert 生成的 MCP_TOOL 同步建 1.0.0 版本，资产保持 DRAFT，「人工确认后发布」落在资产层。
- 版本只有 PUBLISHED/OFFLINE 两态，任何对已发布 definition 的修改一律 40902，只能发新版本。
- MCP_TOOL 创建时 parent 可空（convert 场景尚无服务可挂），发布时强制必须有 parent MCP_SERVICE。
- 注解层 asset:offline/asset:grant 只映射 OWNER/ADMIN，service 层「责任人或 ADMIN/OWNER」为第二道闸；DEVELOPER 身份责任人经 REST 会被注解层先拦。
- SHARED 语义：归属项目始终可用（不需对自己 grant）；references/list 归属项目看全部（影响分析），被授权项目仅看自己。
- OpenAPI 解析用 Jackson 读树（operationId 缺省回退 method+path 命名），未引入新依赖。
