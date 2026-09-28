import { useCallback, useEffect, useState } from 'react';
import { colors } from '../../theme';
import {
  Alert,
  Button,
  Card,
  Descriptions,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Space,
  Tabs,
  Table,
  Tag,
  Typography,
  message,
} from 'antd';
import { ArrowLeftOutlined, ReloadOutlined } from '@ant-design/icons';
import { useNavigate, useParams, useSearchParams } from 'react-router-dom';
import type { ColumnsType } from 'antd/es/table';
import { api } from '../../api/client';
import PageHeader from '../../components/PageHeader';
import StatusBadge from '../../components/StatusBadge';
import JsonTextArea, { jsonRule } from '../../components/JsonTextArea';
import JsonView from '../../components/JsonView';
import type { Agent, AgentDetailResp, AgentVersion, BindingResp } from '../../types';
import { fmtTime, parseJson, prettyJson } from '../../utils/format';

const DECLARATION_PLACEHOLDER = `{
  "model": {
    "modelCode": "gpt-4o-mini",
    "params": { "temperature": 0.7 }
  },
  "skills": [
    { "assetId": 1, "version": "1.0.0" }
  ],
  "mcpTools": [
    { "assetId": 2, "version": "1.0.0" }
  ],
  "knowledgeBases": [
    { "kbId": 1, "refVersion": "v1" }
  ],
  "prompt": { "system": "你是信贷审批助手" },
  "memory": { "type": "window", "size": 20 }
}`;

const SYNC_COLORS: Record<string, string> = {
  SYNCED: 'green',
  STALE: 'orange',
  FAILED: 'red',
  UNVERIFIED: 'default',
};

