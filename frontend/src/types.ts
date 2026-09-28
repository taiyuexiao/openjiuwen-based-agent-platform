// 与后端 DTO/Entity 对应的类型定义（字段名严格来自 Java 代码）

// ---------- 通用 ----------
export interface ApiResponse<T> {
  code: number;
  message: string;
  data: T;
  requestId: string;
}

// ---------- 枚举 ----------
export type EnvType = 'DEV' | 'SIT' | 'UAT' | 'PROD';
export type ProjectStatus = 'ACTIVE' | 'ARCHIVED';
export type Role = 'OWNER' | 'ADMIN' | 'DEVELOPER' | 'OPERATOR';
export type SubjectType = 'USER' | 'GROUP';
export type ProviderType = 'OPENAI_COMPAT' | 'CUSTOM';
export type AuthType = 'CREDENTIAL_REF' | 'GATEWAY_PASSTHROUGH';
export type CatalogStatus = 'ENABLED' | 'DISABLED';
export type AssetType = 'SKILL' | 'MCP_SERVICE' | 'MCP_TOOL' | 'HTTP_API';
export type AssetStatus = 'DRAFT' | 'PUBLISHED' | 'OFFLINE';
export type AssetVersionStatus = 'DRAFT' | 'PUBLISHED' | 'OFFLINE';
export type AssetVisibility = 'PROJECT' | 'SHARED';
export type AccessMode = 'NATIVE' | 'ADAPTED' | 'HOSTED';
export type AgentStatus = 'ACTIVE' | 'ARCHIVED';
export type AgentVisibility = 'PROJECT' | 'PUBLIC';
export type AgentVersionStatus = 'DRAFT' | 'REGISTERED';
export type AgentLevelValue = 'P0' | 'P1' | 'P2' | 'P3';
export type DeployTargetStatus = 'ACTIVE' | 'DISABLED';
export type DeploymentStatus = 'PENDING' | 'RUNNING' | 'FAILED' | 'STOPPED';
export type DeployHealthStatus = 'HEALTHY' | 'UNHEALTHY' | 'UNKNOWN';
export type ReleaseStatus = 'DRAFT' | 'GATED' | 'APPROVED' | 'DEPLOYING' | 'RUNNING' | 'FAILED' | 'ROLLED_BACK';
export type SyncStatus = 'SYNCED' | 'STALE' | 'FAILED' | 'UNVERIFIED';
export type CallerType = 'USER' | 'HIAGENT' | 'AGENT';
export type PolicyStatus = 'ACTIVE' | 'REVOKED';
export type RouteStatus = 'ACTIVE' | 'DRAINED';
export type AlertStatus = 'FIRING' | 'HANDLED' | 'CLOSED';
export type ComponentType = 'DATABASE' | 'MQ' | 'CACHE' | 'SERVICE' | 'EXTERNAL';
export type ComponentHealthStatus = 'UP' | 'DOWN' | 'UNKNOWN';
export type SpanKind = 'LLM' | 'TOOL' | 'AGENT' | 'WORKFLOW' | 'OTHER';

// ---------- 项目 ----------
export interface Project {
  id: number;
  code: string;
  name: string;
  description?: string;
  ownerId: string;
  status: ProjectStatus;
  createdBy: string;
  createdAt: string;
  updatedAt: string;
}

export interface ProjectMember {
  id: number;
  projectId: number;
  subjectType: SubjectType;
  subjectId: string;
  role: Role;
  createdBy: string;
  createdAt: string;
  updatedAt: string;
}

export interface ProjectEnvironment {
  id: number;
  projectId: number;
  env: EnvType;
  resourceQuota?: string;
  createdAt: string;
  updatedAt: string;
}

export interface UserGroup {
  id: number;
  name: string;
  description?: string;
  ownerId: string;
  createdBy: string;
  createdAt: string;
  updatedAt: string;
}

export interface UserGroupMember {
  id: number;
  groupId: number;
  userId: string;
  createdAt: string;
}

// ---------- 模型与知识库 ----------
export interface ModelProvider {
  id: number;
  code: string;
  name: string;
  providerType: ProviderType;
  endpoint?: string;
  authType: AuthType;
  credentialRef?: string;
  status: CatalogStatus;
  createdBy: string;
  createdAt: string;
  updatedAt: string;
}

export interface ModelService {
  id: number;
  providerId: number;
  modelCode: string;
  displayName?: string;
  capabilities?: string;
  defaultParams?: string;
  status: CatalogStatus;
  createdBy: string;
  createdAt: string;
  updatedAt: string;
}

