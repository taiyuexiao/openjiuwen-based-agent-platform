-- 模块 07 Agent 服务发布与访问治理（governance）
-- DDL 使用 MySQL 8 兼容方言（开发/测试运行于 H2 MODE=MySQL）。主键统一采用自增 BIGINT。
-- 表名统一加 gov_ 前缀，避开 route/policy 等词在多库方言中的歧义。

-- 服务路由：按 Agent + 环境 + 版本路由到 06 的部署实例。
-- route_revision 在 (agent_id, env) 内递增；同 agent+env 仅一条 ACTIVE，新路由上位时旧路由置 DRAINED。
-- deployment_id 唯一约束保证幂等：同一部署实例重复 sync 不产生新路由。
CREATE TABLE gov_service_route (
    id             BIGINT      NOT NULL AUTO_INCREMENT,
    agent_id       BIGINT      NOT NULL,
    env            VARCHAR(16) NOT NULL,
    agent_version  VARCHAR(32) NOT NULL,
    deployment_id  BIGINT      NOT NULL,
    route_revision INT         NOT NULL,
    status         VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_by     VARCHAR(64),
    created_at     DATETIME    NOT NULL,
    updated_at     DATETIME    NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_gov_route_deployment UNIQUE (deployment_id)
);
CREATE INDEX idx_gov_route_agent_env ON gov_service_route (agent_id, env, status);

-- 调用方策略：调用方（用户/HiAgent/Agent）访问 Agent 的授权与流控。
-- shared_token 非空时，运行时调用必须携带匹配的 Authorization: Bearer <token>（机器身份共享密钥）。
CREATE TABLE gov_caller_policy (
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    caller_type        VARCHAR(16)  NOT NULL,
    caller_id          VARCHAR(64)  NOT NULL,
    agent_id           BIGINT       NOT NULL,
    env                VARCHAR(16)  NOT NULL,
    shared_token       VARCHAR(128),
    rate_limit_per_min INT          NOT NULL DEFAULT 600,
    timeout_ms         INT          NOT NULL DEFAULT 30000,
    status             VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    created_by         VARCHAR(64),
    created_at         DATETIME     NOT NULL,
    updated_at         DATETIME     NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_gov_caller UNIQUE (caller_type, caller_id, agent_id, env)
);
CREATE INDEX idx_gov_caller_agent ON gov_caller_policy (agent_id, env, status);

-- MCP 调用策略：Agent → Tool 资产的运行时权限（fail-closed，无记录即拒绝）。
CREATE TABLE gov_mcp_invoke_policy (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    agent_id   BIGINT      NOT NULL,
    asset_id   BIGINT      NOT NULL,
    env        VARCHAR(16) NOT NULL,
    allowed    TINYINT(1)  NOT NULL,
    created_by VARCHAR(64),
    created_at DATETIME    NOT NULL,
    updated_at DATETIME    NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_gov_mcp_policy UNIQUE (agent_id, asset_id, env)
);

-- 访问记录：运行时面统一写入（不走 AuditService），是 08 观测模块查询的数据源。
-- status：SUCCESS / FAILED / REJECTED（鉴权、限流、路由缺失等前置拒绝）。
CREATE TABLE gov_invocation_record (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    trace_id      VARCHAR(64)  NOT NULL,
    caller_type   VARCHAR(16),
    caller_id     VARCHAR(64),
    agent_id      BIGINT,
    env           VARCHAR(16),
    route_id      BIGINT,
    agent_version VARCHAR(32),
    status        VARCHAR(16)  NOT NULL,
    latency_ms    BIGINT,
    error         VARCHAR(1024),
    created_at    DATETIME     NOT NULL,
    PRIMARY KEY (id)
);
CREATE INDEX idx_gov_invocation_agent ON gov_invocation_record (agent_id, env, created_at);
CREATE INDEX idx_gov_invocation_trace ON gov_invocation_record (trace_id);

-- 模块 07 权限点角色映射：OWNER/ADMIN 全量；DEVELOPER read；OPERATOR read+route（运维执行路由同步）
INSERT INTO role_permission (`role`, permission) VALUES
('OWNER', 'gov:read'),
('OWNER', 'gov:route'),
('OWNER', 'gov:policy'),
('ADMIN', 'gov:read'),
('ADMIN', 'gov:route'),
('ADMIN', 'gov:policy'),
('DEVELOPER', 'gov:read'),
('OPERATOR', 'gov:read'),
('OPERATOR', 'gov:route');
