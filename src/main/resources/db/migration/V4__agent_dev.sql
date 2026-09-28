-- 模块 02 智能体开发与调试（agent-dev）
-- DDL 使用 MySQL 8 兼容方言（开发/测试运行于 H2 MODE=MySQL）。主键统一采用自增 BIGINT。
-- 注意避开保留字：状态列用 status，模式列用 access_mode。

-- Agent 主表：code 在项目内唯一。HOSTED（运行托管）模式必须记录运行入口与健康检查地址，
-- 平台仅承诺服务调用/生命周期/基础日志，不登记声明清单。
CREATE TABLE agent (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    code             VARCHAR(128) NOT NULL,
    name             VARCHAR(128) NOT NULL,
    project_id       BIGINT       NOT NULL,
    access_mode      VARCHAR(16)  NOT NULL DEFAULT 'NATIVE',
    runtime_endpoint VARCHAR(512),
    health_endpoint  VARCHAR(512),
    description      VARCHAR(1024),
    status           VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    created_by       VARCHAR(64),
    created_at       DATETIME     NOT NULL,
    updated_at       DATETIME     NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_agent_code UNIQUE (project_id, code)
);
CREATE INDEX idx_agent_project ON agent (project_id);

-- Agent 版本：REGISTERED 后 declaration 不可变，变更只能登记新版本。version 格式 x.y.z。
-- capability_degraded：ADAPTED 模式空声明登记时置真，表示平台管理能力降级。
CREATE TABLE agent_version (
    id                  BIGINT      NOT NULL AUTO_INCREMENT,
    agent_id            BIGINT      NOT NULL,
    version             VARCHAR(32) NOT NULL,
    declaration         TEXT,
    capability_degraded TINYINT(1)  NOT NULL DEFAULT 0,
    status              VARCHAR(16) NOT NULL DEFAULT 'REGISTERED',
    registered_by       VARCHAR(64),
    registered_at       DATETIME,
    created_by          VARCHAR(64),
    created_at          DATETIME    NOT NULL,
    updated_at          DATETIME    NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_agent_version UNIQUE (agent_id, version)
);
CREATE INDEX idx_agent_version_agent ON agent_version (agent_id);

-- 脚手架模板：静态预置（启动初始化写入），files 为 路径→内容模板 JSON
CREATE TABLE scaffold_template (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    code        VARCHAR(64)  NOT NULL,
    name        VARCHAR(128) NOT NULL,
    language    VARCHAR(32)  NOT NULL,
    description VARCHAR(1024),
    files       TEXT         NOT NULL,
    created_at  DATETIME     NOT NULL,
    updated_at  DATETIME     NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_scaffold_template_code UNIQUE (code)
);

-- 模块 02 权限点角色映射：OWNER/ADMIN 全量；DEVELOPER read+create+update+register；OPERATOR read
INSERT INTO role_permission (`role`, permission) VALUES
('OWNER', 'agent:read'),
('OWNER', 'agent:create'),
('OWNER', 'agent:update'),
('OWNER', 'agent:register'),
('OWNER', 'agent:archive'),
('ADMIN', 'agent:read'),
('ADMIN', 'agent:create'),
('ADMIN', 'agent:update'),
('ADMIN', 'agent:register'),
('ADMIN', 'agent:archive'),
('DEVELOPER', 'agent:read'),
('DEVELOPER', 'agent:create'),
('DEVELOPER', 'agent:update'),
('DEVELOPER', 'agent:register'),
('OPERATOR', 'agent:read');
