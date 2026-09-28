import { useCallback, useEffect, useState } from 'react';
import {
  Alert,
  Button,
  Card,
  Form,
  Modal,
  Popconfirm,
  Select,
  Space,
  Switch,
  Table,
  Tag,
  Typography,
  message,
} from 'antd';
import { PlusOutlined, ReloadOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import { api } from '../../api/client';
import EmptyState from '../../components/EmptyState';
import PageHeader from '../../components/PageHeader';
import ProjectSelect from '../../components/ProjectSelect';
import { useProject } from '../../context/ProjectContext';
import type { Agent, Asset, EnvType, McpInvokePolicy } from '../../types';
import { fmtTime } from '../../utils/format';

/** MCP 调用策略：grant（upsert allowed）/ revoke（删除，回到 fail-closed）/ list */
export default function McpPolicies() {
  const { projectId } = useProject();
  const [agents, setAgents] = useState<Agent[]>([]);
  const [assets, setAssets] = useState<Asset[]>([]);
  const [agentFilter, setAgentFilter] = useState<number | undefined>();
  const [envFilter, setEnvFilter] = useState<EnvType | undefined>();
  const [data, setData] = useState<McpInvokePolicy[]>([]);
  const [loading, setLoading] = useState(false);
  const [grantOpen, setGrantOpen] = useState(false);
  const [form] = Form.useForm<{ agentId: number; assetId: number; env: EnvType; allowed: boolean }>();

  useEffect(() => {
    if (!projectId) {
      setAgents([]);
      setAssets([]);
      return;
    }
    api<Agent[]>('/v1/api/agents/list', { projectId })
      .then(setAgents)
      .catch(() => undefined);
    api<Asset[]>('/v1/api/assets/list', { projectId })
      .then(setAssets)
      .catch(() => undefined);
  }, [projectId]);

  const load = useCallback(async () => {
    if (!projectId) {
      setData([]);
      return;
    }
    setLoading(true);
    try {
      setData(
        await api<McpInvokePolicy[]>('/v1/api/mcp-policies/list', {
          projectId,
          agentId: agentFilter ?? undefined,
          env: envFilter ?? undefined,
        }),
      );
    } catch {
      // helper 已提示
    } finally {
      setLoading(false);
    }
  }, [projectId, agentFilter, envFilter]);

  useEffect(() => {
    load();
  }, [load]);

  const grant = async () => {
    if (!projectId) return;
    const values = await form.validateFields();
    await api<McpInvokePolicy>('/v1/api/mcp-policies/grant', {
      projectId,
      agentId: values.agentId,
      assetId: values.assetId,
      env: values.env,
      allowed: values.allowed,
    });
    message.success('策略已保存');
    setGrantOpen(false);
    form.resetFields();
    load();
  };

  const revoke = async (id: number) => {
    await api('/v1/api/mcp-policies/revoke', { projectId, id });
    message.success('已删除策略（回到 fail-closed）');
    load();
  };

  const assetName = (id: number) => {
    const a = assets.find((x) => x.id === id);
    return a ? `${a.code}（${a.name}）` : `#${id}`;
  };

  const columns: ColumnsType<McpInvokePolicy> = [
    { title: 'ID', dataIndex: 'id', width: 70 },
    { title: 'Agent', dataIndex: 'agentId', width: 90 },
    { title: '工具资产', dataIndex: 'assetId', render: assetName },
    {
      title: '环境',
      dataIndex: 'env',
      width: 80,
      render: (e: string) => <Tag color={e === 'PROD' ? 'red' : e === 'DEV' ? 'green' : 'orange'}>{e}</Tag>,
    },
    {
      title: '允许调用',
      dataIndex: 'allowed',
      width: 100,
      render: (v: boolean) => (v ? <Tag color="green">允许</Tag> : <Tag color="red">拒绝</Tag>),
    },
    { title: '创建人', dataIndex: 'createdBy', width: 100 },
    { title: '更新时间', dataIndex: 'updatedAt', width: 170, render: fmtTime },
    {
      title: '操作',
      width: 90,
      render: (_, r) => (
        <Popconfirm title="确认删除该策略？删除后回到 fail-closed（默认拒绝）" onConfirm={() => revoke(r.id)}>
          <Typography.Link type="danger">删除</Typography.Link>
        </Popconfirm>
      ),
    },
  ];

  return (
    <>
      <PageHeader title="MCP 调用策略" subTitle="MCP 工具默认 fail-closed；授权后可调，删除策略即回到拒绝" />
      <Card className="soft-card">
      <Space wrap style={{ marginBottom: 16 }}>
        <ProjectSelect />
        <Select
          allowClear
          placeholder="按 Agent 筛选"
          style={{ minWidth: 200 }}
          value={agentFilter}
          onChange={setAgentFilter}
          disabled={!projectId}
          showSearch
          optionFilterProp="label"
          options={agents.map((a) => ({ value: a.id, label: `${a.code}（${a.name}）` }))}
        />
        <Select
          allowClear
          placeholder="按环境筛选"
          style={{ minWidth: 130 }}
          value={envFilter}
          onChange={setEnvFilter}
          options={['DEV', 'SIT', 'UAT', 'PROD'].map((e) => ({ value: e, label: e }))}
        />
        <Button icon={<ReloadOutlined />} onClick={load} disabled={!projectId}>
          刷新
        </Button>
        <Button type="primary" icon={<PlusOutlined />} disabled={!projectId} onClick={() => setGrantOpen(true)}>
          新增/更新策略
        </Button>
      </Space>
      {!projectId && <Alert style={{ marginBottom: 16 }} type="info" showIcon message="请先选择项目" />}
      <Table locale={{ emptyText: <EmptyState small description="暂无 MCP 策略（默认 fail-closed），点击「新增/更新策略」" /> }} rowKey="id" loading={loading} columns={columns} dataSource={data} pagination={false} />

      <Modal title="MCP 调用策略（按 Agent + 工具资产 + 环境 upsert）" open={grantOpen} onOk={grant} onCancel={() => setGrantOpen(false)} destroyOnClose>
        <Form form={form} layout="vertical" preserve={false} initialValues={{ env: 'DEV', allowed: true }}>
          <Form.Item name="agentId" label="Agent" rules={[{ required: true, message: '请选择 Agent' }]}>
            <Select
              showSearch
              optionFilterProp="label"
              options={agents.map((a) => ({ value: a.id, label: `${a.code}（${a.name}）` }))}
            />
          </Form.Item>
          <Form.Item name="assetId" label="MCP 工具资产" rules={[{ required: true, message: '请选择工具资产' }]}>
            <Select
              showSearch
              optionFilterProp="label"
              options={assets
                .filter((a) => a.assetType === 'MCP_TOOL' || a.assetType === 'MCP_SERVICE')
                .map((a) => ({ value: a.id, label: `${a.code}（${a.name} / ${a.assetType}）` }))}
            />
          </Form.Item>
          <Form.Item name="env" label="环境" rules={[{ required: true }]}>
            <Select options={['DEV', 'SIT', 'UAT', 'PROD'].map((e) => ({ value: e, label: e }))} />
          </Form.Item>
          <Form.Item name="allowed" label="是否允许调用" valuePropName="checked" rules={[{ required: true }]}>
            <Switch checkedChildren="允许" unCheckedChildren="拒绝" />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
    </>
  );
}