/** Agent 详情：基本信息 + 版本管理 + CMDB 归属绑定 */
export default function AgentDetail() {
  const { id } = useParams<{ id: string }>();
  const agentId = Number(id);
  const [searchParams] = useSearchParams();
  const [projectId, setProjectId] = useState<number | undefined>(() => {
    const p = Number(searchParams.get('projectId'));
    return Number.isFinite(p) && p > 0 ? p : undefined;
  });
  const navigate = useNavigate();

  const [detail, setDetail] = useState<AgentDetailResp | null>(null);
  const [versions, setVersions] = useState<AgentVersion[]>([]);
  const [bindings, setBindings] = useState<BindingResp[]>([]);
  const [loading, setLoading] = useState(false);

  const [registerOpen, setRegisterOpen] = useState(false);
  const [bindOpen, setBindOpen] = useState(false);
  const [registerForm] = Form.useForm<{ version: string; declaration: string }>();
  const [bindForm] = Form.useForm<{ appCode: string }>();

  const load = useCallback(async () => {
    if (!projectId) return;
    setLoading(true);
    try {
      const [d, v, b] = await Promise.all([
        api<AgentDetailResp>('/v1/api/agents/detail', { id: agentId, projectId }),
        api<AgentVersion[]>(`/v1/api/agents/${agentId}/versions/list`, { projectId }),
        api<BindingResp[]>('/v1/api/bindings/list', { projectId, agentId }),
      ]);
      setDetail(d);
      setVersions(v);
      setBindings(b);
    } catch {
      // helper 已提示
    } finally {
      setLoading(false);
    }
  }, [agentId, projectId]);

  useEffect(() => {
    load();
  }, [load]);

  const registerVersion = async () => {
    const values = await registerForm.validateFields();
    await api(`/v1/api/agents/${agentId}/versions/register`, {
      projectId,
      version: values.version,
      declaration: values.declaration?.trim() ? parseJson(values.declaration) : undefined,
    });
    message.success('版本登记成功');
    setRegisterOpen(false);
    registerForm.resetFields();
    load();
  };

  const bind = async () => {
    const values = await bindForm.validateFields();
    await api('/v1/api/bindings/bind', { projectId, agentId, appCode: values.appCode });
    message.success('绑定成功');
    setBindOpen(false);
    bindForm.resetFields();
    load();
  };

  const setPublic = async (isPublic: boolean) => {
    await api<Agent>(`/v1/api/agents/${isPublic ? 'publish' : 'unpublish'}`, { id: agentId, projectId });
    message.success(isPublic ? '已发布到广场' : '已从广场下架');
    load();
  };

  const bindingOperate = async (action: 'resync' | 'unbind') => {
    await api<BindingResp>(`/v1/api/bindings/${action}`, { projectId, agentId });
    message.success(action === 'resync' ? '已重新同步' : '已解绑');
    load();
  };

  if (!projectId) {
    return (
      <Card title="Agent 详情">
        <Alert
          type="info"
          showIcon
          message="该页面需要 projectId 上下文"
          description={
            <Space>
              <span>请输入所属项目 ID：</span>
              <InputNumber min={1} precision={0} onChange={(v) => setProjectId(v ?? undefined)} />
              <Button type="primary" disabled={!projectId}>
                加载
              </Button>
            </Space>
          }
        />
      </Card>
    );
  }

  const agent = detail?.agent;
  const binding = bindings[0];

  const versionColumns: ColumnsType<AgentVersion> = [
    { title: 'ID', dataIndex: 'id', width: 70 },
    { title: '版本', dataIndex: 'version', width: 110 },
    {
      title: '状态',
      dataIndex: 'status',
      width: 120,
      render: (s: string) => <Tag color={s === 'REGISTERED' ? 'green' : 'default'}>{s}</Tag>,
    },
    {
      title: '能力降级',
      dataIndex: 'capabilityDegraded',
      width: 100,
      render: (v?: boolean) => (v ? <Tag color="orange">是</Tag> : '否'),
    },
    {
      title: '声明 declaration',
      dataIndex: 'declaration',
      render: (v?: string) => <JsonView value={v} title="Agent 声明" />,
    },
    { title: '登记人', dataIndex: 'registeredBy', width: 100 },
    { title: '登记时间', dataIndex: 'registeredAt', width: 170, render: fmtTime },
  ];

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <PageHeader
        backTo="/agents"
        title={agent ? `${agent.name}（${agent.code}）` : `Agent #${agentId}`}
        badge={agent && <StatusBadge value={agent.status} />}
        subTitle={agent ? `ID ${agent.id} · ${agent.accessMode ?? '-'} · 可见性 ${agent.visibility ?? 'PROJECT'}` : undefined}
        extra={
          <Space>
            {agent?.visibility === 'PUBLIC' ? (
              <Popconfirm title="确认从广场下架该 Agent？" onConfirm={() => setPublic(false)}>
                <Button>下架（unpublish）</Button>
              </Popconfirm>
            ) : (
              <Popconfirm title="发布到广场后全平台可见（PUBLIC）" onConfirm={() => setPublic(true)}>
                <Button type="primary" disabled={!agent || agent.status !== 'ACTIVE'}>
                  发布到广场（publish）
                </Button>
              </Popconfirm>
            )}
            <Button icon={<ReloadOutlined />} onClick={load}>刷新</Button>
          </Space>
        }
      />
      <Card className="detail-hero" loading={loading}>
      {agent && (
          <>
            <Descriptions size="small" column={4}>
              <Descriptions.Item label="ID">{agent.id}</Descriptions.Item>
              <Descriptions.Item label="接入模式">
                {agent.accessMode ? <Tag>{agent.accessMode}</Tag> : '-'}
              </Descriptions.Item>
              <Descriptions.Item label="状态">
                <Tag color={agent.status === 'ACTIVE' ? 'green' : 'default'}>{agent.status}</Tag>
              </Descriptions.Item>
              <Descriptions.Item label="可见性">
                <Tag color={agent.visibility === 'PUBLIC' ? 'orange' : 'default'}>
                  {agent.visibility ?? 'PROJECT'}
                </Tag>
              </Descriptions.Item>
              <Descriptions.Item label="创建时间">{fmtTime(agent.createdAt)}</Descriptions.Item>
              <Descriptions.Item label="Runtime Endpoint" span={2}>
                {agent.runtimeEndpoint || '-'}
              </Descriptions.Item>
              <Descriptions.Item label="Health Endpoint" span={2}>
                {agent.healthEndpoint || '-'}
              </Descriptions.Item>
              <Descriptions.Item label="描述" span={4}>
                {agent.description || '-'}
              </Descriptions.Item>
            </Descriptions>
            {detail?.capabilityNote && (
              <Alert style={{ marginTop: 12 }} type="info" showIcon message="能力说明" description={detail.capabilityNote} />
            )}
          </>
        )}
      </Card>

      <Card>
        <Tabs
          items={[
            {
              key: 'versions',
              label: '版本',
              children: (
                <>
                  <Space style={{ marginBottom: 16 }}>
                    <Button type="primary" onClick={() => setRegisterOpen(true)}>
                      注册新版本
                    </Button>
                  </Space>
                  <Table rowKey="id" columns={versionColumns} dataSource={versions} pagination={false} />
                </>
              ),
            },
            {
              key: 'cmdb',
              label: 'CMDB 归属',
              children: (
                <Space direction="vertical" size="middle" style={{ width: '100%' }}>
                  {binding ? (
                    <>
                      <Descriptions size="small" column={4} bordered>
                        <Descriptions.Item label="应用编码">{binding.appCode}</Descriptions.Item>
                        <Descriptions.Item label="来源系统">{binding.sourceSystem || '-'}</Descriptions.Item>
                        <Descriptions.Item label="同步状态">
                          <Tag color={SYNC_COLORS[binding.effectiveSyncStatus ?? binding.syncStatus]}>
                            {binding.effectiveSyncStatus ?? binding.syncStatus}
                          </Tag>
                        </Descriptions.Item>
                        <Descriptions.Item label="同步时间">{fmtTime(binding.syncedAt)}</Descriptions.Item>
                        <Descriptions.Item label="绑定人">{binding.boundBy}</Descriptions.Item>
                        <Descriptions.Item label="绑定时间" span={3}>
                          {fmtTime(binding.createdAt)}
                        </Descriptions.Item>
                        {binding.warning && (
                          <Descriptions.Item label="告警" span={4}>
                            <Typography.Text type="warning">{binding.warning}</Typography.Text>
                          </Descriptions.Item>
                        )}
                      </Descriptions>
                      <Card size="small" title="CMDB 应用快照">
                        <pre style={{ maxHeight: 320, overflow: 'auto', background: colors.bgCode, padding: 12, borderRadius: 6 }}>
                          {prettyJson(binding.snapshot)}
                        </pre>
                      </Card>
                      <Space>
                        <Button onClick={() => bindingOperate('resync')}>重新同步（resync）</Button>
                        <Popconfirm title="确认解绑该应用归属？" onConfirm={() => bindingOperate('unbind')}>
                          <Button danger>解绑（unbind）</Button>
                        </Popconfirm>
                      </Space>
                    </>
                  ) : (
                    <>
                      <Alert type="info" showIcon message="该 Agent 尚未绑定 CMDB 应用" />
                      <Button type="primary" onClick={() => setBindOpen(true)}>
                        绑定应用
                      </Button>
                    </>
                  )}
                </Space>
              ),
            },
          ]}
        />
      </Card>

      <Modal
        title="注册新版本（声明登记，登记后不可变）"
        open={registerOpen}
        onOk={registerVersion}
        onCancel={() => setRegisterOpen(false)}
        destroyOnClose
        width={720}
      >
        <Form form={registerForm} layout="vertical" preserve={false}>
          <Form.Item
            name="version"
            label="版本号（x.y.z）"
            rules={[
              { required: true, message: '请输入版本号' },
              { pattern: /^\d+\.\d+\.\d+$/, message: '版本号必须为 x.y.z 格式' },
            ]}
          >
            <Input placeholder="如 1.0.0" />
          </Form.Item>
          <Form.Item
            name="declaration"
            label="声明 declaration（JSON；NATIVE 模式必填完整声明清单）"
            rules={[jsonRule()]}
          >
            <JsonTextArea rows={16} placeholder={DECLARATION_PLACEHOLDER} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal title="绑定 CMDB 应用" open={bindOpen} onOk={bind} onCancel={() => setBindOpen(false)} destroyOnClose>
        <Form form={bindForm} layout="vertical" preserve={false}>
          <Form.Item
            name="appCode"
            label="应用编码（mock 应用：APP-CORE-001 / APP-CRM-002 / APP-RISK-003）"
            rules={[{ required: true, message: '请输入应用编码' }, { max: 64 }]}
          >
            <Input placeholder="如 APP-CORE-001" />
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  );
}
