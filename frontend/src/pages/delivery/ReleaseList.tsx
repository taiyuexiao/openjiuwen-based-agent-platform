import { useCallback, useEffect, useState } from 'react';
import { Alert, Button, Card, Form, Modal, Select, Skeleton, Space, Table, Tag, message } from 'antd';
import { PlusOutlined, ReloadOutlined } from '@ant-design/icons';
import { Link } from 'react-router-dom';
import type { ColumnsType } from 'antd/es/table';
import { api } from '../../api/client';
import EmptyState from '../../components/EmptyState';
import PageHeader from '../../components/PageHeader';
import ProjectSelect from '../../components/ProjectSelect';
import { useProject } from '../../context/ProjectContext';
import type { Agent, Artifact, DeployTarget, ProjectTargetResp, Release, ReleaseStatus } from '../../types';
import { fmtTime } from '../../utils/format';

export const RELEASE_STATUS_COLORS: Record<string, string> = {
  DRAFT: 'default',
  GATED: 'cyan',
  APPROVED: 'blue',
  DEPLOYING: 'orange',
  RUNNING: 'green',
  FAILED: 'red',
  ROLLED_BACK: 'purple',
};

/** 发布单列表 + 创建 */
export default function ReleaseList() {
  const { projectId } = useProject();
  const [agents, setAgents] = useState<Agent[]>([]);
  const [agentFilter, setAgentFilter] = useState<number | undefined>();
  const [statusFilter, setStatusFilter] = useState<ReleaseStatus | undefined>();
  const [data, setData] = useState<Release[]>([]);
  const [loading, setLoading] = useState(false);
  const [createOpen, setCreateOpen] = useState(false);
  const [artifacts, setArtifacts] = useState<Artifact[]>([]);
  const [targets, setTargets] = useState<DeployTarget[]>([]);
  const [form] = Form.useForm<{ agentId: number; agentVersion: string; artifactId: number; targetId: number }>();
  const watchAgentId = Form.useWatch('agentId', form);

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
        await api<Release[]>('/v1/api/releases/list', {
          projectId,
          agentId: agentFilter ?? undefined,
          status: statusFilter ?? undefined,
        }),
      );
    } catch {
      // helper 已提示
    } finally {
      setLoading(false);
    }
  }, [projectId, agentFilter, statusFilter]);

  useEffect(() => {
    load();
  }, [load]);

  // 创建弹窗打开时加载制品与项目可选目标
  useEffect(() => {
    if (!createOpen || !projectId) return;
    api<ProjectTargetResp[]>(`/v1/api/projects/${projectId}/deploy-targets/list`)
      .then((list) => setTargets(list.map((r) => r.target).filter((t): t is DeployTarget => !!t)))
      .catch(() => undefined);
  }, [createOpen, projectId]);

  useEffect(() => {
    if (!createOpen || !projectId || !watchAgentId) {
      setArtifacts([]);
      return;
    }
    api<Artifact[]>('/v1/api/artifacts/list', { projectId, agentId: watchAgentId })
      .then(setArtifacts)
      .catch(() => undefined);
  }, [createOpen, projectId, watchAgentId]);

  const create = async () => {
    if (!projectId) return;
    const values = await form.validateFields();
    await api<Release>('/v1/api/releases/create', {
      projectId,
      agentId: values.agentId,
      agentVersion: values.agentVersion,
      artifactId: values.artifactId,
      targetId: values.targetId,
    });
    message.success('发布单创建成功');
    setCreateOpen(false);
    form.resetFields();
    load();
  };

  const agentName = (id: number) => {
    const a = agents.find((x) => x.id === id);
    return a ? `${a.code}（${a.name}）` : `#${id}`;
  };

  const columns: ColumnsType<Release> = [
    { title: 'ID', dataIndex: 'id', width: 70 },
    { title: 'Agent', dataIndex: 'agentId', render: agentName },
    { title: '版本', dataIndex: 'agentVersion', width: 90 },
    { title: '制品', dataIndex: 'artifactId', width: 80 },
    { title: '目标', dataIndex: 'targetId', width: 80 },
    {
      title: '状态',
      dataIndex: 'status',
      width: 120,
      render: (s: string) => <Tag color={RELEASE_STATUS_COLORS[s]}>{s}</Tag>,
    },
    {
      title: '回滚自',
      dataIndex: 'rollbackOf',
      width: 90,
      render: (v?: number) => (v ? `#${v}` : '-'),
    },
    { title: '审批引用', dataIndex: 'approvalRef', width: 130, render: (v?: string) => v ?? '-' },
    { title: '创建人', dataIndex: 'createdBy', width: 100 },
    { title: '创建时间', dataIndex: 'createdAt', width: 170, render: fmtTime },
    {
      title: '操作',
      width: 80,
      render: (_, r) => <Link to={`/releases/${r.id}?projectId=${projectId}`}>详情</Link>,
    },
  ];

  return (
    <>
      <PageHeader title="发布单" subTitle="发布状态机：DRAFT → GATED → APPROVED → DEPLOYING → RUNNING" />
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
          placeholder="按状态筛选"
          style={{ minWidth: 150 }}
          value={statusFilter}
          onChange={setStatusFilter}
          options={Object.keys(RELEASE_STATUS_COLORS).map((s) => ({ value: s, label: s }))}
        />
        <Button icon={<ReloadOutlined />} onClick={load} disabled={!projectId}>
          刷新
        </Button>
        <Button type="primary" icon={<PlusOutlined />} disabled={!projectId} onClick={() => setCreateOpen(true)}>
          创建发布单
        </Button>
      </Space>
      {!projectId && <Alert style={{ marginBottom: 16 }} type="info" showIcon message="请先选择项目" />}
      {loading && data.length === 0 ? (
        <Skeleton active paragraph={{ rows: 6 }} title={false} />
      ) : (
        <Table locale={{ emptyText: <EmptyState small description="暂无发布单，点击右上角「创建发布单」" /> }} rowKey="id" columns={columns} dataSource={data} pagination={{ pageSize: 20 }} />
      )}

      <Modal title="创建发布单" open={createOpen} onOk={create} onCancel={() => setCreateOpen(false)} destroyOnClose>
        <Form form={form} layout="vertical" preserve={false}>
          <Form.Item name="agentId" label="Agent" rules={[{ required: true, message: '请选择 Agent' }]}>
            <Select
              showSearch
              optionFilterProp="label"
              options={agents.map((a) => ({ value: a.id, label: `${a.code}（${a.name}）` }))}
            />
          </Form.Item>
          <Form.Item name="agentVersion" label="Agent 版本" rules={[{ required: true, message: '请选择版本' }]}>
            <Select
              placeholder="选择该 Agent 已登记制品对应的版本"
              options={[...new Set(artifacts.map((a) => a.agentVersion))].map((v) => ({ value: v, label: v }))}
            />
          </Form.Item>
          <Form.Item name="artifactId" label="制品" rules={[{ required: true, message: '请选择制品' }]}>
            <Select
              placeholder="选择已登记制品"
              options={artifacts.map((a) => ({
                value: a.id,
                label: `#${a.id}（${a.agentVersion}${a.codeCommit ? ` / ${a.codeCommit.slice(0, 8)}` : ''}）`,
              }))}
            />
          </Form.Item>
          <Form.Item name="targetId" label="部署目标" rules={[{ required: true, message: '请选择部署目标' }]}>
            <Select
              placeholder="项目已 attach 的目标"
              options={targets.map((t) => ({ value: t.id, label: `${t.code}（${t.name} / ${t.env}）` }))}
            />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
    </>
  );
}