export interface KnowledgeBase {
  id: number;
  code: string;
  name: string;
  kbType: string;
  endpoint?: string;
  connectionConfig?: string;
  ownerId: string;
  status: CatalogStatus;
  createdBy: string;
  createdAt: string;
  updatedAt: string;
}

export interface ProjectModelGrant {
  id: number;
  projectId: number;
  modelServiceId: number;
  paramPolicy?: string;
  grantedBy: string;
  createdAt: string;
  updatedAt: string;
}

export interface EffectiveModel {
  grantId: number;
  modelServiceId: number;
  modelCode: string;
  displayName?: string;
  providerId: number;
  providerCode: string;
  providerType: string;
  endpoint?: string;
  authType: string;
  credentialRef?: string;
  capabilities?: unknown;
  effectiveParams?: Record<string, unknown>;
  removedKeys?: string[];
}

export interface KnowledgeBaseGrant {
  id: number;
  kbId: number;
  projectId: number;
  grantScope?: string;
  grantedBy: string;
  createdAt: string;
  updatedAt: string;
}

export interface KnowledgeBaseRef {
  id: number;
  kbId: number;
  projectId: number;
  agentId?: number;
  refVersion?: string;
  createdBy: string;
  createdAt: string;
  updatedAt: string;
}

// ---------- 资产 ----------
export interface Asset {
  id: number;
  code: string;
  name: string;
  assetType: AssetType;
  parentAssetId?: number;
  ownerProjectId: number;
  ownerUserId: string;
  visibility: AssetVisibility;
  status: AssetStatus;
  description?: string;
  createdBy: string;
  createdAt: string;
  updatedAt: string;
}

export interface AssetVersion {
  id: number;
  assetId: number;
  version: string;
  definition?: string;
  status: AssetVersionStatus;
  publishedBy: string;
  publishedAt: string;
  createdBy: string;
  createdAt: string;
  updatedAt: string;
}

export interface AssetGrant {
  id: number;
  assetId: number;
  toProjectId: number;
  grantedBy: string;
  createdAt: string;
  updatedAt: string;
}

export interface AssetReference {
  id: number;
  assetId: number;
  assetVersion: string;
  projectId: number;
  agentId?: number;
  createdBy: string;
  createdAt: string;
  updatedAt: string;
}

export interface ToolDraft {
  name: string;
  description?: string;
  method: string;
  path: string;
  parameters?: unknown;
  requestBody?: unknown;
}

export interface AssetUploadResp {
  assetId: number;
  assetCode: string;
  assetStatus: string;
  versionId: number;
  version: string;
  versionStatus: string;
  filename: string;
  size: number;
  sha256: string;
  contentType?: string;
}

// ---------- Agent ----------
export interface Agent {
  id: number;
  code: string;
  name: string;
  projectId: number;
  accessMode?: AccessMode;
  runtimeEndpoint?: string;
  healthEndpoint?: string;
  description?: string;
  status: AgentStatus;
  visibility?: AgentVisibility;
  createdBy: string;
  createdAt: string;
  updatedAt: string;
}

export interface AgentDetailResp {
  agent: Agent;
  capabilityNote?: string;
}

export interface AgentVersion {
  id: number;
  agentId: number;
  version: string;
  declaration?: string;
  capabilityDegraded?: boolean;
  status: AgentVersionStatus;
  registeredBy: string;
  registeredAt: string;
  createdBy: string;
  createdAt: string;
  updatedAt: string;
}

export interface AgentSquareItem {
  id: number;
  code: string;
  name: string;
  description?: string;
  projectId: number;
  projectName?: string;
  accessMode?: AccessMode;
  latestVersion?: string;
  versionCount: number;
  createdAt: string;
}

// ---------- 企业集成（CMDB / ITSM） ----------
export interface BindingResp {
  id: number;
  agentId: number;
  appCode: string;
  sourceSystem?: string;
  syncStatus: SyncStatus;
  effectiveSyncStatus?: SyncStatus;
  syncedAt?: string;
  snapshot?: string;
  boundBy: string;
  createdAt: string;
  warning?: string;
}

// ---------- 制品与发布 ----------
export interface Artifact {
  id: number;
  agentId: number;
  agentVersion: string;
  codeCommit?: string;
  imageDigest?: string;
  configDigest?: string;
  evaluationRef?: string;
  builtAt?: string;
  createdBy: string;
  createdAt: string;
  updatedAt: string;
}

export interface DeployTarget {
  id: number;
  code: string;
  name: string;
  env: EnvType;
  cluster: string;
  namespace: string;
  baseResource?: string;
  allowedAgentLevels?: string;
  status: DeployTargetStatus;
  createdBy: string;
  createdAt: string;
  updatedAt: string;
}

