-- 前端产品化改版支撑：Agent 广场可见性 + 平台管理员配置（管理员清单为配置项 agentops.auth.admins，无 DDL）
-- DDL 使用 MySQL 8 兼容方言（开发/测试运行于 H2 MODE=MySQL）。

-- Agent 发布可见性：PROJECT=仅所属项目可见（默认）；PUBLIC=出现在 Agent 广场（全平台可见）
ALTER TABLE agent ADD COLUMN visibility VARCHAR(16) NOT NULL DEFAULT 'PROJECT';
CREATE INDEX idx_agent_visibility ON agent (visibility, status);
