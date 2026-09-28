-- 模块 06 制品交付与托管运行（delivery）
-- DDL 使用 MySQL 8 兼容方言（开发/测试运行于 H2 MODE=MySQL）。主键统一采用自增 BIGINT。
-- 表名统一加 delivery_ 前缀：release/deployment 等词在多库方言中易歧义，前缀化防冲突。

-- 制品：不可变，(agent_id, agent_version) 唯一（一个版本对应一个固化制品，重建需新版本）。
-- evaluation_ref 为评测报告关联标识（占位，评测平台对接后校验其状态）。
CREATE TABLE delivery_artifact (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    agent_id       BIGINT       NOT NULL,
    agent_version  VARCHAR(32)  NOT NULL,
    code_commit    VARCHAR(64),
    image_digest   VARCHAR(256),
    config_digest  VARCHAR(128),
    evaluation_ref VARCHAR(128),
    built_at       DATETIME,
    created_by     VARCHAR(64),
    created_at     DATETIME     NOT NULL,
    updated_at     DATETIME     NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_delivery_artifact_version UNIQUE (agent_id, agent_version)
);
CREATE INDEX idx_delivery_artifact_agent ON delivery_artifact (agent_id);

-- 部署目标：平台级资源（环境 + 集群 + 命名空间 + 基础资源配置 + 可部署 Agent 等级）。
-- base_resource / allowed_agent_levels 为 JSON 文本。
CREATE TABLE delivery_deploy_target (
    id                   BIGINT       NOT NULL AUTO_INCREMENT,
    code                 VARCHAR(64)  NOT NULL,
    name                 VARCHAR(128) NOT NULL,
    env                  VARCHAR(8)   NOT NULL,
    cluster              VARCHAR(64)  NOT NULL,
    namespace            VARCHAR(64)  NOT NULL,
    base_resource        TEXT,
    allowed_agent_levels TEXT,
    status               VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    created_by           VARCHAR(64),
    created_at           DATETIME     NOT NULL,
    updated_at           DATETIME     NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_delivery_target_code UNIQUE (code)
);

-- 项目可选/默认部署目标
CREATE TABLE delivery_project_deploy_target (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    project_id BIGINT      NOT NULL,
    target_id  BIGINT      NOT NULL,
    is_default TINYINT(1)  NOT NULL DEFAULT 0,
    created_by VARCHAR(64),
    created_at DATETIME    NOT NULL,
    updated_at DATETIME    NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_delivery_project_target UNIQUE (project_id, target_id)
);
CREATE INDEX idx_delivery_project_target_project ON delivery_project_deploy_target (project_id);

-- Agent 分级结论（外部评审形成，平台记录/校验/应用）。level_value 避开 level 关键字歧义。
-- effective=1 为当前生效结论；confirm 新等级时旧记录自动失效。
CREATE TABLE delivery_agent_level (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    agent_id     BIGINT       NOT NULL,
    level_value  VARCHAR(4)   NOT NULL,
    source       VARCHAR(256),
    effective    TINYINT(1)   NOT NULL DEFAULT 1,
    confirmed_by VARCHAR(64),
    confirmed_at DATETIME,
    created_by   VARCHAR(64),
    created_at   DATETIME     NOT NULL,
    updated_at   DATETIME     NOT NULL,
    PRIMARY KEY (id)
);
CREATE INDEX idx_delivery_agent_level_agent ON delivery_agent_level (agent_id, effective);

-- 发布单：状态机 DRAFT → GATED → APPROVED → DEPLOYING → RUNNING；失败 FAILED；回滚 ROLLED_BACK。
-- gate_result 为门禁四项检查聚合 JSON；approval_ref 为行内审批单号（占位）；rollback_of 指向被回滚的发布单。
CREATE TABLE delivery_release (
    id             BIGINT      NOT NULL AUTO_INCREMENT,
    agent_id       BIGINT      NOT NULL,
    agent_version  VARCHAR(32) NOT NULL,
    artifact_id    BIGINT      NOT NULL,
    target_id      BIGINT      NOT NULL,
    level_snapshot VARCHAR(4),
    status         VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    gate_result    TEXT,
    approval_ref   VARCHAR(64),
    rollback_of    BIGINT,
    created_by     VARCHAR(64),
    created_at     DATETIME    NOT NULL,
    updated_at     DATETIME    NOT NULL,
    PRIMARY KEY (id)
);
CREATE INDEX idx_delivery_release_agent ON delivery_release (agent_id, status);

-- 部署实例：executor 记录执行通道（LOCAL_STUB/OPENJIUWEN_RUNTIME/HOSTED_EXTERNAL），
-- health_status：HEALTHY/UNHEALTHY/UNKNOWN（HOSTED 外部托管健康状态平台不可知，标记 UNKNOWN）。
CREATE TABLE delivery_deployment (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    release_id    BIGINT       NOT NULL,
    target_id     BIGINT       NOT NULL,
    executor      VARCHAR(32)  NOT NULL,
    instance_url  VARCHAR(512),
    replicas      INT,
    status        VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    health_status VARCHAR(16)  NOT NULL DEFAULT 'UNKNOWN',
    started_at    DATETIME,
    created_by    VARCHAR(64),
    created_at    DATETIME     NOT NULL,
    updated_at    DATETIME     NOT NULL,
    PRIMARY KEY (id)
);
CREATE INDEX idx_delivery_deployment_release ON delivery_deployment (release_id);

-- 模块 06 权限点角色映射：OWNER 全量；ADMIN 除 approve 外全量（拟办分离）；
-- DEVELOPER read+artifact+release；OPERATOR read+deploy（运维执行）
INSERT INTO role_permission (`role`, permission) VALUES
('OWNER', 'delivery:read'),
('OWNER', 'delivery:artifact'),
('OWNER', 'delivery:target'),
('OWNER', 'delivery:release'),
('OWNER', 'delivery:approve'),
('OWNER', 'delivery:deploy'),
('ADMIN', 'delivery:read'),
('ADMIN', 'delivery:artifact'),
('ADMIN', 'delivery:target'),
('ADMIN', 'delivery:release'),
('ADMIN', 'delivery:deploy'),
('DEVELOPER', 'delivery:read'),
('DEVELOPER', 'delivery:artifact'),
('DEVELOPER', 'delivery:release'),
('OPERATOR', 'delivery:read'),
('OPERATOR', 'delivery:deploy');
