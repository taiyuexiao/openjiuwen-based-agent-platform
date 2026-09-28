-- 模块 08 运行观测与运维保障（observability）
-- DDL 使用 MySQL 8 兼容方言（开发/测试运行于 H2 MODE=MySQL）。主键统一采用自增 BIGINT。

-- 调用链 span：OTLP 接收端点落库的最小副本（查询加速与评测留痕），不追求完整链路存储。
-- start_time/end_time 为 Unix 纳秒（OTLP startTimeUnixNano 原值），避免时区与精度损失。
-- attrs 为脱敏后的 span attributes JSON；invocation_id 为根 span 补记关联的 07 调用记录。
CREATE TABLE trace_span (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    trace_id        VARCHAR(64)  NOT NULL,
    span_id         VARCHAR(64)  NOT NULL,
    parent_span_id  VARCHAR(64),
    agent_id        BIGINT       NOT NULL,
    env             VARCHAR(16)  NOT NULL DEFAULT 'DEV',
    span_name       VARCHAR(256),
    span_kind       VARCHAR(16)  NOT NULL DEFAULT 'OTHER',
    start_time      BIGINT,
    end_time        BIGINT,
    status          VARCHAR(16),
    attrs           TEXT,
    invocation_id   BIGINT,
    created_at      DATETIME     NOT NULL,
    PRIMARY KEY (id)
);
CREATE INDEX idx_trace_span_trace ON trace_span (trace_id);
CREATE INDEX idx_trace_span_agent ON trace_span (agent_id, env, created_at);

-- 告警事件：行内告警平台 webhook 推送落库；handle 登记处置说明后置 HANDLED。
CREATE TABLE alert_event (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    source      VARCHAR(64),
    alert_key   VARCHAR(128),
    agent_id    BIGINT,
    level       VARCHAR(16),
    title       VARCHAR(256)   NOT NULL,
    detail      TEXT,
    status      VARCHAR(16)    NOT NULL DEFAULT 'FIRING',
    handle_note VARCHAR(1024),
    handled_by  VARCHAR(64),
    handled_at  DATETIME,
    created_at  DATETIME       NOT NULL,
    updated_at  DATETIME       NOT NULL,
    PRIMARY KEY (id)
);
CREATE INDEX idx_alert_event_status ON alert_event (status, created_at);
CREATE INDEX idx_alert_event_agent ON alert_event (agent_id);

-- 平台核心组件清单：deps 存依赖 code 列表 JSON（只存不校验）；health 聚合逐探 health_endpoint。
CREATE TABLE component_registry (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    code            VARCHAR(64)  NOT NULL,
    name            VARCHAR(128) NOT NULL,
    type            VARCHAR(16)  NOT NULL,
    owner_team      VARCHAR(64),
    health_endpoint VARCHAR(512),
    critical        TINYINT(1)   NOT NULL DEFAULT 0,
    deps            TEXT,
    description     VARCHAR(1024),
    created_by      VARCHAR(64),
    created_at      DATETIME     NOT NULL,
    updated_at      DATETIME     NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_component_code UNIQUE (code)
);

-- 模块 08 权限点角色映射：OWNER/ADMIN 全量；DEVELOPER read；OPERATOR read+alert+action+component
INSERT INTO role_permission (`role`, permission) VALUES
('OWNER', 'obs:read'),
('OWNER', 'obs:alert'),
('OWNER', 'obs:action'),
('OWNER', 'obs:component'),
('ADMIN', 'obs:read'),
('ADMIN', 'obs:alert'),
('ADMIN', 'obs:action'),
('ADMIN', 'obs:component'),
('DEVELOPER', 'obs:read'),
('OPERATOR', 'obs:read'),
('OPERATOR', 'obs:alert'),
('OPERATOR', 'obs:action'),
('OPERATOR', 'obs:component');