export interface ProjectTargetResp {
  id: number;
  projectId: number;
  targetId: number;
  isDefault: boolean;
  target?: DeployTarget;
}

export interface AgentLevel {
  id: number;
  agentId: number;
  level: AgentLevelValue;
  source?: string;
  effective?: boolean;
  confirmedBy: string;
  confirmedAt: string;
  createdBy: string;
  createdAt: string;
  updatedAt: string;
}

export interface Release {
  id: number;
  agentId: number;
  agentVersion: string;
  artifactId: number;
  targetId: number;
  levelSnapshot?: string;
  status: ReleaseStatus;
  gateResult?: string;
  approvalRef?: string;
  rollbackOf?: number;
  createdBy: string;
  createdAt: string;
  updatedAt: string;
}

export interface Deployment {
  id: number;
  releaseId: number;
  targetId: number;
  executor?: string;
  instanceUrl?: string;
  replicas?: number;
  status: DeploymentStatus;
  healthStatus?: DeployHealthStatus;
  startedAt?: string;
  createdBy: string;
  createdAt: string;
  updatedAt: string;
}

// ---------- 服务治理 ----------
export interface ServiceRoute {
  id: number;
  agentId: number;
  env: string;
  agentVersion: string;
  deploymentId?: number;
  routeRevision?: number;
  status: RouteStatus;
  createdBy: string;
  createdAt: string;
  updatedAt: string;
}

export interface CallerPolicy {
  id: number;
  callerType: CallerType;
  callerId: string;
  agentId: number;
  env: string;
  sharedToken?: string;
  rateLimitPerMin?: number;
  timeoutMs?: number;
  status: PolicyStatus;
  createdBy: string;
  createdAt: string;
  updatedAt: string;
}

export interface McpInvokePolicy {
  id: number;
  agentId: number;
  assetId: number;
  env: string;
  allowed: boolean;
  createdBy: string;
  createdAt: string;
  updatedAt: string;
}

export interface ServiceDirectoryEntry {
  agentId: number;
  agentCode: string;
  agentName: string;
  env: string;
  agentVersion: string;
  address?: string;
  authType?: string;
}

// ---------- 观测 ----------
export interface TraceSpan {
  id: number;
  traceId: string;
  spanId: string;
  parentSpanId?: string;
  agentId?: number;
  env?: string;
  spanName?: string;
  spanKind?: SpanKind;
  startTime?: number;
  endTime?: number;
  status?: string;
  attrs?: string;
  invocationId?: number;
  createdAt: string;
}

export interface AuditEvent {
  id: number;
  requestId: string;
  userId: string;
  module: string;
  action: string;
  resourceType?: string;
  resourceId?: string;
  detail?: string;
  result?: string;
  createdAt: string;
}

export interface AlertEvent {
  id: number;
  source?: string;
  alertKey?: string;
  agentId?: number;
  level?: string;
  title: string;
  detail?: string;
  status: AlertStatus;
  handleNote?: string;
  handledBy?: string;
  handledAt?: string;
  createdAt: string;
  updatedAt: string;
}

export interface ComponentRegistry {
  id: number;
  code: string;
  name: string;
  type: ComponentType;
  ownerTeam?: string;
  healthEndpoint?: string;
  critical?: boolean;
  deps?: string;
  description?: string;
  createdBy: string;
  createdAt: string;
  updatedAt: string;
}

export interface ComponentHealthItem {
  code: string;
  name: string;
  type: ComponentType;
  critical?: boolean;
  healthEndpoint?: string;
  status: ComponentHealthStatus;
  httpStatus?: number;
  latencyMs?: number;
  error?: string;
}

export interface ComponentHealthSummary {
  total: number;
  up: number;
  down: number;
  unknown: number;
  components: ComponentHealthItem[];
}

export interface DailyTrendItem {
  date: string;
  total: number;
  success: number;
  failed: number;
}

export interface TopAgentItem {
  agentId: number;
  calls: number;
}

export interface StatsOverviewResp {
  scope: string;
  projectId?: number;
  totalCalls: number;
  successRate: number;
  avgLatencyMs: number;
  dailyTrend: DailyTrendItem[];
  topAgents: TopAgentItem[];
  spanKindDist: Record<string, number>;
  alertOpenCount: number;
}

// ---------- 权限矩阵 ----------
export interface RolePermissions {
  role: Role;
  permissions: string[];
}

export interface PermissionMatrixResp {
  permissions: string[];
  roles: RolePermissions[];
}
