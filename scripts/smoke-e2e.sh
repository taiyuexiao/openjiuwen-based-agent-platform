#!/usr/bin/env bash
# agentops-platform 端到端冒烟脚本
#
# 启动平台（内存 H2，每次数据干净），走通完整上线流程：
#   项目权限(01) → 模型与知识(03) → 资产(04) → Agent(02) → CMDB归属(09)
#   → 制品交付(06) → 服务治理(07) → 观测(08)
# 每步断言 code=0（运行时面按 HTTP 状态码断言），失败打印原始响应并 exit 1。
#
# 用法: scripts/smoke-e2e.sh
# 环境变量（均有默认值）:
#   SMOKE_PORT          平台监听端口（默认 18080，避开本机 8080 上其他服务）
#   SMOKE_USER          主操作人 token（默认 u1001，application.yml agentops.auth.users）
#   SMOKE_MEMBER        被添加的项目成员（默认 u1002）
#   SMOKE_MACHINE_TOKEN 机器通道 token（默认 dev-machine-token，application.yml agentops.observability.machine-token）
#   SMOKE_APP_CODE      CMDB mock ACTIVE 应用（默认 APP-CORE-001，application.yml agentops.cmdb.mock-apps）
set -euo pipefail

# 脚本含中文多字节字符：强制 C locale 按字节解析，
# 避免 UTF-8 locale 下 bash 把紧邻 $VAR 的多字节字符（如 ，（））并入变量名导致 unbound variable。
export LC_ALL=C

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$PROJECT_ROOT"

PORT="${SMOKE_PORT:-18080}"
BASE="http://127.0.0.1:${PORT}"
TOKEN="${SMOKE_USER:-u1001}"
MEMBER="${SMOKE_MEMBER:-u1002}"
MACHINE_TOKEN="${SMOKE_MACHINE_TOKEN:-dev-machine-token}"
APP_CODE="${SMOKE_APP_CODE:-APP-CORE-001}"
LOG_FILE="$PROJECT_ROOT/target/smoke-e2e-server.log"
READY_TIMEOUT_S=120

STEP=0
RESP=""
HTTP_CODE=""
SERVER_PID=""

# ---------- 输出与断言 ----------

step_pass() {
  STEP=$((STEP + 1))
  echo "PASS ${STEP}. $1"
}

fail() {
  echo "" >&2
  echo "FAIL: $1" >&2
  echo "----- HTTP status: ${HTTP_CODE:-?}" >&2
  echo "----- raw response body:" >&2
  printf '%s\n' "${RESP:-<empty>}" >&2
  echo "-----" >&2
  exit 1
}

# jqv <jq-filter>：从 RESP 提取字段；jq 失败打印原始响应
jqv() {
  local out
  if ! out=$(jq -er "$1" <<<"$RESP" 2>/dev/null); then
    fail "jq 提取失败（filter: $1）"
  fi
  printf '%s' "$out"
}

# assert_jq <jq-filter(须为 true)> <描述>
assert_jq() {
  local v
  if ! v=$(jq -r "$1" <<<"$RESP" 2>/dev/null); then
    fail "jq 断言执行失败: $2（filter: $1）"
  fi
  [ "$v" = "true" ] || fail "断言失败: $2"
}

# api <path> <json-body>：管理面 POST（带 Bearer token），断言 HTTP 200 且 code=0
api() {
  local path="$1" body="${2:-{\}}"
  local body_file
  body_file=$(mktemp)
  HTTP_CODE=$(curl -sS -o "$body_file" -w '%{http_code}' -X POST "$BASE$path" \
    -H 'Content-Type: application/json' \
    -H "Authorization: Bearer $TOKEN" \
    -d "$body") || { RESP="curl 传输错误"; rm -f "$body_file"; fail "curl 调用失败: $path"; }
  RESP=$(cat "$body_file")
  rm -f "$body_file"
  [ "$HTTP_CODE" = "200" ] || fail "HTTP 状态非 200: $path"
  local code
  code=$(jq -r '.code // "NO_CODE"' <<<"$RESP" 2>/dev/null) || fail "响应非 JSON: $path"
  [ "$code" = "NO_CODE" ] && fail "响应缺少 code 字段: $path"
  [ "$code" = "0" ] || fail "业务码非 0（code=$code）: $path"
}

