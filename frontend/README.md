# AgentOps 管理控制台（frontend）

`agentops-platform`（Spring Boot 后端）的产品化管理控制台。React 18 + TypeScript + Vite 5 + antd 5 + react-router-dom 6 + echarts/echarts-for-react；无状态管理库（React 内置 + Context）。

## 启动方式

```bash
npm install
npm run dev      # 默认 http://localhost:5173（端口被占用时自动顺延，以终端输出为准）
npm run build    # tsc + vite build，产物在 dist/
npm run preview  # 预览构建产物
```

### 后端地址（环境变量）

dev server 代理 `/v1` → `http://localhost:8080`（默认）。本机 8080 被占用或需要指向其他实例时：

```bash
# 后端（示例：18081 端口）
mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=18081
# 前端
VITE_API_TARGET=http://localhost:18081 npm run dev
```

`VITE_API_TARGET` 在 `vite.config.ts` 中读取，不写回配置文件；不改环境变量时即默认 8080。

## 认证与 token

- token 即 userId，请求头 `Authorization: Bearer <token>`；种子用户 `u1001`（张三）~ `u1006`（孙八），平台管理员 `u1001`（`agentops.auth.admins`）。
- 登录页支持 token 输入与种子用户快捷按钮；token 存 `localStorage`（`agentops_token`）。
- 顶栏「当前项目」为**全局项目切换器**（`localStorage` 键 `agentops_project`），各业务页面默认消费；无 token 访问任意页面跳 `/login`，401 自动登出。

## 菜单与页面

| 菜单 | 路由 | 说明 |
|---|---|---|
| 工作台 | `/` | 统计卡（项目/Agent/资产/今日调用/成功率/未处置告警）+ 近 7 天调用趋势折线 + Top Agent 排行 + 快捷入口 |
| 项目空间 | `/projects`、`/projects/:id` | 卡片化列表（角色徽标/状态/归档）；详情 tabs：成员、环境配额、模型授权（含 effective-models）、知识库授权（含 refs 绑定） |
| 团队空间 | `/teams` | 用户组列表/创建/删除、组成员管理、组被授权到项目的视图 |
| 广场 - Agent 广场 | `/square/agents` | PUBLIC Agent 卡片墙 + 搜索（`agent-square/list`） |
| 广场 - Skill 广场 | `/square/skills` | 当前项目可见 PUBLISHED 资产卡片墙，类型 Segmented + 搜索 |
| 资产中心 | `/assets`、`/assets/:id`、`/assets/upload`、`/assets/versions`、`/assets/http-api` | 资产列表/详情（版本/授权/引用）；**Skill 上传**（Upload.Dragger multipart → DRAFT，展示 sha256/大小，一键 publish-draft）；**版本管理**（左侧类型切换 SKILL/AGENT/MCP_*，右侧版本 Timeline + 草稿发布）；HTTP API 注册/转换 |
| Agent 管理 | `/agents`、`/agents/new`、`/agents/:id` | 列表 + **三步创建向导**（基本信息 → 接入方式 NATIVE/ADAPTED/HOSTED 联动 → 确认提交）；详情页含版本登记、CMDB 归属、**发布到广场/下架** |
| 可观测 | `/observability` | 大盘：趋势折线 / 成功率环 / Span 分布饼图 / 总量·延迟·告警卡；下方 tabs：链路追踪、审计查询、告警处置 |
| 交付发布 | `/delivery/artifacts|targets|levels|releases`、`/releases/:id` | 制品登记、部署目标（含项目 attach）、Agent 分级、发布单状态机（Steps + gate_result + gate/approve/deploy/rollback + 部署实例） |
| 服务治理 | `/gov/routes|caller-policies|mcp-policies|directory` | 路由 sync、调用方策略、MCP 策略、服务目录（只读） |
| 组件运维 | `/ops/components` | 健康汇总（UP/DOWN/UNKNOWN 统计 + 明细）+ 登记册管理 |
| 权限设置 | `/settings/permissions` | 角色 × 权限点 Checkbox 矩阵；仅平台管理员（u1001）可勾选保存（`roles/update`），其他人只读横幅提示 |
| 平台目录 | `/catalog/providers|services|kbs` | 模型 Provider / 模型服务 / 知识库目录管理 |

## 接口约定

- 全部 `POST /v1/api/...`，响应 `{code, message, data, requestId}`，`code=0` 成功；`src/api/client.ts` 统一封装（`api` JSON / `apiUpload` multipart）。
- 表单字段名与后端 DTO 一一对应；JSON 字段（declaration/definition/paramPolicy 等）表单内文本输入、提交前 `JSON.parse` 并校验。
- 视觉：品牌主色 `#2B5AED`（`src/theme.ts`），深色渐变侧边栏 + pill 选中态，统一 `PageHeader` 页头，卡片柔和阴影。

## 联调提示

- 模型授权 `paramPolicy` 是**参数白名单**：键为允许覆盖的参数名，值可带 `value`/`min`/`max`；策略外参数计入 `effectiveModels.removedKeys`。
- Agent 版本 declaration 结构：`{model:{modelCode,params}, skills:[{assetId,version}], mcpTools:[{assetId,version}], knowledgeBases:[{kbId,refVersion}], prompt, memory}`。
- 发布门禁校验：CMDB 归属绑定、制品完整性（imageDigest/configDigest/evaluationRef 必填）、Agent 分级、目标等级允许列表。
- 资产上传只产生 DRAFT；发布走 `assets/versions/publish-draft`（版本管理页或上传结果卡均可触发）。
- 权限矩阵修改仅 `u1001` 可用，其他用户只读（后端二次校验，前端同步禁用）。
