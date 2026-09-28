# 高码智能体平台（AgentOps）项目主文档

## 项目做什么

上海银行高码智能体平台：为行内开发团队提供高码 Agent 的统一开发、资产管理、受控交付、发布治理与运行观测能力。
以开源 openJiuwen（agent-core 内核）为 Agent 执行底座，管理面自建。需求来源：`../高码平台能力清单.xlsx`（9 大模块 89 条）；
选型依据：`../openJiuwen二次调研报告.docx`。

**范围约束**：模块 05（智能体评测）由独立的评测平台承担（gitee: shanghai-bank_1/agent-evaluation-platform），
本平台不开发评测功能，本期也不做与评测平台的对接（预留接口边界即可）。

## 技术栈与约定

- 后端：Java 17 + Spring Boot 3.3 + MyBatis-Plus + Flyway；构建用系统 Maven（需 `JAVA_HOME` 指向 JDK 17，本机 mvn 默认绑 JDK 26）。
- 数据库：开发/测试 H2（MySQL 兼容模式），生产 MySQL 8。
- 前端：React 18 + Vite 5 + TS + antd 5（`frontend/`，已完成首版，见 modules/frontend.md）。
- 执行底座：openJiuwen agent-core（Python，进程外集成，经 REST/OTLP 交互，不做源码级耦合）。
- 包结构：`com.bosc.agentops.<模块>.{controller,service,mapper,entity,dto}`，横向分层、层内按业务分包。
- API：`/v1/api/<资源>/<动作>`，统一响应 `{code,message,data,requestId}`；认证与行内 IAM 的对接通过 `auth` 模块的适配层，本地开发用简单 token。
- 所有管理功能模块都必须注册权限点（参照模块 01 的 PermissionService），未注册的接口视为越权面（该教训来自 agent-studio 的 fail-open 设计）。

## 模块索引

| 模块 | 文档 | 对应能力清单 | 状态 |
|---|---|---|---|
| 平台基础 foundation | [modules/foundation.md](modules/foundation.md) | 全部模块的公共底座（统一响应/错误/认证上下文/权限/审计） | 首版完成 |
| 01 项目与研发环境 project-space | [modules/project-space.md](modules/project-space.md) | 01 | 首版完成 |
| 02 开发与调试 agent-dev | [modules/agent-dev.md](modules/agent-dev.md) | 02 | 首版完成 |
| 03 模型与知识接入 model-knowledge | [modules/model-knowledge.md](modules/model-knowledge.md) | 03 | 首版完成 |
| 04 Skill/MCP 资产 asset-hub | [modules/asset-hub.md](modules/asset-hub.md) | 04 | 首版完成 |
| 06 制品交付与托管运行 delivery | [modules/delivery.md](modules/delivery.md) | 06 | 首版完成 |
| 07 服务发布与访问治理 governance | [modules/governance.md](modules/governance.md) | 07 | 首版完成 |
| 08 观测与运维保障 observability | [modules/observability.md](modules/observability.md) | 08 | 首版完成 |
| 09 企业集成 enterprise-integration | [modules/enterprise-integration.md](modules/enterprise-integration.md) | 09 | 首版完成 |
| 前端控制台 frontend | [modules/frontend.md](modules/frontend.md) | 全部模块的界面 | 首版完成 |
| 底座桥接 agent-bridge | [modules/agent-bridge.md](modules/agent-bridge.md) | 02/08 的底座侧验证与适配 | 首版完成（联调通过） |

## 关键架构决策

1. **路线二：自建管理面**（2026-09-27）。复用 openJiuwen agent-core 为执行内核；不采用 agent-studio 改造路线
   （其权限模型 fail-open、Simple Auth 为 PoC 级，安全收口成本高）。详见二次调研报告第七章。
2. **底座进程外集成**：openJiuwen 侧能力（执行、观测）经 REST/OTLP 接入，行内治理对象（项目、资产、发布、路由）自持数据模型。
3. **权限模型先行**：项目/资产/Agent 的统一归属与授权模型是全平台关键路径，模块 01 优先于一切业务模块。
4. **不可变版本对象串联全流程**：AgentVersion / AssetVersion / Release / Deployment / ServiceRoute 设计见二次调研报告第八章对象表。

## 变更日志