# api_raw <path> <json-body> <额外curl参数...>：只发请求，不断言（运行时面用）
api_raw() {
  local path="$1" body="$2"
  shift 2
  local body_file
  body_file=$(mktemp)
  HTTP_CODE=$(curl -sS -o "$body_file" -w '%{http_code}' -X POST "$BASE$path" \
    -H 'Content-Type: application/json' "$@" \
    -d "$body") || { RESP="curl 传输错误"; rm -f "$body_file"; fail "curl 调用失败: $path"; }
  RESP=$(cat "$body_file")
  rm -f "$body_file"
}

# ---------- 平台生命周期 ----------

cleanup() {
  local rc=$?
  if [ -n "$SERVER_PID" ]; then
    kill "$SERVER_PID" 2>/dev/null || true
    # spring-boot:run 默认 fork 子 JVM，按监听端口精确清理（不误杀其他 java 进程）
    local pids
    pids=$(lsof -nP -ti "tcp:$PORT" 2>/dev/null || true)
    if [ -n "$pids" ]; then
      kill $pids 2>/dev/null || true
      sleep 2
      pids=$(lsof -nP -ti "tcp:$PORT" 2>/dev/null || true)
      if [ -n "$pids" ]; then
        kill -9 $pids 2>/dev/null || true
      fi
    fi
  fi
  exit "$rc"
}
trap cleanup EXIT INT TERM

if lsof -nP -ti "tcp:$PORT" >/dev/null 2>&1; then
  echo "端口 $PORT 已被占用，请先释放或用 SMOKE_PORT 指定其他端口" >&2
  exit 1
fi

echo "== 启动 agentops-platform（port=$PORT，日志: $LOG_FILE）=="
mkdir -p "$PROJECT_ROOT/target"
JAVA_HOME="$(/usr/libexec/java_home -v 17)" mvn -B spring-boot:run \
  "-Dspring-boot.run.arguments=--server.port=$PORT" \
  >"$LOG_FILE" 2>&1 &
SERVER_PID=$!

echo "== 等待 /v1/api 可响应（超时 ${READY_TIMEOUT_S}s）=="
deadline=$((SECONDS + READY_TIMEOUT_S))
ready=0
while [ $SECONDS -lt $deadline ]; do
  if ! kill -0 "$SERVER_PID" 2>/dev/null; then
    echo "平台进程提前退出，最近日志：" >&2
    tail -n 60 "$LOG_FILE" >&2
    exit 1
  fi
  probe=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$BASE/v1/api/projects/list" \
    -H "Authorization: Bearer $TOKEN" 2>/dev/null || true)
  if [ "$probe" = "200" ]; then
    ready=1
    break
  fi
  sleep 2
done
if [ "$ready" != "1" ]; then
  echo "等待平台就绪超时（${READY_TIMEOUT_S}s），最近日志：" >&2
  tail -n 60 "$LOG_FILE" >&2
  exit 1
fi
echo "== 平台已就绪，开始冒烟 =="

# ---------- 01 项目权限 ----------

api /v1/api/projects/create \
  '{"code":"SMOKE-E2E","name":"冒烟测试项目","description":"smoke-e2e 自动创建"}'
PROJECT_ID=$(jqv '.data.id')
step_pass "创建项目（u1001）projectId=$PROJECT_ID"

api "/v1/api/projects/$PROJECT_ID/members/add" \
  "{\"subjectType\":\"USER\",\"subjectId\":\"$MEMBER\",\"role\":\"DEVELOPER\"}"
assert_jq ".data.subjectId == \"$MEMBER\" and .data.role == \"DEVELOPER\"" "成员角色应为 DEVELOPER"
step_pass "添加项目成员 $MEMBER（DEVELOPER）"

# ---------- 03 模型与知识 ----------

