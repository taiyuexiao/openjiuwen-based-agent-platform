# 模块 01：项目与研发环境（project-space）

## 摘要
项目空间的创建与管理、成员/角色、用户组、项目级授权。是全平台统一归属与授权模型的根模块。

## 对应需求（能力清单 01）
- 按项目隔离成员、配置、资产引用和研发环境资源配额（配额模型本期只建数据结构）。
- 对接行内身份权限体系，支持成员授权和密钥安全引用（IAM 对接走 foundation 的 AuthProvider 适配层）。
- 建立项目、Agent 及 Skill/MCP 等可复用组件的统一归属与授权模型：用户和用户组授权、Agent 级能力绑定、跨项目共享授权、环境隔离。

## 领域模型
- `Project`：id、code（唯一）、name、owner、status（ACTIVE/ARCHIVED）、环境集合。
- `ProjectEnvironment`：project_id、env（DEV/SIT/UAT/PROD）、资源配额 JSON（本期仅记录）。
- `ProjectMember`：project_id、subject_type（USER/GROUP）、subject_id、role。
- 角色四档（沿用行业惯例并与 studio 对齐）：OWNER / ADMIN / DEVELOPER / OPERATOR。
- `UserGroup` / `UserGroupMember`：用户组及成员。
- 权限矩阵：见「权限设计」节；角色-权限映射落库（role_permission），可配置。

## 关键接口（REST）
- `POST /v1/api/projects/create|update|archive|detail|list`
- `POST /v1/api/projects/{id}/members/add|remove|change-role|list`（subject 支持用户与用户组）
- `POST /v1/api/user-groups/create|...`、`/members/add|remove`
- 权限点：`project:create / project:update / project:archive / project:member:manage / project:env:manage / group:manage`

## 设计决策
- 成员授权同时支持用户和用户组；权限判定时展开组 → 用户（foundation 的 PermissionService 统一做）。
- 删除一律软删/归档，保留审计追溯。
- 跨项目共享授权（资产侧）由 asset-hub 模块在自己的表实现，本模块只提供「项目存在性 + 成员判定」SPI：`ProjectAccessService`。

## 实现状态（2026-09-27 首版完成）
- 实现位置：`src/main/java/com/bosc/agentops/project/`（4 个 Controller、6 个 Mapper、ProjectAccessService SPI）。
- 创建项目自动初始化 DEV/SIT/UAT/PROD 四个环境记录；创建者自动成为 OWNER。
- 集成测试 `ProjectSpaceIntegrationTest` 8 个用例全过，覆盖：创建即 OWNER、ADMIN 加成员、DEVELOPER 加成员被拒（fail-closed 证据）、组授权展开生效、移除最后一个 OWNER 被拒、审计按 requestId 落库、未认证 40101。

## 实现偏差记录（相对初版设计）
- 新增 `project:read` 权限点（四角色都有）：读接口（detail/members:list/envs:list）需要权限点做 fail-closed 判定，初版矩阵只定义了写权限。
  种子映射实际为：OWNER/ADMIN = read+update+archive+member:manage+env:manage，DEVELOPER = read+update，OPERATOR = 仅 read。
- 组权限判定实时查 `user_group_member` 表，而非 AuthUser.groupIds（后者只是认证 mock 的展示信息，实时查库保证动态加人进组立即生效）。
- DDL 中 `role` 列用反引号转义（MySQL 8.0 保留字）；H2 侧用 NON_KEYWORDS 放行。**接真实 MySQL 8 时注意 MyBatis 生成 SQL 的 role 列转义问题**。

## Bug 与问题记录
（暂无）

## 已知限制
- 资源配额仅建模不执行；环境隔离的网络/配额执行依赖容器平台，后期由 delivery 模块落实。
- 密钥安全引用（凭据托管）在 foundation 后续迭代，不在本期。
