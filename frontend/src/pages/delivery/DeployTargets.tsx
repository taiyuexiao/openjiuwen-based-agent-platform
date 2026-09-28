import { useCallback, useEffect, useState } from 'react';
import {
  Button,
  Card,
  Divider,
  Form,
  Input,
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
import JsonTextArea, { jsonRule } from '../../components/JsonTextArea';
import JsonView from '../../components/JsonView';
import EmptyState from '../../components/EmptyState';
import PageHeader from '../../components/PageHeader';
import ProjectSelect from '../../components/ProjectSelect';
import { useProject } from '../../context/ProjectContext';
import type { DeployTarget, DeployTargetStatus, EnvType, ProjectTargetResp } from '../../types';
import { fmtTime, parseJson } from '../../utils/format';

const LEVELS = ['P0', 'P1', 'P2', 'P3'];

/** 部署目标（平台级）+ 项目 attach/detach/set-default */
export default function DeployTargets() {
  const [envFilter, setEnvFilter] = useState<EnvType | undefined>();
  const [statusFilter, setStatusFilter] = useState<DeployTargetStatus | undefined>();
  const [data, setData] = useState<DeployTarget[]>([]);
  const [loading, setLoading] = useState(false);
  const [createOpen, setCreateOpen] = useState(false);
  const [form] = Form.useForm<{
    code: string;
    name: string;
    env: EnvType;
    cluster: string;
    namespace: string;
    baseResource?: string;
    allowedAgentLevels?: string[];
  }>();

  const { projectId } = useProject();
  const [projectTargets, setProjectTargets] = useState<ProjectTargetResp[]>([]);
  const [ptLoading, setPtLoading] = useState(false);
  const [attachTargetId, setAttachTargetId] = useState<number | undefined>();

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setData(
        await api<DeployTarget[]>('/v1/api/deploy-targets/list', {
          env: envFilter ?? undefined,
          status: statusFilter ?? undefined,
        }),
      );
    } catch {
      // helper 已提示
    } finally {
      setLoading(false);
    }
  }, [envFilter, statusFilter]);

  useEffect(() => {
    load();
  }, [load]);

  const loadProjectTargets = useCallback(async () => {
    if (!projectId) {
      setProjectTargets([]);
      return;
    }
    setPtLoading(true);
    try {
      setProjectTargets(await api<ProjectTargetResp[]>(`/v1/api/projects/${projectId}/deploy-targets/list`));
    } catch {
      // helper 已提示
    } finally {
      setPtLoading(false);
    }
  }, [projectId]);

  useEffect(() => {
    loadProjectTargets();
  }, [loadProjectTargets]);

  const create = async () => {
    const values = await form.validateFields();
    await api<DeployTarget>('/v1/api/deploy-targets/create', {
      code: values.code,
      name: values.name,
      env: values.env,
      cluster: values.cluster,
      namespace: values.namespace,
      baseResource: values.baseResource?.trim() ? parseJson(values.baseResource) : undefined,
      allowedAgentLevels: values.allowedAgentLevels,
    });
    message.success('部署目标创建成功');
    setCreateOpen(false);
    form.resetFields();
    load();
  };

  const disable = async (id: number) => {
    await api<DeployTarget>('/v1/api/deploy-targets/disable', { id });
    message.success('已禁用');
    load();
  };

  const attach = async () => {
    if (!projectId || !attachTargetId) {
      message.warning('请选择项目与目标');
      return;
    }
    await api(`/v1/api/projects/${projectId}/deploy-targets/attach`, { targetId: attachTargetId });
    message.success('已关联');
    setAttachTargetId(undefined);
    loadProjectTargets();
  };

  const detach = async (targetId: number) => {
    await api(`/v1/api/projects/${projectId}/deploy-targets/detach`, { targetId });
    message.success('已移除关联');
    loadProjectTargets();
  };

  const setDefault = async (targetId: number) => {
    await api(`/v1/api/projects/${projectId}/deploy-targets/set-default`, { targetId });
    message.success('已设为默认');
    loadProjectTargets();
  };

  const columns: ColumnsType<DeployTarget> = [
    { title: 'ID', dataIndex: 'id', width: 70 },
    { title: '编码', dataIndex: 'code', width: 140 },
    { title: '名称', dataIndex: 'name' },
    {
      title: '环境',
      dataIndex: 'env',
      width: 90,
      render: (e: string) => <Tag color={e === 'PROD' ? 'red' : e === 'DEV' ? 'green' : 'orange'}>{e}</Tag>,
    },
    { title: '集群', dataIndex: 'cluster', width: 120 },
    { title: '命名空间', dataIndex: 'namespace', width: 130 },
    {
      title: '基础资源',
      dataIndex: 'baseResource',
      render: (v?: string) => <JsonView value={v} title="基础资源配置" />,
    },
    {
      title: '允许的 Agent 等级',
      dataIndex: 'allowedAgentLevels',
      width: 160,
      render: (v?: string) => {
        if (!v) return '-';
        try {
          return (JSON.parse(v) as string[]).map((l) => <Tag key={l}>{l}</Tag>);
        } catch {
          return v;
        }
      },
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (s: string) => <Tag color={s === 'ACTIVE' ? 'green' : 'default'}>{s}</Tag>,
    },
    { title: '创建时间', dataIndex: 'createdAt', width: 170, render: fmtTime },
    {
      title: '操作',
      width: 100,
      render: (_, r) =>
        r.status === 'ACTIVE' ? (
          <Popconfirm title="确认禁用该部署目标？" onConfirm={() => disable(r.id)}>
            <Typography.Link type="danger">禁用</Typography.Link>
          </Popconfirm>
        ) : null,
    },
  ];

  const ptColumns: ColumnsType<ProjectTargetResp> = [
    { title: '关联 ID', dataIndex: 'id', width: 90 },
    { title: '目标编码', render: (_, r) => r.target?.code ?? `#${r.targetId}` },
    { title: '名称', render: (_, r) => r.target?.name ?? '-' },
    { title: '环境', width: 90, render: (_, r) => r.target && <Tag>{r.target.env}</Tag> },
    {
      title: '默认',
      dataIndex: 'isDefault',
      width: 90,
      render: (v: boolean) => (v ? <Tag color="gold">默认</Tag> : '-'),
    },
    {
      title: '操作',
      width: 180,
      render: (_, r) => (
        <Space>
          {!r.isDefault && (
            <Typography.Link onClick={() => setDefault(r.targetId)}>设为默认</Typography.Link>
          )}
          <Popconfirm title="确认移除该关联？" onConfirm={() => detach(r.targetId)}>
            <Typography.Link type="danger">移除</Typography.Link>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <>
      <PageHeader title="部署目标" subTitle="平台级部署目标管理 + 项目侧 attach / set-default / detach" />
      <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Card
        className="soft-card"
        extra={
          <Space>
            <Select
              allowClear
              placeholder="环境筛选"
              style={{ minWidth: 120 }}
              value={envFilter}
              onChange={setEnvFilter}
              options={['DEV', 'SIT', 'UAT', 'PROD'].map((e) => ({ value: e, label: e }))}
            />
            <Select
              allowClear
              placeholder="状态筛选"
              style={{ minWidth: 120 }}
              value={statusFilter}
              onChange={setStatusFilter}
              options={[
                { value: 'ACTIVE', label: 'ACTIVE' },
                { value: 'DISABLED', label: 'DISABLED' },
              ]}
            />
            <Button icon={<ReloadOutlined />} onClick={load}>
              刷新
            </Button>
            <Button type="primary" icon={<PlusOutlined />} onClick={() => setCreateOpen(true)}>
              创建目标
            </Button>
          </Space>
        }
      >
        <Table locale={{ emptyText: <EmptyState small description="暂无部署目标，点击右上角「创建目标」" /> }} rowKey="id" loading={loading} columns={columns} dataSource={data} pagination={false} />
      </Card>

      <Card title="项目可选部署目标（attach / set-default / detach）">
        <Space wrap style={{ marginBottom: 16 }}>
          <ProjectSelect />
          <Select
            placeholder="选择要关联的目标"
            style={{ minWidth: 240 }}
            value={attachTargetId}
            onChange={setAttachTargetId}
            disabled={!projectId}
            options={data
              .filter((t) => t.status === 'ACTIVE')
              .map((t) => ({ value: t.id, label: `${t.code}（${t.name} / ${t.env}）` }))}
          />
          <Button type="primary" onClick={attach} disabled={!projectId || !attachTargetId}>
            关联（attach）
          </Button>
          <Button icon={<ReloadOutlined />} onClick={loadProjectTargets} disabled={!projectId}>
            刷新
          </Button>
        </Space>
        <Table rowKey="id" loading={ptLoading} columns={ptColumns} dataSource={projectTargets} pagination={false} />
      </Card>

      <Modal title="创建部署目标" open={createOpen} onOk={create} onCancel={() => setCreateOpen(false)} destroyOnClose>
        <Form form={form} layout="vertical" preserve={false} initialValues={{ env: 'DEV' }}>
          <Form.Item name="code" label="编码" rules={[{ required: true }, { max: 64 }]}>
            <Input placeholder="如 k8s-dev-a" />
          </Form.Item>
          <Form.Item name="name" label="名称" rules={[{ required: true }, { max: 128 }]}>
            <Input />
          </Form.Item>
          <Form.Item name="env" label="环境" rules={[{ required: true }]}>
            <Select options={['DEV', 'SIT', 'UAT', 'PROD'].map((e) => ({ value: e, label: e }))} />
          </Form.Item>
          <Form.Item name="cluster" label="集群" rules={[{ required: true }, { max: 64 }]}>
            <Input />
          </Form.Item>
          <Form.Item name="namespace" label="命名空间" rules={[{ required: true }, { max: 64 }]}>
            <Input />
          </Form.Item>
          <Form.Item name="baseResource" label="基础资源（JSON，可选）" rules={[jsonRule()]}>
            <JsonTextArea rows={4} placeholder={'{\n  "cpu": "1",\n  "memory": "2Gi",\n  "replicas": 1\n}'} />
          </Form.Item>
          <Form.Item name="allowedAgentLevels" label="允许的 Agent 等级（可多选）">
            <Select mode="multiple" options={LEVELS.map((l) => ({ value: l, label: l }))} placeholder="不选表示不限制" />
          </Form.Item>
        </Form>
      </Modal>
      <Divider style={{ margin: 0 }} />
    </Space>
    </>
  );
}
