import { useCallback, useEffect, useState } from 'react';
import { Alert, Button, Card, Form, Input, Modal, Select, Space, Table, Tag, message } from 'antd';
import { ReloadOutlined, SyncOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import { api } from '../../api/client';
import EmptyState from '../../components/EmptyState';
import PageHeader from '../../components/PageHeader';
import ProjectSelect from '../../components/ProjectSelect';
import { useProject } from '../../context/ProjectContext';
import type { Agent, EnvType, ServiceRoute } from '../../types';
import { fmtTime } from '../../utils/format';

/** 服务路由：sync（从 RUNNING 部署生成/更新路由）+ list */
export default function Routes() {
  const { projectId } = useProject();
  const [agents, setAgents] = useState<Agent[]>([]);
  const [agentFilter, setAgentFilter] = useState<number | undefined>();
  const [envFilter, setEnvFilter] = useState<EnvType | undefined>();
  const [data, setData] = useState<ServiceRoute[]>([]);
  const [loading, setLoading] = useState(false);
  const [syncOpen, setSyncOpen] = useState(false);
  const [syncForm] = Form.useForm<{ agentId: number; env: EnvType; agentVersion: string }>();

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
        await api<ServiceRoute[]>('/v1/api/routes/list', {
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

  const sync = async () => {
    if (!projectId) return;
    const values = await syncForm.validateFields();
    await api<ServiceRoute>('/v1/api/routes/sync', {
      projectId,
      agentId: values.agentId,
      env: values.env,
      agentVersion: values.agentVersion,
    });
    message.success('路由已同步');
    setSyncOpen(false);
    syncForm.resetFields();
    load();
  };

  const columns: ColumnsType<ServiceRoute> = [
    { title: 'ID', dataIndex: 'id', width: 70 },
    { title: 'Agent', dataIndex: 'agentId', width: 90 },
    {
      title: '环境',
      dataIndex: 'env',
      width: 90,
      render: (e: string) => <Tag color={e === 'PROD' ? 'red' : e === 'DEV' ? 'green' : 'orange'}>{e}</Tag>,
    },
    { title: 'Agent 版本', dataIndex: 'agentVersion', width: 110 },
    { title: '部署实例', dataIndex: 'deploymentId', width: 100, render: (v?: number) => v ?? '-' },
    { title: '路由修订', dataIndex: 'routeRevision', width: 100, render: (v?: number) => v ?? '-' },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (s: string) => <Tag color={s === 'ACTIVE' ? 'green' : 'default'}>{s}</Tag>,
    },
    { title: '创建人', dataIndex: 'createdBy', width: 100 },
    { title: '更新时间', dataIndex: 'updatedAt', width: 170, render: fmtTime },
  ];

  return (
    <>
      <PageHeader title="服务路由" subTitle="从 RUNNING 部署生成/更新路由；sync 后服务目录对调用方可见" />
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
        <Button type="primary" icon={<SyncOutlined />} disabled={!projectId} onClick={() => setSyncOpen(true)}>
          同步路由（sync）
        </Button>
      </Space>
      {!projectId && <Alert style={{ marginBottom: 16 }} type="info" showIcon message="请先选择项目" />}
      <Table locale={{ emptyText: <EmptyState small description="暂无路由，先有 RUNNING 部署后点「同步路由」" /> }} rowKey="id" loading={loading} columns={columns} dataSource={data} pagination={false} />

      <Modal
        title="同步路由（从 RUNNING 部署生成/更新路由）"
        open={syncOpen}
        onOk={sync}
        onCancel={() => setSyncOpen(false)}
        destroyOnClose
      >
        <Form form={syncForm} layout="vertical" preserve={false} initialValues={{ env: 'DEV' }}>
          <Form.Item name="agentId" label="Agent" rules={[{ required: true, message: '请选择 Agent' }]}>
            <Select
              showSearch
              optionFilterProp="label"
              options={agents.map((a) => ({ value: a.id, label: `${a.code}（${a.name}）` }))}
            />
          </Form.Item>
          <Form.Item name="env" label="环境" rules={[{ required: true }]}>
            <Select options={['DEV', 'SIT', 'UAT', 'PROD'].map((e) => ({ value: e, label: e }))} />
          </Form.Item>
          <Form.Item
            name="agentVersion"
            label="Agent 版本（x.y.z，须存在 RUNNING 部署）"
            rules={[
              { required: true, message: '请输入版本号' },
              { pattern: /^\d+\.\d+\.\d+$/, message: '版本号必须为 x.y.z 格式' },
            ]}
          >
            <Input placeholder="如 1.0.0" />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
    </>
  );
}