api /v1/api/model-providers/create \
  '{"code":"PROV-SMOKE","name":"冒烟模型提供方","providerType":"OPENAI_COMPAT","endpoint":"https://models.example.com/v1","authType":"CREDENTIAL_REF","credentialRef":"cred-smoke"}'
PROVIDER_ID=$(jqv '.data.id')
step_pass "创建模型 Provider providerId=$PROVIDER_ID"

api /v1/api/model-services/create \
  "{\"providerId\":$PROVIDER_ID,\"modelCode\":\"smoke-llm-001\",\"displayName\":\"冒烟大模型\",\"capabilities\":{\"chat\":true},\"defaultParams\":{\"temperature\":0.7,\"maxTokens\":1024}}"
MODEL_SERVICE_ID=$(jqv '.data.id')
step_pass "创建模型服务 modelServiceId=$MODEL_SERVICE_ID"

api "/v1/api/projects/$PROJECT_ID/model-grants/grant" \
  "{\"modelServiceId\":$MODEL_SERVICE_ID}"
step_pass "项目模型授权（model-grants/grant）"

api "/v1/api/projects/$PROJECT_ID/model-grants/effective-models"
assert_jq '[.data[].modelCode] | index("smoke-llm-001") != null' "effective-models 应包含 smoke-llm-001"
step_pass "effective-models 断言模型可见"

api /v1/api/knowledge-bases/create \
  '{"code":"KB-SMOKE","name":"冒烟知识库","kbType":"RAGFLOW","endpoint":"https://kb.example.com","connectionConfig":{"dataset":"smoke"}}'
KB_ID=$(jqv '.data.id')
step_pass "创建知识库 kbId=$KB_ID"

api "/v1/api/projects/$PROJECT_ID/kb-grants/grant" \
  "{\"kbId\":$KB_ID}"
step_pass "项目知识库授权（kb-grants/grant）"

api "/v1/api/projects/$PROJECT_ID/kb-refs/bind" \
  "{\"kbId\":$KB_ID,\"refVersion\":\"v1\"}"
step_pass "知识库引用绑定（kb-refs/bind，项目级）"

# ---------- 04 资产 ----------

api /v1/api/assets/create \
  "{\"code\":\"SKILL-SMOKE\",\"name\":\"冒烟技能\",\"assetType\":\"SKILL\",\"projectId\":$PROJECT_ID,\"visibility\":\"PROJECT\",\"description\":\"冒烟技能资产\"}"
SKILL_ASSET_ID=$(jqv '.data.id')

api "/v1/api/assets/$SKILL_ASSET_ID/versions/publish" \
  "{\"projectId\":$PROJECT_ID,\"version\":\"1.0.0\",\"definition\":{\"name\":\"smoke-skill\",\"entry\":\"main.py\"}}"
assert_jq '.data.status == "PUBLISHED"' "资产版本应为 PUBLISHED"
step_pass "创建 SKILL 资产 + 版本 publish assetId=$SKILL_ASSET_ID"

api /v1/api/assets/publish \
  "{\"id\":$SKILL_ASSET_ID,\"projectId\":$PROJECT_ID}"
assert_jq '.data.status == "PUBLISHED"' "资产应为 PUBLISHED"
step_pass "SKILL 资产 publish"

api /v1/api/assets/create \
  "{\"code\":\"MCPSVC-SMOKE\",\"name\":\"冒烟 MCP 服务\",\"assetType\":\"MCP_SERVICE\",\"projectId\":$PROJECT_ID,\"visibility\":\"PROJECT\"}"
MCP_SERVICE_ID=$(jqv '.data.id')
api "/v1/api/assets/$MCP_SERVICE_ID/versions/publish" \
  "{\"projectId\":$PROJECT_ID,\"version\":\"1.0.0\",\"definition\":{\"endpoint\":\"https://mcp.example.com/sse\"}}"
api /v1/api/assets/publish \
  "{\"id\":$MCP_SERVICE_ID,\"projectId\":$PROJECT_ID}"
step_pass "创建 MCP_SERVICE 资产并发布 assetId=$MCP_SERVICE_ID"

