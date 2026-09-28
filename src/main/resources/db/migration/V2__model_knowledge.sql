-- 模块 03 模型与知识能力接入
-- DDL 使用 MySQL 8 兼容方言（开发/测试运行于 H2 MODE=MySQL）。主键统一采用自增 BIGINT。
-- 注意避开保留字：`type` 列改为 provider_type / kb_type，`scope` 列改为 grant_scope。

-- 模型 Provider 平台级目录。凭据只存引用（credential_ref），不出现明文密钥。
CREATE TABLE model_provider (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    code           VARCHAR(64)  NOT NULL,
    name           VARCHAR(128) NOT NULL,
    provider_type  VARCHAR(32)  NOT NULL,
    endpoint       VARCHAR(512),
    auth_type      VARCHAR(32)  NOT NULL,
    credential_ref VARCHAR(256),
    status         VARCHAR(16)  NOT NULL DEFAULT 'ENABLED',
    created_by     VARCHAR(64),
    created_at     DATETIME     NOT NULL,
    updated_at     DATETIME     NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_model_provider_code UNIQUE (code)
);

-- 模型服务目录：挂在某 Provider 下的具体模型
CREATE TABLE model_service (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    provider_id    BIGINT       NOT NULL,
    model_code     VARCHAR(128) NOT NULL,
    display_name   VARCHAR(128),
    capabilities   TEXT,
    default_params TEXT,
    status         VARCHAR(16)  NOT NULL DEFAULT 'ENABLED',
    created_by     VARCHAR(64),
    created_at     DATETIME     NOT NULL,
    updated_at     DATETIME     NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_model_service UNIQUE (provider_id, model_code)
);
CREATE INDEX idx_model_service_provider ON model_service (provider_id);

-- 项目级模型授权：param_policy 为允许覆盖的参数白名单/上下限（JSON）
CREATE TABLE project_model_grant (
    id               BIGINT   NOT NULL AUTO_INCREMENT,
    project_id       BIGINT   NOT NULL,
    model_service_id BIGINT   NOT NULL,
    param_policy     TEXT,
    granted_by       VARCHAR(64),
    created_at       DATETIME NOT NULL,
    updated_at       DATETIME NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_project_model_grant UNIQUE (project_id, model_service_id)
);
CREATE INDEX idx_project_model_grant_service ON project_model_grant (model_service_id);

-- 知识库平台级目录：kb_type 为行内系统标识；连接配置 JSON，不含明文密钥
CREATE TABLE knowledge_base (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    code              VARCHAR(64)  NOT NULL,
    name              VARCHAR(128) NOT NULL,
    kb_type           VARCHAR(64)  NOT NULL,
    endpoint          VARCHAR(512),
    connection_config TEXT,
    owner_id          VARCHAR(64),
    status            VARCHAR(16)  NOT NULL DEFAULT 'ENABLED',
    created_by        VARCHAR(64),
    created_at        DATETIME     NOT NULL,
    updated_at        DATETIME     NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_knowledge_base_code UNIQUE (code)
);

-- 项目级知识库授权：grant_scope 为授权范围（集合/标签过滤等，JSON）
CREATE TABLE knowledge_base_grant (
    id         BIGINT   NOT NULL AUTO_INCREMENT,
    kb_id      BIGINT   NOT NULL,
    project_id BIGINT   NOT NULL,
    grant_scope TEXT,
    granted_by VARCHAR(64),
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_knowledge_base_grant UNIQUE (kb_id, project_id)
);
CREATE INDEX idx_knowledge_base_grant_project ON knowledge_base_grant (project_id);

-- 知识库引用：保留知识库-项目-Agent 关联。agent_id 可空（空=项目级引用），
-- Agent 实体由模块 02 提供，本期为弱引用 Long。
CREATE TABLE knowledge_base_ref (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    kb_id       BIGINT      NOT NULL,
    project_id  BIGINT      NOT NULL,
    agent_id    BIGINT,
    ref_version VARCHAR(64),
    created_by  VARCHAR(64),
    created_at  DATETIME    NOT NULL,
    updated_at  DATETIME    NOT NULL,
    PRIMARY KEY (id)
);
CREATE INDEX idx_knowledge_base_ref_project ON knowledge_base_ref (project_id, agent_id);
CREATE INDEX idx_knowledge_base_ref_kb ON knowledge_base_ref (kb_id);

-- 模块 03 权限点角色映射：OWNER/ADMIN 全量，DEVELOPER read+grant，OPERATOR read
INSERT INTO role_permission (`role`, permission) VALUES
('OWNER', 'model:read'),
('OWNER', 'model:manage'),
('OWNER', 'model:grant'),
('OWNER', 'kb:read'),
('OWNER', 'kb:manage'),
('OWNER', 'kb:grant'),
('ADMIN', 'model:read'),
('ADMIN', 'model:manage'),
('ADMIN', 'model:grant'),
('ADMIN', 'kb:read'),
('ADMIN', 'kb:manage'),
('ADMIN', 'kb:grant'),
('DEVELOPER', 'model:read'),
('DEVELOPER', 'model:grant'),
('DEVELOPER', 'kb:read'),
('DEVELOPER', 'kb:grant'),
('OPERATOR', 'model:read'),
('OPERATOR', 'kb:read');
