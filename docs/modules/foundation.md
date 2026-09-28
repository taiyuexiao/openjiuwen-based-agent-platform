# 模块：平台基础（foundation）

## 状态
首版已完成（2026-09-27），集成测试全过（8/8，见 project-space 模块测试）。实现位置：`src/main/java/com/bosc/agentops/common/`。

## 摘要
全部业务模块的公共底座：统一 API 响应与错误码、请求上下文（requestId/操作人）、认证适配层、权限注解与检查、审计事件。

## 动机
- 所有管理功能接口必须纳入权限管控（反面教材：agent-studio 未登记 URI 默认放行）。权限采用「显式注册 + 默认拒绝」。
- 认证对接行内 IAM 是后期事项，当前以 `AuthProvider` 接口隔离：本地开发用 SimpleTokenAuthProvider，生产替换为行内实现。

## 实现落点
- `common/api`：ApiResponse {code,message,data,requestId} / ErrorCode / BizException / GlobalExceptionHandler；
  未认证返回 40101，权限拒绝 40301，参数校验失败 400xx。
- `common/context`：RequestContext（ThreadLocal：requestId/AuthUser/clientIp）；requestId 取 `X-Request-Id` 头，否则生成 UUID。
- `common/auth`：AuthProvider SPI + SimpleTokenAuthProvider（token 即 userId，用户表来自配置 `agentops.auth.users`）+ AuthFilter。
- `common/permission`：PermissionService SPI、@RequirePermission + @PermissionScope（SpEL）+ PermissionAspect；
  scope 解析顺序：@PermissionScope SpEL → 名为 projectId 的参数 → 参数对象的 getProjectId()。
  另有 PermissionRegistrationAuditor：启动时扫描 Controller，发现未加 @RequirePermission 的接口即告警（防 fail-open）。
- `common/audit`：AuditService + audit_event 表，业务事务内写入（不异步，避免审计丢失）。
- `common/mybatis`：AuditMetaObjectHandler 自动填充 createdAt/updatedAt。主键自增 BIGINT，时间 LocalDateTime。

## 关键接口
- `AuthProvider.resolve(token) -> AuthUser`（userId、姓名、所属组）
- `PermissionService.check(userId, permission, projectId)`，权限点格式 `<模块>:<资源>:<动作>`，如 `project:member:manage`
- 注解 `@RequirePermission("project:member:manage")`，AOP 从请求上下文取操作人、从参数解析资源 scope

## 设计决策
- 权限判定 fail-closed：无任何授权记录时拒绝；接口未标注解时启动即告警。
- 审计与业务同事务写 audit_event 表，避免异步丢失导致审计缺失。

## Bug 与问题记录
（暂无）

## 已知限制
- 行内 IAM 未接，AuthUser 来自本地配置 mock。
- 平台级权限（projectScoped=false，如 project:create / group:manage）本期对所有认证用户放行，无白名单语义；接 IAM 时需收紧。

## 权限矩阵管理（2026-09-28 增补）
- `POST /v1/api/permissions/matrix`：返回权限点全集 × 四角色的勾选矩阵（认证用户可读）。
- `POST /v1/api/permissions/roles/update`：整体替换某角色权限点集合；**仅平台管理员**（配置 `agentops.auth.admins`，当前 u1001）可调用，非管理员 40301；改 OWNER 直接 40902 防锁死。
- 管理员判定挂在 SimpleTokenAuthProvider.isAdmin()（本地认证实现）；接行内 IAM 时应随 AuthProvider 一并替换。