api /v1/api/assets/create \
  "{\"code\":\"MCPTOOL-SMOKE\",\"name\":\"冒烟 MCP 工具\",\"assetType\":\"MCP_TOOL\",\"parentAssetId\":$MCP_SERVICE_ID,\"projectId\":$PROJECT_ID,\"visibility\":\"PROJECT\"}"
MCP_TOOL_ASSET_ID=$(jqv '.data.id')
api "/v1/api/assets/$MCP_TOOL_ASSET_ID/versions/publish" \
  "{\"projectId\":$PROJECT_ID,\"version\":\"1.0.0\",\"definition\":{\"tool\":\"smoke_tool\",\"schema\":{\"type\":\"object\"}}}"
api /v1/api/assets/publish \
  "{\"id\":$MCP_TOOL_ASSET_ID,\"projectId\":$PROJECT_ID}"
assert_jq '.data.status == "PUBLISHED"' "MCP_TOOL 资产应为 PUBLISHED"
step_pass "创建 MCP_TOOL 资产并发布 assetId=$MCP_TOOL_ASSET_ID"

# ---------- 02 Agent ----------

api /v1/api/agents/create \
  "{\"code\":\"agent-smoke-001\",\"name\":\"冒烟 Agent\",\"projectId\":$PROJECT_ID,\"accessMode\":\"NATIVE\",\"description\":\"冒烟 Agent\"}"
AGENT_ID=$(jqv '.data.id')
step_pass "创建 NATIVE Agent agentId=$AGENT_ID"

api "/v1/api/agents/$AGENT_ID/versions/register" \
  "{\"projectId\":$PROJECT_ID,\"version\":\"1.0.0\",\"declaration\":{\"model\":{\"modelCode\":\"smoke-llm-001\",\"params\":{\"temperature\":0.3}},\"prompt\":{\"system\":\"你是冒烟测试助手\"},\"skills\":[{\"assetId\":$SKILL_ASSET_ID,\"version\":\"1.0.0\"}],\"knowledgeBases\":[{\"kbId\":$KB_ID}]}}"
assert_jq '.data.status == "REGISTERED"' "Agent 版本应为 REGISTERED"
step_pass "注册 Agent 版本（声明含 model/skill/kb），断言 REGISTERED"

# ---------- 09 CMDB 归属 ----------

api /v1/api/bindings/bind \
  "{\"projectId\":$PROJECT_ID,\"agentId\":$AGENT_ID,\"appCode\":\"$APP_CODE\"}"
assert_jq ".data.appCode == \"$APP_CODE\" and .data.syncStatus == \"SYNCED\"" "CMDB 绑定应为 SYNCED"
step_pass "CMDB 归属绑定（mock ACTIVE 应用 $APP_CODE）"

# ---------- 06 制品交付 ----------

api /v1/api/agent-levels/confirm \
  "{\"projectId\":$PROJECT_ID,\"agentId\":$AGENT_ID,\"level\":\"P1\",\"source\":\"SMOKE-REVIEW-001\"}"
assert_jq '.data.level == "P1" and .data.effective == true' "分级应为 P1 且生效"
step_pass "Agent 分级 confirm（P1）"

api /v1/api/deploy-targets/create \
  '{"code":"PROD-SMOKE","name":"冒烟生产集群","env":"PROD","cluster":"prod-cluster","namespace":"agentops","baseResource":{"storage":"10Gi"},"allowedAgentLevels":["P0","P1"]}'
TARGET_ID=$(jqv '.data.id')
step_pass "创建部署目标（PROD，allowed 含 P1）targetId=$TARGET_ID"

api "/v1/api/projects/$PROJECT_ID/deploy-targets/attach" \
  "{\"targetId\":$TARGET_ID}"
step_pass "项目 attach 部署目标"

api /v1/api/artifacts/register \
  "{\"projectId\":$PROJECT_ID,\"agentId\":$AGENT_ID,\"agentVersion\":\"1.0.0\",\"codeCommit\":\"abc1234\",\"imageDigest\":\"sha256:0123456789abcdef\",\"configDigest\":\"sha256:fedcba9876543210\",\"evaluationRef\":\"MOCK-EVAL-001\"}"
