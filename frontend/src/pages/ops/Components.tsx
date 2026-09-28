import { useCallback, useEffect, useState } from 'react';
import {
  Alert,
  Button,
  Card,
  Col,
  Form,
  Input,
  Modal,
  Row,
  Select,
  Space,
  Statistic,
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
import type { ComponentHealthSummary, ComponentRegistry, ComponentType } from '../../types';
import { fmtTime } from '../../utils/format';

const HEALTH_COLORS: Record<string, string> = {
  UP: 'green',
  DOWN: 'red',
  UNKNOWN: 'default',
};

/** 组件运维：register / update / list / health 健康聚合 */
export default function Components() {
  const { projectId } = useProject();
  const [typeFilter, setTypeFilter] = useState<ComponentType | undefined>();
  const [criticalFilter, setCriticalFilter] = useState<boolean | undefined>();
  const [data, setData] = useState<ComponentRegistry[]>([]);
  const [health, setHealth] = useState<ComponentHealthSummary | null>(null);
  const [loading, setLoading] = useState(false);
  const [registerOpen, setRegisterOpen] = useState(false);
  const [editTarget, setEditTarget] = useState<ComponentRegistry | null>(null);
  const [registerForm] = Form.useForm<{
    code: string;
    name: string;
    type: ComponentType;
    ownerTeam?: string;
    healthEndpoint?: string;
    critical?: boolean;
    description?: string;
    deps?: string[];
  }>();
  const [editForm] = Form.useForm<{
    name?: string;
    ownerTeam?: string;
    healthEndpoint?: string;
    critical?: boolean;
    description?: string;
    deps?: string[];
  }>();

  const load = useCallback(async () => {
    if (!projectId) {
      setData([]);
      setHealth(null);
      return;
    }
    setLoading(true);
    try {
      const [list, summary] = await Promise.all([
        api<ComponentRegistry[]>('/v1/api/observability/components/list', {
          projectId,
          type: typeFilter ?? undefined,
          critical: criticalFilter ?? undefined,
        }),
        api<ComponentHealthSummary>('/v1/api/observability/components/health', { projectId }),
      ]);
      setData(list);
      setHealth(summary);
    } catch {
      // helper 已提示
    } finally {
      setLoading(false);
    }
  }, [projectId, typeFilter, criticalFilter]);

  useEffect(() => {
    load();
  }, [load]);

  const register = async () => {
    if (!projectId) return;
    const values = await registerForm.validateFields();
    await api<ComponentRegistry>('/v1/api/observability/components/register', {
      projectId,
      code: values.code,
      name: values.name,
      type: values.type,
      ownerTeam: values.ownerTeam?.trim() || undefined,
      healthEndpoint: values.healthEndpoint?.trim() || undefined,
      critical: values.critical ?? undefined,
      description: values.description?.trim() || undefined,
      deps: values.deps && values.deps.length > 0 ? values.deps : undefined,
    });
    message.success('组件已登记');
    setRegisterOpen(false);
    registerForm.resetFields();
    load();
  };

  const update = async () => {
    if (!projectId || !editTarget) return;
    const values = await editForm.validateFields();
    await api<ComponentRegistry>('/v1/api/observability/components/update', {
      projectId,
      id: editTarget.id,
      name: values.name?.trim() || undefined,
      ownerTeam: values.ownerTeam?.trim() || undefined,
      healthEndpoint: values.healthEndpoint?.trim() || undefined,
      critical: values.critical ?? undefined,
      description: values.description?.trim() || undefined,
      deps: values.deps && values.deps.length > 0 ? values.deps : undefined,
    });
    message.success('组件已更新');
    setEditTarget(null);
    load();
  };

  const parseDeps = (deps?: string): string[] => {
    if (!deps) return [];
    try {
      return JSON.parse(deps) as string[];
    } catch {
      return [];
    }
  };

  const columns: ColumnsType<ComponentRegistry> = [
    { title: 'ID', dataIndex: 'id', width: 70 },
    { title: '编码', dataIndex: 'code', width: 150 },
    { title: '名称', dataIndex: 'name' },
    { title: '类型', dataIndex: 'type', width: 110, render: (t: string) => <Tag>{t}</Tag> },
    { title: '负责团队', dataIndex: 'ownerTeam', width: 120, render: (v?: string) => v ?? '-' },
    {
      title: '关键组件',
      dataIndex: 'critical',
      width: 90,
      render: (v?: boolean) => (v ? <Tag color="red">关键</Tag> : '-'),
    },
    { title: '健康检查端点', dataIndex: 'healthEndpoint', ellipsis: true, render: (v?: string) => v ?? '-' },
    {
      title: '依赖',
      dataIndex: 'deps',
      width: 160,
      render: (v?: string) => {
        const deps = parseDeps(v);
        return deps.length > 0 ? deps.map((d) => <Tag key={d}>{d}</Tag>) : '-';
      },
    },
    {
      title: '操作',
      width: 80,
      render: (_, r) => (
        <Typography.Link
          onClick={() => {
            setEditTarget(r);
            editForm.setFieldsValue({
              name: r.name,
              ownerTeam: r.ownerTeam,
              healthEndpoint: r.healthEndpoint,
              critical: r.critical ?? false,
              description: r.description,
              deps: parseDeps(r.deps),
            });
          }}
        >
          编辑
        </Typography.Link>
      ),
    },
  ];

  const healthColumns: ColumnsType<ComponentHealthSummary['components'][number]> = [
    { title: '编码', dataIndex: 'code', width: 150 },
    { title: '名称', dataIndex: 'name' },
    { title: '类型', dataIndex: 'type', width: 110 },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (s: string) => <Tag color={HEALTH_COLORS[s]}>{s}</Tag>,
    },
    { title: 'HTTP', dataIndex: 'httpStatus', width: 80, render: (v?: number) => v ?? '-' },
    { title: '延迟（ms）', dataIndex: 'latencyMs', width: 100, render: (v?: number) => v ?? '-' },
    { title: '错误', dataIndex: 'error', ellipsis: true, render: (v?: string) => v ?? '-' },
  ];

  const typeOptions = ['DATABASE', 'MQ', 'CACHE', 'SERVICE', 'EXTERNAL'].map((t) => ({ value: t, label: t }));

  return (
    <>
      <PageHeader title="组件运维" subTitle="组件登记册与健康聚合巡检" />
      <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Card
        className="soft-card"
        title="组件健康汇总"
        extra={
          <Space>
            <ProjectSelect />
            <Button icon={<ReloadOutlined />} onClick={load} disabled={!projectId}>
              刷新
            </Button>
          </Space>
        }
      >
        {!projectId && <Alert type="info" showIcon message="请先选择项目" />}
        {health && (
          <>
            <Row gutter={24} style={{ marginBottom: 16 }}>
              <Col span={6}><Statistic title="组件总数" value={health.total} /></Col>
              <Col span={6}><Statistic title="UP" value={health.up} valueStyle={{ color: '#3f8600' }} /></Col>
              <Col span={6}><Statistic title="DOWN" value={health.down} valueStyle={{ color: '#cf1322' }} /></Col>
              <Col span={6}><Statistic title="UNKNOWN" value={health.unknown} /></Col>
            </Row>
            <Table locale={{ emptyText: <EmptyState small description="暂无组件，点击「登记组件」" /> }} rowKey="code" size="small" columns={healthColumns} dataSource={health.components} pagination={false} />
          </>
        )}
      </Card>

      <Card
        title="组件登记册"
        extra={
          <Space>
            <Select allowClear placeholder="类型筛选" style={{ minWidth: 140 }} value={typeFilter} onChange={setTypeFilter} options={typeOptions} />
            <Select
              allowClear
              placeholder="是否关键"
              style={{ minWidth: 110 }}
              value={criticalFilter}
              onChange={setCriticalFilter}
              options={[
                { value: true, label: '关键' },
                { value: false, label: '非关键' },
              ]}
            />
            <Button type="primary" icon={<PlusOutlined />} disabled={!projectId} onClick={() => setRegisterOpen(true)}>
              登记组件
            </Button>
          </Space>
        }
      >
        <Table rowKey="id" loading={loading} columns={columns} dataSource={data} pagination={false} />
      </Card>

      <Modal title="登记组件" open={registerOpen} onOk={register} onCancel={() => setRegisterOpen(false)} destroyOnClose>
        <Form form={registerForm} layout="vertical" preserve={false} initialValues={{ type: 'SERVICE', critical: false }}>
          <Form.Item name="code" label="编码" rules={[{ required: true, message: '请输入编码' }]}>
            <Input placeholder="如 core-mysql" />
          </Form.Item>
          <Form.Item name="name" label="名称" rules={[{ required: true, message: '请输入名称' }]}>
            <Input />
          </Form.Item>
          <Form.Item name="type" label="类型" rules={[{ required: true }]}>
            <Select options={typeOptions} />
          </Form.Item>
          <Form.Item name="ownerTeam" label="负责团队">
            <Input />
          </Form.Item>
          <Form.Item name="healthEndpoint" label="健康检查端点">
            <Input placeholder="如 http://mysql-exporter:9104/health" />
          </Form.Item>
          <Form.Item name="critical" label="关键组件" valuePropName="checked">
            <Switch />
          </Form.Item>
          <Form.Item name="deps" label="依赖组件编码（可多选输入，回车添加）">
            <Select mode="tags" placeholder="输入依赖的组件编码" options={[]} />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <Input.TextArea rows={2} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal title={`编辑组件：${editTarget?.code ?? ''}`} open={editTarget !== null} onOk={update} onCancel={() => setEditTarget(null)} destroyOnClose>
        <Form form={editForm} layout="vertical" preserve={false}>
          <Form.Item name="name" label="名称">
            <Input />
          </Form.Item>
          <Form.Item name="ownerTeam" label="负责团队">
            <Input />
          </Form.Item>
          <Form.Item name="healthEndpoint" label="健康检查端点">
            <Input />
          </Form.Item>
          <Form.Item name="critical" label="关键组件" valuePropName="checked">
            <Switch />
          </Form.Item>
          <Form.Item name="deps" label="依赖组件编码（可多选输入，回车添加）">
            <Select mode="tags" options={[]} />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <Input.TextArea rows={2} />
          </Form.Item>
        </Form>
      </Modal>
    </Space>
    </>
  );
}
