-- 模块 04 Skill 与 MCP 资产管理（asset-hub）
-- DDL 使用 MySQL 8 兼容方言（开发/测试运行于 H2 MODE=MySQL）。主键统一采用自增 BIGINT。
-- 注意避开保留字：类型列用 asset_type，不用 type。

-- 资产主表：SKILL / MCP_SERVICE / MCP_TOOL / HTTP_API 统一模型。
-- MCP_TOOL 经 parent_asset_id 归属某个 MCP_SERVICE；HTTP_API 为历史接口登记表，可转换为 MCP_TOOL。
CREATE TABLE asset (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    code             VARCHAR(128) NOT NULL,
    name             VARCHAR(128) NOT NULL,
    asset_type       VARCHAR(32)  NOT NULL,
    parent_asset_id  BIGINT,
    owner_project_id BIGINT       NOT NULL,
    owner_user_id    VARCHAR(64)  NOT NULL,
    visibility       VARCHAR(16)  NOT NULL DEFAULT 'PROJECT',
    status           VARCHAR(16)  NOT NULL DEFAULT 'DRAFT',
    description      VARCHAR(1024),
    created_by       VARCHAR(64),
    created_at       DATETIME     NOT NULL,
    updated_at       DATETIME     NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_asset_code UNIQUE (code)
);
CREATE INDEX idx_asset_owner_project ON asset (owner_project_id);
CREATE INDEX idx_asset_parent ON asset (parent_asset_id);

-- 资产版本：发布后 definition 不可变，修改只能发新版本。version 格式 x.y.z。
CREATE TABLE asset_version (
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    asset_id     BIGINT      NOT NULL,
    version      VARCHAR(32) NOT NULL,
    definition   TEXT,
    status       VARCHAR(16) NOT NULL DEFAULT 'PUBLISHED',
    published_by VARCHAR(64),
    published_at DATETIME,
    created_by   VARCHAR(64),
    created_at   DATETIME    NOT NULL,
    updated_at   DATETIME    NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_asset_version UNIQUE (asset_id, version)
);

-- 跨项目共享授权：visibility=SHARED 的资产经本表授权给其他项目后才可被其引用
CREATE TABLE asset_grant (
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    asset_id      BIGINT      NOT NULL,
    to_project_id BIGINT      NOT NULL,
    granted_by    VARCHAR(64),
    created_at    DATETIME    NOT NULL,
    updated_at    DATETIME    NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_asset_grant UNIQUE (asset_id, to_project_id)
);
CREATE INDEX idx_asset_grant_project ON asset_grant (to_project_id);

-- 资产引用：记录「哪个项目/Agent 在用哪个资产的哪个版本」，供下线影响分析与追溯。
-- agent_id 可空（空=项目级引用）；Agent 实体由模块 02 提供，本期为弱引用 Long。
CREATE TABLE asset_reference (
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    asset_id      BIGINT      NOT NULL,
    asset_version VARCHAR(32) NOT NULL,
    project_id    BIGINT      NOT NULL,
    agent_id      BIGINT,
    created_by    VARCHAR(64),
    created_at    DATETIME    NOT NULL,
    updated_at    DATETIME    NOT NULL,
    PRIMARY KEY (id)
);
CREATE INDEX idx_asset_reference_asset ON asset_reference (asset_id);
CREATE INDEX idx_asset_reference_project ON asset_reference (project_id, agent_id);

-- 模块 04 权限点角色映射：OWNER/ADMIN 全量；DEVELOPER read+create+update+publish+reference；OPERATOR read
INSERT INTO role_permission (`role`, permission) VALUES
('OWNER', 'asset:read'),
('OWNER', 'asset:create'),
('OWNER', 'asset:update'),
('OWNER', 'asset:publish'),
('OWNER', 'asset:offline'),
('OWNER', 'asset:grant'),
('OWNER', 'asset:reference'),
('ADMIN', 'asset:read'),
('ADMIN', 'asset:create'),
('ADMIN', 'asset:update'),
('ADMIN', 'asset:publish'),
('ADMIN', 'asset:offline'),
('ADMIN', 'asset:grant'),
('ADMIN', 'asset:reference'),
('DEVELOPER', 'asset:read'),
('DEVELOPER', 'asset:create'),
('DEVELOPER', 'asset:update'),
('DEVELOPER', 'asset:publish'),
('DEVELOPER', 'asset:reference'),
('OPERATOR', 'asset:read');