ARTIFACT_ID=$(jqv '.data.id')
step_pass "制品登记（四字段填全）artifactId=$ARTIFACT_ID"

api /v1/api/releases/create \
  "{\"projectId\":$PROJECT_ID,\"agentId\":$AGENT_ID,\"agentVersion\":\"1.0.0\",\"artifactId\":$ARTIFACT_ID,\"targetId\":$TARGET_ID}"
RELEASE_ID=$(jqv '.data.id')
assert_jq '.data.status == "DRAFT"' "发布单初始应为 DRAFT"
step_pass "创建发布单 releaseId=$RELEASE_ID"

api /v1/api/releases/gate \
  "{\"projectId\":$PROJECT_ID,\"id\":$RELEASE_ID}"
assert_jq '.data.status == "GATED" and ((.data.gateResult | fromjson) as $g | $g.passed == true and ([$g.checks[] | .passed] | all))' \
  "gateResult 应全过"
step_pass "发布门禁 gate（断言 gateResult 全过）"

api /v1/api/releases/approve \
  "{\"projectId\":$PROJECT_ID,\"id\":$RELEASE_ID,\"approvalRef\":\"MOCK-APPROVAL-001\"}"
assert_jq '.data.status == "APPROVED" and .data.approvalRef == "MOCK-APPROVAL-001"' "审批后应为 APPROVED"
step_pass "发布审批 approve（approvalRef=MOCK-APPROVAL-001）"

api /v1/api/releases/deploy \
  "{\"projectId\":$PROJECT_ID,\"id\":$RELEASE_ID}"
assert_jq '.data.status == "RUNNING"' "部署后 Release 应为 RUNNING"
step_pass "发布部署 deploy（断言 Release RUNNING）"

api /v1/api/deployments/list \
  "{\"projectId\":$PROJECT_ID,\"releaseId\":$RELEASE_ID}"
assert_jq '.data | length >= 1' "应存在 Deployment"
DEPLOYMENT_ID=$(jqv '.data[0].id')
assert_jq '.data[0].status == "RUNNING" and (.data[0].instanceUrl | length > 0)' "Deployment 应为 RUNNING 且有 instanceUrl"
step_pass "断言 Deployment 存在且 RUNNING deploymentId=$DEPLOYMENT_ID"

# ---------- 07 服务治理 ----------

api /v1/api/routes/sync \
  "{\"projectId\":$PROJECT_ID,\"agentId\":$AGENT_ID,\"env\":\"PROD\",\"agentVersion\":\"1.0.0\"}"
assert_jq ".data.status == \"ACTIVE\" and .data.deploymentId == $DEPLOYMENT_ID" "路由应为 ACTIVE"
ROUTE_ID=$(jqv '.data.id')
step_pass "路由同步 routes/sync（断言 ACTIVE 路由生成）routeId=$ROUTE_ID"

api /v1/api/caller-policies/grant \
  "{\"projectId\":$PROJECT_ID,\"agentId\":$AGENT_ID,\"env\":\"PROD\",\"callerType\":\"USER\",\"callerId\":\"$TOKEN\"}"
step_pass "调用方策略授权 caller-policies/grant（USER/$TOKEN）"

# 运行时面：上游为本地桩伪地址（http://stub.local/{deploymentId}），预期 502/50201
api_raw "/v1/invoke/agent-smoke-001/PROD" '{"input":"smoke ping"}' \
  -H "X-Caller-Id: $TOKEN" -H "X-Caller-Type: USER"
invoke_body_code=$(jq -r '.code // "NO_CODE"' <<<"$RESP" 2>/dev/null || echo "PARSE_FAIL")
if [ "$HTTP_CODE" != "502" ] && [ "$invoke_body_code" != "50201" ]; then
  fail "invoke 应返回 HTTP 502 或 code=50201（实际 HTTP=$HTTP_CODE, code=$invoke_body_code）"
