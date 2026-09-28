import { useCallback, useEffect, useState } from 'react';
import { Alert, Button, Card, DatePicker, Form, Input, Modal, Select, Space, Table, message } from 'antd';
import { PlusOutlined, ReloadOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import dayjs, { type Dayjs } from 'dayjs';
import { api } from '../../api/client';
import EmptyState from '../../components/EmptyState';
import PageHeader from '../../components/PageHeader';
import ProjectSelect from '../../components/ProjectSelect';
import { useProject } from '../../context/ProjectContext';
import type { Agent, Artifact } from '../../types';
import { fmtTime } from '../../utils/format';

/** 制品登记与查询（制品不可变，无 update） */
export default function Artifacts() {
  const { projectId } = useProject();
  const [agents, setAgents] = useState<Agent[]>([]);
  const [agentId, setAgentId] = useState<number | undefined>();
  const [data, setData] = useState<Artifact[]>([]);
  const [loading, setLoading] = useState(false);
  const [registerOpen, setRegisterOpen] = useState(false);
  const [form] = Form.useForm<{
    agentVersion: string;
    codeCommit?: string;
    imageDigest?: string;
    configDigest?: string;
    evaluationRef?: string;
    builtAt?: Dayjs;
  }>();

  useEffect(() => {
    if (!projectId) {
      setAgents([]);
      setAgentId(undefined);
      return;
    }
    api<Agent[]>('/v1/api/agents/list', { projectId })
      .then(setAgents)
      .catch(() => undefined);
  }, [projectId]);

  const load = useCallback(async () => {
    if (!projectId || !agentId) {
      setData([]);
      return;
    }
    setLoading(true);
    try {
      setData(await api<Artifact[]>('/v1/api/artifacts/list', { projectId, agentId }));
    } catch {
      // helper 已提示
    } finally {
      setLoading(false);
    }
  }, [projectId, agentId]);

  useEffect(() => {
    load();
  }, [load]);

  const register = async () => {
    if (!projectId || !agentId) return;
    const values = await form.validateFields();
    await api<Artifact>('/v1/api/artifacts/register', {
      projectId,
      agentId,
      agentVersion: values.agentVersion,
      codeCommit: values.codeCommit?.trim() || undefined,
      imageDigest: values.imageDigest?.trim() || undefined,
      configDigest: values.configDigest?.trim() || undefined,
      evaluationRef: values.evaluationRef?.trim() || undefined,
      builtAt: values.builtAt ? values.builtAt.format('YYYY-MM-DDTHH:mm:ss') : undefined,
    });
    message.success('制品登记成功');
    setRegisterOpen(false);
    form.resetFields();
    load();
  };

  const columns: ColumnsType<Artifact> = [
    { title: 'ID', dataIndex: 'id', width: 70 },
    { title: 'Agent 版本', dataIndex: 'agentVersion', width: 110 },
    { title: '代码提交', dataIndex: 'codeCommit', width: 130, ellipsis: true, render: (v?: string) => v ?? '-' },
    { title: '镜像摘要', dataIndex: 'imageDigest', ellipsis: true, render: (v?: string) => v ?? '-' },
    { title: '配置摘要', dataIndex: 'configDigest', width: 140, ellipsis: true, render: (v?: string) => v ?? '-' },
    { title: '评测引用', dataIndex: 'evaluationRef', width: 130, render: (v?: string) => v ?? '-' },
    { title: '构建时间', dataIndex: 'builtAt', width: 170, render: fmtTime },
    { title: '登记人', dataIndex: 'createdBy', width: 100 },
    { title: '登记时间', dataIndex: 'createdAt', width: 170, render: fmtTime },
  ];

  return (
    <>
      <PageHeader title="制品" subTitle="制品不可变：登记即固化构建指纹，供发布单引用" />
      <Card className="soft-card">
      <Space wrap style={{ marginBottom: 16 }}>
        <ProjectSelect />
        <Select
          placeholder="选择 Agent"
          style={{ minWidth: 220 }}
          value={agentId}
          onChange={setAgentId}
          showSearch
          optionFilterProp="label"
          disabled={!projectId}
          options={agents.map((a) => ({ value: a.id, label: `${a.code}（${a.name}）` }))}
        />
        <Button icon={<ReloadOutlined />} onClick={load} disabled={!projectId || !agentId}>
          刷新
        </Button>
        <Button
          type="primary"
          icon={<PlusOutlined />}
          disabled={!projectId || !agentId}
          onClick={() => setRegisterOpen(true)}
        >
          登记制品
        </Button>
      </Space>
      {(!projectId || !agentId) && (
        <Alert style={{ marginBottom: 16 }} type="info" showIcon message="请选择项目与 Agent 以加载制品列表" />
      )}
      <Table locale={{ emptyText: <EmptyState small description="暂无制品，选择 Agent 后点击「登记制品」" /> }} rowKey="id" loading={loading} columns={columns} dataSource={data} pagination={{ pageSize: 20 }} />

      <Modal title="登记制品" open={registerOpen} onOk={register} onCancel={() => setRegisterOpen(false)} destroyOnClose>
        <Form form={form} layout="vertical" preserve={false} initialValues={{ builtAt: dayjs() }}>
          <Form.Item
            name="agentVersion"
            label="Agent 版本"
            rules={[
              { required: true, message: '请输入 Agent 版本' },
              { pattern: /^\d+\.\d+\.\d+$/, message: '版本号必须为 x.y.z 格式' },
              { max: 32 },
            ]}
          >
            <Input placeholder="如 1.0.0（须为已登记版本）" />
          </Form.Item>
          <Form.Item name="codeCommit" label="代码提交（commit SHA）" rules={[{ max: 64 }]}>
            <Input placeholder="如 9fceb02..." />
          </Form.Item>
          <Form.Item name="imageDigest" label="镜像摘要" rules={[{ max: 256 }]}>
            <Input placeholder="如 sha256:..." />
          </Form.Item>
          <Form.Item name="configDigest" label="配置摘要" rules={[{ max: 128 }]}>
            <Input />
          </Form.Item>
          <Form.Item name="evaluationRef" label="评测报告引用" rules={[{ max: 128 }]}>
            <Input placeholder="如 eval-report-2026Q3-01" />
          </Form.Item>
          <Form.Item name="builtAt" label="构建时间">
            <DatePicker showTime style={{ width: '100%' }} />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
    </>
  );
}
