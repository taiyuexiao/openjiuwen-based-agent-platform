import { useCallback, useEffect, useState } from 'react';
import { Alert, Button, Card, Form, Input, Modal, Popconfirm, Select, Space, Table, Tag, Typography, message } from 'antd';
import { PlusOutlined, ReloadOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import { api } from '../../api/client';
import EmptyState from '../../components/EmptyState';
import PageHeader from '../../components/PageHeader';
import ProjectSelect from '../../components/ProjectSelect';
import { useProject } from '../../context/ProjectContext';
import type { Agent, AgentLevel, AgentLevelValue } from '../../types';
import { fmtTime } from '../../utils/format';

const LEVEL_COLORS: Record<string, string> = {
  P0: 'red',
  P1: 'orange',
  P2: 'blue',
  P3: 'default',
};

/** Agent 分级：confirm / disable / list */
export default function AgentLevels() {
  const { projectId } = useProject();
  const [agents, setAgents] = useState<Agent[]>([]);
  const [agentId, setAgentId] = useState<number | undefined>();
  const [data, setData] = useState<AgentLevel[]>([]);
  const [loading, setLoading] = useState(false);
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [form] = Form.useForm<{ level: AgentLevelValue; source?: string }>();

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
      setData(await api<AgentLevel[]>('/v1/api/agent-levels/list', { projectId, agentId }));
    } catch {
      // helper 已提示
    } finally {
      setLoading(false);
    }
  }, [projectId, agentId]);

  useEffect(() => {
    load();
  }, [load]);

  const confirm = async () => {
    if (!projectId || !agentId) return;
    const values = await form.validateFields();
    await api<AgentLevel>('/v1/api/agent-levels/confirm', {
      projectId,
      agentId,
      level: values.level,
      source: values.source?.trim() || undefined,
    });
    message.success('分级已确认');
    setConfirmOpen(false);
    form.resetFields();
    load();
  };

  const disable = async () => {
    if (!projectId || !agentId) return;
    await api('/v1/api/agent-levels/disable', { projectId, agentId });
    message.success('已停用当前生效分级');
    load();
  };

  const columns: ColumnsType<AgentLevel> = [
    { title: 'ID', dataIndex: 'id', width: 70 },
    {
      title: '等级',
      dataIndex: 'level',
      width: 100,
      render: (l: string) => <Tag color={LEVEL_COLORS[l]}>{l}</Tag>,
    },
    {
      title: '生效中',
      dataIndex: 'effective',
      width: 100,
      render: (v?: boolean) => (v ? <Tag color="green">生效</Tag> : '历史'),
    },
    { title: '来源', dataIndex: 'source', render: (v?: string) => v ?? '-' },
    { title: '确认人', dataIndex: 'confirmedBy', width: 100 },
    { title: '确认时间', dataIndex: 'confirmedAt', width: 170, render: fmtTime },
  ];

  return (
    <>
      <PageHeader title="Agent 分级" subTitle="P0~P3 运行保障等级；发布门禁与部署参数按等级快照取用" />
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
          onClick={() => setConfirmOpen(true)}
        >
          确认分级
        </Button>
        <Popconfirm title="确认停用该 Agent 当前生效的分级？" onConfirm={disable}>
          <Button danger disabled={!projectId || !agentId || !data.some((d) => d.effective)}>
            停用分级
          </Button>
        </Popconfirm>
      </Space>
      {(!projectId || !agentId) && (
        <Alert style={{ marginBottom: 16 }} type="info" showIcon message="请选择项目与 Agent 以查看分级记录" />
      )}
      <Table locale={{ emptyText: <EmptyState small description="暂无分级记录，点击「确认分级」录入" /> }} rowKey="id" loading={loading} columns={columns} dataSource={data} pagination={false} />

      <Modal title="确认 Agent 分级" open={confirmOpen} onOk={confirm} onCancel={() => setConfirmOpen(false)} destroyOnClose>
        <Form form={form} layout="vertical" preserve={false} initialValues={{ level: 'P2' }}>
          <Form.Item name="level" label="等级" rules={[{ required: true }]}>
            <Select
              options={[
                { value: 'P0', label: 'P0（最高保障：4 副本 / 4C8G）' },
                { value: 'P1', label: 'P1（2 副本 / 2C4G）' },
                { value: 'P2', label: 'P2（1 副本 / 1C2G）' },
                { value: 'P3', label: 'P3（1 副本 / 0.5C1G）' },
              ]}
            />
          </Form.Item>
          <Form.Item name="source" label="分级来源（可选）" rules={[{ max: 256 }]}>
            <Input placeholder="如 架构评审-2026Q3 / 人工确认" />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
    </>
  );
}
