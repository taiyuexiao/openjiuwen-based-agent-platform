-- 模块 09 企业集成与行内规范（enterprise-integration）
-- DDL 使用 MySQL 8 兼容方言（开发/测试运行于 H2 MODE=MySQL）。主键统一采用自增 BIGINT。

-- Agent 与行内 CMDB 应用的归属绑定：一个 Agent 至多绑定一个主归属应用（agent_id 唯一）。
-- snapshot 为同步时刻的权威信息全量 JSON（appCode/appName/owner/bizDomain/appLevel），
-- 平台不提供手工编辑快照的接口；STALE 由读取/门禁按 synced_at + 阈值动态判定。
CREATE TABLE app_binding (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    agent_id      BIGINT       NOT NULL,
    app_code      VARCHAR(64)  NOT NULL,
    source_system VARCHAR(64)  NOT NULL,
    sync_status   VARCHAR(16)  NOT NULL DEFAULT 'UNVERIFIED',
    synced_at     DATETIME,
    snapshot      TEXT,
    bound_by      VARCHAR(64),
    created_at    DATETIME     NOT NULL,
    updated_at    DATETIME     NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_app_binding_agent UNIQUE (agent_id)
);
CREATE INDEX idx_app_binding_app_code ON app_binding (app_code);

-- ITSM 变更登记：本期仅登记与查询，change_no 全局唯一；流程审批对接后期。
CREATE TABLE itsm_change_record (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    change_no   VARCHAR(64)  NOT NULL,
    agent_id    BIGINT       NOT NULL,
    change_type VARCHAR(32)  NOT NULL,
    payload     TEXT,
    status      VARCHAR(16)  NOT NULL DEFAULT 'RECORDED',
    created_by  VARCHAR(64),
    created_at  DATETIME     NOT NULL,
    updated_at  DATETIME     NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_itsm_change_no UNIQUE (change_no)
);
CREATE INDEX idx_itsm_change_agent ON itsm_change_record (agent_id);

-- 模块 09 权限点角色映射：OWNER/ADMIN manage；DEVELOPER/OPERATOR read
INSERT INTO role_permission (`role`, permission) VALUES
('OWNER', 'binding:read'),
('OWNER', 'binding:manage'),
('ADMIN', 'binding:read'),
('ADMIN', 'binding:manage'),
('DEVELOPER', 'binding:read'),
('OPERATOR', 'binding:read');