| 日期 | 类型 | 摘要 | 涉及模块 |
|---|---|---|---|
| 2026-09-27 | 初始化 | 项目骨架、文档结构建立 | 全部 |
| 2026-09-27 | 功能 | foundation + 模块 01 首版完成：项目/环境/成员/用户组 CRUD、角色权限（含组授权展开）、fail-closed 权限拦截、审计落库；8 个集成测试全过 | foundation、project-space |
| 2026-09-27 | 功能 | 模块 03 首版完成：模型目录/项目授权/参数策略合并、知识库登记/授权/引用关联；新增 7 测试，累计 15 全过 | model-knowledge |
| 2026-09-27 | 功能 | 模块 04 首版完成：Skill/MCP/HTTP_API 资产全生命周期（不可变版本、可见性+跨项目授权、引用关系）、OpenAPI 转工具定义；新增 8 测试，累计 23 全过 | asset-hub |
| 2026-09-27 | 功能 | 模块 02 首版完成：Agent/AgentVersion 与声明登记校验（模型/资产/知识库授权逐项校验、失败项聚合返回）、三类历史接入边界、脚手架模板；新增 13 测试，累计 36 全过 | agent-dev |
| 2026-09-27 | 功能 | 模块 09 首版完成：CMDB 归属绑定（连接器 SPI + Mock）、权威信息快照与重同步、投产归属门禁 SPI（AppBindingGate）、ITSM 登记占位；新增 11 测试，累计 47 全过 | enterprise-integration |
| 2026-09-27 | 功能 | 模块 06 首版完成：制品/部署目标/Agent 分级/发布状态机+四项门禁/拟办分离/回滚留痕/执行器 SPI（本地 stub + openJiuwen 骨架）；新增 15 测试，累计 62 全过 | delivery |
| 2026-09-27 | 功能 | 模块 07 首版完成：服务目录、路由同步、统一调用入口（鉴权/限流/SSE 透传/调用记录）、MCP 调用鉴权 PEP、Agent 间鉴权；新增 13 测试，累计 75 全过 | governance |
| 2026-09-27 | 功能 | 模块 08 首版完成：OTLP trace 接收与链路查询、审计查询（脱敏）、告警接入与处置动作（跨模块停路由/吊销调用方）、组件健康聚合；新增 10 测试，累计 85 全过 | observability |
| 2026-09-27 | 修复 | PermissionRegistrationAuditor 支持排除前缀配置，消除运行时面/机器通道的启动告警 | foundation、governance、observability |
| 2026-09-27 | 验证 | 端到端冒烟脚本 `scripts/smoke-e2e.sh` 完成并连跑两遍全绿（36 步覆盖 项目→授权→资产→声明登记→归属→制品→门禁→审批→部署→路由→invoke→MCP 鉴权→审计→告警→组件健康） | 全部 |
| 2026-09-27 | 功能+验证 | agent-bridge 完成：真实 openJiuwen agent-core 0.1.18 运行 Agent，自定义模型 Provider 插件（自动注册机制实测）、OTLP/JSON exporter 回传平台 traces/query；三项调研结论全部工程验证通过 | agent-bridge、governance、observability |
| 2026-09-28 | 功能 | 前端控制台首版完成：23 个路由覆盖全部模块，`npm run build` 通过，并经真实后端全链路联调 | frontend |
| 2026-09-28 | 功能 | 后端补口：资产文件上传（upload）与草稿发布（publish-draft）、Agent 广场与可见性（V9）、权限矩阵查看与管理（admin 制）、观测统计聚合（stats/overview）；新增 21 测试，累计 106 全过 | asset-hub、agent-dev、foundation、observability |
| 2026-09-28 | 改版 | 前端产品化重做：深色侧边栏+品牌主题、工作台大盘、团队空间、Agent/Skill 广场、Skill 上传、版本管理统一页、可观测图表大盘、权限矩阵页；14 菜单 27 路由，逐页联调验证 | frontend |
| 2026-09-28 | 改版 | 前端视觉精修：设计令牌系统化（theme.ts）、分栏登录页、毛玻璃顶栏、StatCard sparkline、SquareCard/StatusBadge/EmptyState 公共组件收敛、Skeleton 加载与微交互；构建通过，逐页截图自查 | frontend |

## 待处理 / 后续

**当前状态**：8 个后端模块 + foundation 首版全部完成（85 集成测试全绿）；端到端冒烟脚本 36 步全过；
前端控制台完成；与真实 openJiuwen 底座的联调通过（HOSTED 通路：平台 → 网关 → agent-core SSE → OTel span 回传）。

下一步建议（按优先级）：
- **行内 IAM/SSO 真实对接**（当前 AuthProvider 为本地 token；平台级权限点对认证用户全放行，接 IAM 时需收紧）。
- **NATIVE/ADAPTED 通路的真实底座部署联调**：目前真实联调走的是 HOSTED 通路；delivery 的 OpenJiuwenRuntimeExecutor 接真实 agent-runtime 仍未实测（runtime 的 K8s/REST 部署目前硬编码低码路径，接入前需先按其扩展点适配，见二次调研报告第四章）。
- **echo Provider 换行内 LLMOps 网关 Provider**（agent-bridge/echo_model_client.py 注释有换装指南）。
- **invoke↔trace 精确串联**：平台 X-Trace-Id 以 traceparent 透传上游（当前 traceId 双轨，详见 modules/agent-bridge.md）。
- **InvocationRecord 管理面查询接口**（当前无查询 API，冒烟脚本只能断言到 HTTP 层）。
- **评测平台对接**（等其执行链路落地后启动，方案见二次调研报告第六章方案 A；届时补 evaluation_ref 状态校验）。
- **限流多实例化**：governance 限流当前为内存滑动窗口单实例语义，生产多副本需换 Redis。
- **接真实 MySQL 8**：处理 `role` 列转义（详见 project-space 模块文档偏差记录）；平台版本升级/配置变更/回退的流程编排（08 目前只有组件登记与健康聚合）。
- **openJiuwen 版本锁定与兼容清单**。
