-- 模块 01 项目与研发环境 + foundation 审计/权限映射
-- DDL 使用 MySQL 8 兼容方言（开发/测试运行于 H2 MODE=MySQL）。主键统一采用自增 BIGINT。

CREATE TABLE project (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    code        VARCHAR(64)  NOT NULL,
    name        VARCHAR(128) NOT NULL,
    description VARCHAR(1024),
    owner_id    VARCHAR(64)  NOT NULL,
    status      VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    created_by  VARCHAR(64),
    created_at  DATETIME     NOT NULL,
    updated_at  DATETIME     NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_project_code UNIQUE (code)
);

CREATE TABLE project_environment (
    id             BIGINT      NOT NULL AUTO_INCREMENT,
    project_id     BIGINT      NOT NULL,
    env            VARCHAR(16) NOT NULL,
    resource_quota TEXT,
    created_at     DATETIME    NOT NULL,
    updated_at     DATETIME    NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_project_env UNIQUE (project_id, env)
);
CREATE INDEX idx_project_env_project ON project_environment (project_id);

CREATE TABLE project_member (
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    project_id   BIGINT      NOT NULL,
    subject_type VARCHAR(8)  NOT NULL,
    subject_id   VARCHAR(64) NOT NULL,
    `role`       VARCHAR(16) NOT NULL,
    created_by   VARCHAR(64),
    created_at   DATETIME    NOT NULL,
    updated_at   DATETIME    NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_project_member UNIQUE (project_id, subject_type, subject_id)
);
CREATE INDEX idx_project_member_subject ON project_member (subject_type, subject_id);

CREATE TABLE user_group (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    name        VARCHAR(128) NOT NULL,
    description VARCHAR(1024),
    owner_id    VARCHAR(64)  NOT NULL,
    created_by  VARCHAR(64),
    created_at  DATETIME     NOT NULL,
    updated_at  DATETIME     NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_user_group_name UNIQUE (name)
);

CREATE TABLE user_group_member (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    group_id   BIGINT      NOT NULL,
    user_id    VARCHAR(64) NOT NULL,
    created_at DATETIME    NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_group_member UNIQUE (group_id, user_id)
);
CREATE INDEX idx_group_member_user ON user_group_member (user_id);

CREATE TABLE role_permission (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    `role`     VARCHAR(16) NOT NULL,
    permission VARCHAR(64) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_role_permission UNIQUE (`role`, permission)
);

CREATE TABLE audit_event (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    request_id    VARCHAR(64),
    user_id       VARCHAR(64),
    module        VARCHAR(64)  NOT NULL,
    action        VARCHAR(64)  NOT NULL,
    resource_type VARCHAR(64),
    resource_id   VARCHAR(64),
    detail        TEXT,
    result        VARCHAR(16)  NOT NULL,
    created_at    DATETIME     NOT NULL,
    PRIMARY KEY (id)
);
CREATE INDEX idx_audit_event_request ON audit_event (request_id);
CREATE INDEX idx_audit_event_resource ON audit_event (resource_type, resource_id);

-- 四角色默认角色-权限映射（可配置，后续可由管理界面维护）
INSERT INTO role_permission (`role`, permission) VALUES
('OWNER', 'project:read'),
('OWNER', 'project:update'),
('OWNER', 'project:archive'),
('OWNER', 'project:member:manage'),
('OWNER', 'project:env:manage'),
('ADMIN', 'project:read'),
('ADMIN', 'project:update'),
('ADMIN', 'project:archive'),
('ADMIN', 'project:member:manage'),
('ADMIN', 'project:env:manage'),
('DEVELOPER', 'project:read'),
('DEVELOPER', 'project:update'),
('OPERATOR', 'project:read');
