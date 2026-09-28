# scripts/smoke-e2e.sh — 端到端冒烟

启动 agentops-platform（Spring Boot + 内存 H2，每次启动数据干净，脚本可重复执行），
按真实上线顺序走通八大模块并逐条断言，全部通过时输出 `SMOKE E2E: ALL PASS`。

## 用法

```bash
scripts/smoke-e2e.sh
```

依赖：bash、curl、jq、JDK 17（经 `/usr/libexec/java_home -v 17` 解析）、Maven。

脚本行为：

- 后台执行 `mvn -B spring-boot:run`（默认端口 18080，避免与本机 8080 上其他服务冲突），
  轮询 `/v1/api/projects/list` 直至可响应（超时 120s），平台日志写入 `target/smoke-e2e-server.log`。
- 任何一步断言失败：打印该步原始响应体与 HTTP 状态并以 exit 1 退出。
- 退出时（含失败/中断）通过 trap 杀掉 mvn 进程及按端口精确清理 fork 出的 Spring Boot JVM。

## 覆盖流程（与 PASS 行一一对应）

1. 项目权限：创建项目（u1001）→ 添加成员 u1002（DEVELOPER）
2. 模型与知识：provider + model service → 项目 grant → effective-models 可见性断言；
   知识库 create → kb grant → kb-refs/bind
3. 资产：SKILL 资产 create → 版本 publish → 资产 publish；
   MCP_SERVICE / MCP_TOOL 资产各自创建并发布
4. Agent：NATIVE Agent create → 版本 register（声明含 model/skill/kb）→ 断言 REGISTERED
5. CMDB 归属：绑定 mock ACTIVE 应用（默认 APP-CORE-001），断言 SYNCED
6. 制品交付：agent-levels/confirm（P1）→ PROD deploy target（allowed 含 P1）→ 项目 attach
   → artifacts/register（四字段填全）→ releases create → gate（断言 gateResult 全过）
   → approve（MOCK-APPROVAL-001）→ deploy → 断言 Release RUNNING、Deployment 存在
7. 服务治理：routes/sync 断言 ACTIVE 路由；caller-policies/grant（USER/u1001）→
   /v1/invoke/{agentCode}/PROD（上游为本地桩伪地址，预期 HTTP 502 / code=50201）；
   mcp-policies/grant（允许）→ /v1/mcp-check 断言 allowed=true，未授权 tool 断言 allowed=false
8. 观测：audit/query 断言写操作留痕；alerts/webhook（机器 token）→ alerts/handle；
   components/register（无 endpoint）→ components/health 断言 UNKNOWN 计数 >= 1

## 环境变量

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `SMOKE_PORT` | `18080` | 平台监听端口 |
| `SMOKE_USER` | `u1001` | 主操作人（token 即 userId，见 application.yml `agentops.auth.users`） |
| `SMOKE_MEMBER` | `u1002` | 被添加的项目成员 |
| `SMOKE_MACHINE_TOKEN` | `dev-machine-token` | 机器通道 token（`agentops.observability.machine-token`） |
| `SMOKE_APP_CODE` | `APP-CORE-001` | CMDB mock ACTIVE 应用（`agentops.cmdb.mock-apps`） |

## 已知说明

- `/v1/invoke` 的上游是本地桩执行器生成的伪地址（`http://stub.local/{deploymentId}`），
  预期失败；脚本断言 HTTP 502 或响应 code=50201。
- InvocationRecord 仅在运行时面经 mapper 落库，管理面无查询接口，故跳过落库断言（脚本内有注释）。