fi
# InvocationRecord 断言：治理模块仅经 InvocationRecordMapper 落库，管理面无查询接口
# （已核对 com.bosc.agentops 全部 controller，无 invocation 查询端点），按任务约定跳过该断言。
step_pass "统一调用入口 /v1/invoke（HTTP=$HTTP_CODE, code=$invoke_body_code，符合预期 502/50201；InvocationRecord 管理面无查询接口，跳过落库断言）"

api /v1/api/mcp-policies/grant \
  "{\"projectId\":$PROJECT_ID,\"agentId\":$AGENT_ID,\"assetId\":$MCP_TOOL_ASSET_ID,\"env\":\"PROD\",\"allowed\":true}"
step_pass "MCP 策略授权 mcp-policies/grant（MCP_TOOL，允许）"

api_raw /v1/mcp-check \
  "{\"agentId\":$AGENT_ID,\"toolAssetId\":$MCP_TOOL_ASSET_ID,\"env\":\"PROD\"}"
[ "$HTTP_CODE" = "200" ] || fail "mcp-check HTTP 状态非 200"
assert_jq '.code == 0 and .data.allowed == true' "已授权 tool 应 allowed=true"
step_pass "/v1/mcp-check 已授权 tool 断言 allowed=true"

api_raw /v1/mcp-check \
  "{\"agentId\":$AGENT_ID,\"toolAssetId\":$SKILL_ASSET_ID,\"env\":\"PROD\"}"
[ "$HTTP_CODE" = "200" ] || fail "mcp-check HTTP 状态非 200"
assert_jq '.code == 0 and .data.allowed == false' "未授权 tool 应 allowed=false（fail-closed）"
step_pass "/v1/mcp-check 未授权 tool 断言 allowed=false"

# ---------- 08 观测 ----------

api /v1/api/observability/audit/query \
  "{\"projectId\":$PROJECT_ID,\"limit\":500}"
assert_jq '([.data[].action] | index("agent.version.register") != null)
  and ([.data[].action] | index("release.approve") != null)
  and ([.data[].action] | index("route.sync") != null)
  and (.data | length >= 15)' "审计记录应包含 register/approve/sync 等写操作且数量充足"
step_pass "审计查询 audit/query（断言前述写操作有审计记录）"

api_raw /v1/alerts/webhook \
  "{\"source\":\"smoke-e2e\",\"alertKey\":\"smoke-alert-001\",\"agentId\":$AGENT_ID,\"level\":\"WARN\",\"title\":\"冒烟告警\",\"detail\":{\"metric\":\"cpu\",\"value\":95}}" \
  -H "X-Platform-Token: $MACHINE_TOKEN"
[ "$HTTP_CODE" = "200" ] || fail "alerts/webhook HTTP 状态非 200"
assert_jq '.code == 0 and .data.status == "FIRING"' "webhook  ingest 后告警应为 FIRING"
ALERT_ID=$(jqv '.data.id')
step_pass "告警 webhook 推送（机器 token）alertId=$ALERT_ID"

api /v1/api/observability/alerts/handle \
  "{\"projectId\":$PROJECT_ID,\"id\":$ALERT_ID,\"note\":\"冒烟测试处置\"}"
assert_jq '.data.status == "HANDLED"' "处置后告警应为 HANDLED"
step_pass "告警处置 alerts/handle（断言 HANDLED）"

api /v1/api/observability/components/register \
  "{\"projectId\":$PROJECT_ID,\"code\":\"COMP-SMOKE\",\"name\":\"冒烟组件\",\"type\":\"SERVICE\",\"ownerTeam\":\"smoke-team\",\"critical\":false,\"description\":\"无 healthEndpoint 的组件\"}"
COMPONENT_ID=$(jqv '.data.id')
step_pass "组件登记（不带 endpoint）componentId=$COMPONENT_ID"

api /v1/api/observability/components/health \
  "{\"projectId\":$PROJECT_ID}"
assert_jq '.data.unknown >= 1' "UNKNOWN 计数应 >= 1"
step_pass "组件健康聚合 components/health（断言 UNKNOWN 计数 >= 1）"

echo ""
echo "SMOKE E2E: ALL PASS"
