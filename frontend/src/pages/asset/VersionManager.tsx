import { useCallback, useEffect, useState } from 'react';
import { colors } from '../../theme';
import { Alert, Button, Card, Col, Empty, List, Row, Select, Space, Tag, Timeline, Typography, message } from 'antd';
import { ReloadOutlined } from '@ant-design/icons';
import { api } from '../../api/client';
import JsonView from '../../components/JsonView';
import PageHeader from '../../components/PageHeader';
import ProjectSelect from '../../components/ProjectSelect';
import { useProject } from '../../context/ProjectContext';
import type { Agent, AgentVersion, Asset, AssetType, AssetVersion } from '../../types';
import { fmtTime } from '../../utils/format';

type OwnerType = 'SKILL' | 'MCP_SERVICE' | 'MCP_TOOL' | 'AGENT';

interface VersionRow {
  id: number;
  version: string;
  status: string;
  time?: string;
  by?: string;
  definition?: string;
  degraded?: boolean;
}

/** 版本管理统一页：左侧类型切换（SKILL/AGENT/MCP_SERVICE/MCP_TOOL），右侧版本 Timeline */
export default function VersionManager() {
  const { projectId } = useProject();
  const [ownerType, setOwnerType] = useState<OwnerType>('SKILL');
  const [owners, setOwners] = useState<{ id: number; label: string }[]>([]);
  const [ownerId, setOwnerId] = useState<number | undefined>();
  const [versions, setVersions] = useState<VersionRow[]>([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    setOwnerId(undefined);
    setVersions([]);
    if (!projectId) {
      setOwners([]);
      return;
    }
    if (ownerType === 'AGENT') {
      api<Agent[]>('/v1/api/agents/list', { projectId })
        .then((list) => setOwners(list.map((a) => ({ id: a.id, label: `${a.code}（${a.name}）` }))))
        .catch(() => undefined);
    } else {
      api<Asset[]>('/v1/api/assets/list', { projectId, assetType: ownerType as AssetType })
        .then((list) => setOwners(list.map((a) => ({ id: a.id, label: `${a.code}（${a.name}）` }))))
        .catch(() => undefined);
    }
  }, [projectId, ownerType]);

  const loadVersions = useCallback(async () => {
    if (!projectId || !ownerId) {
      setVersions([]);
      return;
    }
    setLoading(true);
    try {
      if (ownerType === 'AGENT') {
        const list = await api<AgentVersion[]>(`/v1/api/agents/${ownerId}/versions/list`, { projectId });
        setVersions(
          list.map((v) => ({
            id: v.id,
            version: v.version,
            status: v.status,
            time: v.registeredAt,
            by: v.registeredBy,
            definition: v.declaration,
            degraded: v.capabilityDegraded,
          })),
        );
      } else {
        const list = await api<AssetVersion[]>(`/v1/api/assets/${ownerId}/versions/list`, { projectId });
        setVersions(
          list.map((v) => ({
            id: v.id,
            version: v.version,
            status: v.status,
            time: v.publishedAt ?? v.createdAt,
            by: v.publishedBy ?? v.createdBy,
            definition: v.definition,
          })),
        );
      }
    } catch {
      // helper 已提示
    } finally {
      setLoading(false);
    }
  }, [projectId, ownerId, ownerType]);

  useEffect(() => {
    loadVersions();
  }, [loadVersions]);

  const publishDraft = async (version: string) => {
    if (!projectId || !ownerId) return;
    await api('/v1/api/assets/versions/publish-draft', { assetId: ownerId, version, projectId });
    message.success(`版本 ${version} 已发布`);
    loadVersions();
  };

  const statusColor = (s: string) =>
    s === 'PUBLISHED' || s === 'REGISTERED' ? 'green' : s === 'DRAFT' ? 'orange' : 'red';

  return (
    <>
      <PageHeader
        title="版本管理"
        subTitle="统一查看各类型对象的版本时间线；资产 DRAFT 版本可在此一键发布"
        extra={
          <>
            <ProjectSelect />
            <Button icon={<ReloadOutlined />} onClick={loadVersions} disabled={!ownerId}>
              刷新
            </Button>
          </>
        }
      />
      {!projectId && <Alert style={{ marginBottom: 16 }} type="info" showIcon message="请先在顶栏选择项目" />}
      <Row gutter={16}>
        <Col xs={24} lg={7}>
          <Card title="对象类型" className="soft-card">
            <List
              size="small"
              dataSource={[
                { key: 'SKILL', label: 'SKILL（技能）' },
                { key: 'AGENT', label: 'AGENT（智能体）' },
                { key: 'MCP_SERVICE', label: 'MCP_SERVICE（MCP 服务）' },
                { key: 'MCP_TOOL', label: 'MCP_TOOL（MCP 工具）' },
              ]}
              renderItem={(item) => (
                <List.Item
                  onClick={() => setOwnerType(item.key as OwnerType)}
                  style={{
                    cursor: 'pointer',
                    padding: '10px 12px',
                    borderRadius: 8,
                    background: ownerType === item.key ? colors.brandSoft : undefined,
                    color: ownerType === item.key ? colors.brand : undefined,
                    fontWeight: ownerType === item.key ? 600 : 400,
                  }}
                >
                  {item.label}
                </List.Item>
              )}
            />
            <div style={{ marginTop: 16 }}>
              <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                选择{ownerType === 'AGENT' ? ' Agent' : '资产'}：
              </Typography.Text>
              <Select
                style={{ width: '100%', marginTop: 8 }}
                placeholder={ownerType === 'AGENT' ? '选择 Agent' : '选择资产'}
                value={ownerId}
                onChange={setOwnerId}
                showSearch
                optionFilterProp="label"
                disabled={!projectId}
                options={owners}
              />
            </div>
          </Card>
        </Col>
        <Col xs={24} lg={17}>
          <Card title="版本时间线" className="soft-card" loading={loading}>
            {!ownerId ? (
              <Empty description="请选择左侧对象查看版本" />
            ) : versions.length === 0 ? (
              <Empty description="暂无版本" />
            ) : (
              <Timeline
                style={{ marginTop: 8 }}
                items={versions.map((v) => ({
                  color: statusColor(v.status),
                  children: (
                    <Space direction="vertical" size={2}>
                      <Space>
                        <Typography.Text strong style={{ fontFamily: 'monospace' }}>
                          v{v.version}
                        </Typography.Text>
                        <Tag color={statusColor(v.status)}>{v.status}</Tag>
                        {v.degraded && <Tag color="orange">能力降级</Tag>}
                        {ownerType !== 'AGENT' && v.status === 'DRAFT' && (
                          <Button size="small" type="primary" ghost onClick={() => publishDraft(v.version)}>
                            发布草稿
                          </Button>
                        )}
                      </Space>
                      <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                        {fmtTime(v.time)} {v.by ? `· ${v.by}` : ''}
                      </Typography.Text>
                      {v.definition && <JsonView value={v.definition} title={`版本 ${v.version} 内容`} />}
                    </Space>
                  ),
                }))}
              />
            )}
          </Card>
        </Col>
      </Row>
    </>
  );
}
