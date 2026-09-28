import { useCallback, useEffect, useState } from 'react';
import { Alert, Button, Card, Form, Input, Modal, Popconfirm, Select, Skeleton, Space, Table, Tag, Typography, message } from 'antd';
import { PlusOutlined, ReloadOutlined } from '@ant-design/icons';
import { Link, useNavigate } from 'react-router-dom';
import type { ColumnsType } from 'antd/es/table';
import { api } from '../../api/client';
import EmptyState from '../../components/EmptyState';
import PageHeader from '../../components/PageHeader';
import ProjectSelect from '../../components/ProjectSelect';
import { useProject } from '../../context/ProjectContext';
import type { AccessMode, Agent, AgentStatus } from '../../types';
import { fmtTime } from '../../utils/format';

const MODE_COLORS: Record<string, string> = {
  NATIVE: 'green',
  ADAPTED: 'blue',
  HOSTED: 'orange',
};

/** Agent 列表 + 创建（HOSTED 模式必须填 runtime/health endpoint） */
export default function AgentList() {
  const { projectId } = useProject();
  const navigate = useNavigate();
  const [accessMode, setAccessMode] = useState<AccessMode | undefined>();
  const [status, setStatus] = useState<AgentStatus | undefined>();
  const [data, setData] = useState<Agent[]>([]);
  const [loading, setLoading] = useState(false);
  const [createOpen, setCreateOpen] = useState(false);
  const [form] = Form.useForm<{
    code: string;
    name: string;
    accessMode?: AccessMode;
    description?: string;
    runtimeEndpoint?: string;
    healthEndpoint?: string;
  }>();
  const watchMode = Form.useWatch('accessMode', form);

  const load = useCallback(async () => {
    if (!projectId) {
      setData([]);
      return;
    }
    setLoading(true);
    try {
      setData(
        await api<Agent[]>('/v1/api/agents/list', {
          projectId,
          accessMode: accessMode ?? undefined,
          status: status ?? undefined,
        }),
      );
    } catch {
      // helper 已提示
    } finally {
      setLoading(false);
    }
  }, [projectId, accessMode, status]);

  useEffect(() => {
    load();
  }, [load]);

  const create = async () => {
    if (!projectId) return;
    const values = await form.validateFields();
    await api<Agent>('/v1/api/agents/create', {
      code: values.code,
      name: values.name,
      projectId,
      accessMode: values.accessMode ?? undefined,
      description: values.description?.trim() || undefined,
      runtimeEndpoint: values.runtimeEndpoint?.trim() || undefined,
      healthEndpoint: values.healthEndpoint?.trim() || undefined,
    });
    message.success('Agent 创建成功');
    setCreateOpen(false);
    form.resetFields();
    load();
  };

  const archive = async (r: Agent) => {
    await api<Agent>('/v1/api/agents/archive', { id: r.id, projectId: r.projectId });
    message.success('已归档');
    load();
  };

  const columns: ColumnsType<Agent> = [
    { title: 'ID', dataIndex: 'id', width: 70 },
    { title: '编码', dataIndex: 'code', width: 160 },
    {
      title: '名称',
      dataIndex: 'name',
      render: (_, r) => <Link to={`/agents/${r.id}?projectId=${r.projectId}`}>{r.name}</Link>,
    },
    {
      title: '接入模式',
      dataIndex: 'accessMode',
      width: 110,
      render: (m?: string) => (m ? <Tag color={MODE_COLORS[m]}>{m}</Tag> : '-'),
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (s: string) => <Tag color={s === 'ACTIVE' ? 'green' : 'default'}>{s}</Tag>,
    },
    { title: 'Runtime Endpoint', dataIndex: 'runtimeEndpoint', ellipsis: true, render: (v?: string) => v ?? '-' },
    { title: '创建人', dataIndex: 'createdBy', width: 100 },
    { title: '创建时间', dataIndex: 'createdAt', width: 170, render: fmtTime },
    {
      title: '操作',
      width: 130,
      render: (_, r) => (
        <Space>
          <Link to={`/agents/${r.id}?projectId=${r.projectId}`}>详情</Link>
          {r.status === 'ACTIVE' && (
            <Popconfirm title="确认归档该 Agent？" onConfirm={() => archive(r)}>
              <Typography.Link type="danger">归档</Typography.Link>
            </Popconfirm>
          )}
        </Space>
      ),
    },
  ];

  return (
    <>
      <PageHeader title="Agent 管理" subTitle="Agent 的创建、登记与生命周期管理；推荐使用三步创建向导" />
      <Card className="soft-card">
      <Space wrap style={{ marginBottom: 16 }}>
        <ProjectSelect />
        <Select
          allowClear
          placeholder="接入模式"
          style={{ minWidth: 150 }}
          value={accessMode}
          onChange={setAccessMode}
          options={['NATIVE', 'ADAPTED', 'HOSTED'].map((m) => ({ value: m, label: m }))}
        />
        <Select
          allowClear
          placeholder="状态"
          style={{ minWidth: 130 }}
          value={status}
          onChange={setStatus}
          options={[
            { value: 'ACTIVE', label: 'ACTIVE' },
            { value: 'ARCHIVED', label: 'ARCHIVED' },
          ]}
        />
        <Button icon={<ReloadOutlined />} onClick={load} disabled={!projectId}>
          刷新
        </Button>
        <Button type="primary" icon={<PlusOutlined />} disabled={!projectId} onClick={() => navigate('/agents/new')}>
          创建向导
        </Button>
        <Button disabled={!projectId} onClick={() => setCreateOpen(true)}>
          快速创建
        </Button>
      </Space>
      {!projectId && <Alert style={{ marginBottom: 16 }} type="info" showIcon message="请先选择项目以加载 Agent 列表" />}
      {loading && data.length === 0 ? (
        <Skeleton active paragraph={{ rows: 6 }} title={false} />
      ) : (
        <Table locale={{ emptyText: <EmptyState small description="还没有 Agent，点击上方「创建向导」创建第一个" /> }} rowKey="id" columns={columns} dataSource={data} pagination={{ pageSize: 20 }} />
      )}

      <Modal title="创建 Agent" open={createOpen} onOk={create} onCancel={() => setCreateOpen(false)} destroyOnClose width={560}>
        <Form form={form} layout="vertical" preserve={false} initialValues={{ accessMode: 'NATIVE' }}>
          <Form.Item name="code" label="编码" rules={[{ required: true, message: '请输入编码' }, { max: 128 }]}>
            <Input placeholder="如 credit-assistant" />
          </Form.Item>
          <Form.Item name="name" label="名称" rules={[{ required: true }, { max: 128 }]}>
            <Input />
          </Form.Item>
          <Form.Item name="accessMode" label="接入模式">
            <Select
              options={[
                { value: 'NATIVE', label: 'NATIVE（平台原生研发）' },
                { value: 'ADAPTED', label: 'ADAPTED（适配已有 Agent）' },
                { value: 'HOSTED', label: 'HOSTED（外部托管接入）' },
              ]}
            />
          </Form.Item>
          {watchMode === 'HOSTED' && (
            <Alert
              style={{ marginBottom: 16 }}
              type="warning"
              showIcon
              message="HOSTED 模式必须提供 runtimeEndpoint 与 healthEndpoint"
            />
          )}
          <Form.Item
            name="runtimeEndpoint"
            label="Runtime Endpoint"
            rules={[
              { max: 512 },
              { required: watchMode === 'HOSTED', message: 'HOSTED 模式必填' },
            ]}
          >
            <Input placeholder="如 http://agent-svc:8080/run" />
          </Form.Item>
          <Form.Item
            name="healthEndpoint"
            label="Health Endpoint"
            rules={[
              { max: 512 },
              { required: watchMode === 'HOSTED', message: 'HOSTED 模式必填' },
            ]}
          >
            <Input placeholder="如 http://agent-svc:8080/health" />
          </Form.Item>
          <Form.Item name="description" label="描述" rules={[{ max: 1024 }]}>
            <Input.TextArea rows={3} />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
    </>
  );
}
