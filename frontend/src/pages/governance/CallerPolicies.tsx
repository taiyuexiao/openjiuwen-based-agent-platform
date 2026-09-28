import { useCallback, useEffect, useState } from 'react';
import {
  Alert,
  Button,
  Card,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Select,
  Space,
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
import type { Agent, CallerPolicy, CallerType, EnvType } from '../../types';
import { fmtTime } from '../../utils/format';

/** 调用方策略：grant（授权 + 流控参数）/ revoke / list */
export default function CallerPolicies() {
  const { projectId } = useProject();
  const [agents, setAgents] = useState<Agent[]>([]);
  const [agentFilter, setAgentFilter] = useState<number | undefined>();
  const [envFilter, setEnvFilter] = useState<EnvType | undefined>();
  const [data, setData] = useState<CallerPolicy[]>([]);
  const [loading, setLoading] = useState(false);
  const [grantOpen, setGrantOpen] = useState(false);
  const [form] = Form.useForm<{
    agentId: number;
    env: EnvType;
    callerType: CallerType;
    callerId: string;
    sharedToken?: string;
    rateLimitPerMin?: number;
    timeoutMs?: number;
  }>();

  useEffect(() => {
    if (!projectId) {
      setAgents([]);
      return;
    }
    api<Agent[]>('/v1/api/agents/list', { projectId })
      .then(setAgents)
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
        await api<CallerPolicy[]>('/v1/api/caller-policies/list', {
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
    await api<CallerPolicy>('/v1/api/caller-policies/grant', {
      projectId,
      agentId: values.agentId,
      env: values.env,
      callerType: values.callerType,
      callerId: values.callerId,
      sharedToken: values.sharedToken?.trim() || undefined,
      rateLimitPerMin: values.rateLimitPerMin ?? undefined,
      timeoutMs: values.timeoutMs ?? undefined,
    });
    message.success('授权成功');
    setGrantOpen(false);
    form.resetFields();
    load();
  };

  const revoke = async (id: number) => {
    await api<CallerPolicy>('/v1/api/caller-policies/revoke', { projectId, id });
    message.success('已吊销');
    load();
  };

  const columns: ColumnsType<CallerPolicy> = [
    { title: 'ID', dataIndex: 'id', width: 70 },
    { title: 'Agent', dataIndex: 'agentId', width: 80 },
    {
      title: '环境',
      dataIndex: 'env',
      width: 80,
      render: (e: string) => <Tag color={e === 'PROD' ? 'red' : e === 'DEV' ? 'green' : 'orange'}>{e}</Tag>,
    },
    {
      title: '调用方类型',
      dataIndex: 'callerType',
      width: 110,
      render: (t: string) => <Tag color={t === 'USER' ? 'blue' : t === 'AGENT' ? 'purple' : 'cyan'}>{t}</Tag>,
    },
    { title: '调用方 ID', dataIndex: 'callerId', width: 140 },
    { title: '共享令牌', dataIndex: 'sharedToken', width: 120, ellipsis: true, render: (v?: string) => v ?? '-' },
    { title: '限流（次/分）', dataIndex: 'rateLimitPerMin', width: 110, render: (v?: number) => v ?? '-' },
    { title: '超时（ms）', dataIndex: 'timeoutMs', width: 100, render: (v?: number) => v ?? '-' },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (s: string) => <Tag color={s === 'ACTIVE' ? 'green' : 'default'}>{s}</Tag>,
    },
    { title: '创建人', dataIndex: 'createdBy', width: 100 },
    { title: '创建时间', dataIndex: 'createdAt', width: 170, render: fmtTime },
    {
      title: '操作',
      width: 90,
      render: (_, r) =>
        r.status === 'ACTIVE' ? (
          <Popconfirm title="确认吊销该调用方授权？" onConfirm={() => revoke(r.id)}>
            <Typography.Link type="danger">吊销</Typography.Link>
          </Popconfirm>
        ) : null,
    },
  ];

  return (
    <>
      <PageHeader title="调用方策略" subTitle="按 Agent + 环境授权调用方，支持限流与超时参数" />
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
          授权调用方
        </Button>
      </Space>
      {!projectId && <Alert style={{ marginBottom: 16 }} type="info" showIcon message="请先选择项目" />}
      <Table locale={{ emptyText: <EmptyState small description="暂无调用方策略，点击「授权调用方」" /> }} rowKey="id" loading={loading} columns={columns} dataSource={data} pagination={false} />

      <Modal title="授权调用方" open={grantOpen} onOk={grant} onCancel={() => setGrantOpen(false)} destroyOnClose width={560}>
        <Form form={form} layout="vertical" preserve={false} initialValues={{ env: 'DEV', callerType: 'USER' }}>
          <Form.Item name="agentId" label="被调用 Agent" rules={[{ required: true, message: '请选择 Agent' }]}>
            <Select
              showSearch
              optionFilterProp="label"
              options={agents.map((a) => ({ value: a.id, label: `${a.code}（${a.name}）` }))}
            />
          </Form.Item>
          <Form.Item name="env" label="环境" rules={[{ required: true }]}>
            <Select options={['DEV', 'SIT', 'UAT', 'PROD'].map((e) => ({ value: e, label: e }))} />
          </Form.Item>
          <Form.Item name="callerType" label="调用方类型" rules={[{ required: true }]}>
            <Select
              options={[
                { value: 'USER', label: 'USER（行内用户/渠道）' },
                { value: 'HIAGENT', label: 'HIAGENT（高码平台 Agent）' },
                { value: 'AGENT', label: 'AGENT（本平台 Agent）' },
              ]}
            />
          </Form.Item>
          <Form.Item name="callerId" label="调用方 ID" rules={[{ required: true, message: '请输入调用方 ID' }]}>
            <Input placeholder="如 mobile-app / agent-code" />
          </Form.Item>
          <Form.Item name="sharedToken" label="共享令牌（可选）">
            <Input.Password placeholder="调用方凭证" />
          </Form.Item>
          <Form.Item name="rateLimitPerMin" label="限流（次/分钟，可选）">
            <InputNumber style={{ width: '100%' }} min={1} precision={0} />
          </Form.Item>
          <Form.Item name="timeoutMs" label="超时（毫秒，可选）">
            <InputNumber style={{ width: '100%' }} min={100} precision={0} />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
    </>
  );
}
