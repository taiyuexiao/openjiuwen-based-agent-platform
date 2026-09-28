import { useCallback, useEffect, useState } from 'react';
import {
  Button,
  Card,
  Form,
  Input,
  Modal,
  Popconfirm,
  Select,
  Space,
  Table,
  Typography,
  message,
} from 'antd';
import { PlusOutlined, ReloadOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import { api } from '../../api/client';
import JsonTextArea, { jsonRule } from '../../components/JsonTextArea';
import JsonView from '../../components/JsonView';
import type { Agent, KnowledgeBase, KnowledgeBaseGrant, KnowledgeBaseRef } from '../../types';
import { fmtTime, parseJson } from '../../utils/format';

/** 项目知识库授权：kb-grants（grant/revoke/list） + kb-refs（bind/unbind/list） */
export default function KbGrantsTab({ projectId }: { projectId: number }) {
  const [grants, setGrants] = useState<KnowledgeBaseGrant[]>([]);
  const [refs, setRefs] = useState<KnowledgeBaseRef[]>([]);
  const [kbs, setKbs] = useState<KnowledgeBase[]>([]);
  const [agents, setAgents] = useState<Agent[]>([]);
  const [loading, setLoading] = useState(false);
  const [grantOpen, setGrantOpen] = useState(false);
  const [bindOpen, setBindOpen] = useState(false);
  const [grantForm] = Form.useForm<{ kbId: number; scope?: string }>();
  const [bindForm] = Form.useForm<{ kbId: number; agentId?: number; refVersion?: string }>();

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [g, r, k, a] = await Promise.all([
        api<KnowledgeBaseGrant[]>(`/v1/api/projects/${projectId}/kb-grants/list`),
        api<KnowledgeBaseRef[]>(`/v1/api/projects/${projectId}/kb-refs/list`),
        api<KnowledgeBase[]>('/v1/api/knowledge-bases/list'),
        api<Agent[]>('/v1/api/agents/list', { projectId }),
      ]);
      setGrants(g);
      setRefs(r);
      setKbs(k);
      setAgents(a);
    } catch {
      // helper 已提示
    } finally {
      setLoading(false);
    }
  }, [projectId]);

  useEffect(() => {
    load();
  }, [load]);

  const kbName = (id: number) => {
    const k = kbs.find((x) => x.id === id);
    return k ? `${k.code}（${k.name}）` : `#${id}`;
  };

  const grant = async () => {
    const values = await grantForm.validateFields();
    await api(`/v1/api/projects/${projectId}/kb-grants/grant`, {
      kbId: values.kbId,
      scope: values.scope?.trim() ? parseJson(values.scope) : undefined,
    });
    message.success('知识库授权成功');
    setGrantOpen(false);
    grantForm.resetFields();
    load();
  };

  const revoke = async (kbId: number) => {
    await api(`/v1/api/projects/${projectId}/kb-grants/revoke`, { kbId });
    message.success('已吊销');
    load();
  };

  const bind = async () => {
    const values = await bindForm.validateFields();
    await api(`/v1/api/projects/${projectId}/kb-refs/bind`, {
      kbId: values.kbId,
      agentId: values.agentId ?? undefined,
      refVersion: values.refVersion?.trim() || undefined,
    });
    message.success('引用绑定成功');
    setBindOpen(false);
    bindForm.resetFields();
    load();
  };

  const unbind = async (refId: number) => {
    await api(`/v1/api/projects/${projectId}/kb-refs/unbind`, { refId });
    message.success('已解绑');
    load();
  };

  const grantColumns: ColumnsType<KnowledgeBaseGrant> = [
    { title: 'ID', dataIndex: 'id', width: 70 },
    { title: '知识库', dataIndex: 'kbId', render: kbName },
    {
      title: '授权范围 scope',
      dataIndex: 'grantScope',
      render: (v?: string) => <JsonView value={v} title="授权范围" />,
    },
    { title: '授权人', dataIndex: 'grantedBy', width: 100 },
    { title: '授权时间', dataIndex: 'createdAt', width: 170, render: fmtTime },
    {
      title: '操作',
      width: 100,
      render: (_, r) => (
        <Popconfirm title="确认吊销该知识库授权？" onConfirm={() => revoke(r.kbId)}>
          <Typography.Link type="danger">吊销</Typography.Link>
        </Popconfirm>
      ),
    },
  ];

  const refColumns: ColumnsType<KnowledgeBaseRef> = [
    { title: 'ID', dataIndex: 'id', width: 70 },
    { title: '知识库', dataIndex: 'kbId', render: kbName },
    {
      title: '绑定 Agent',
      dataIndex: 'agentId',
      width: 160,
      render: (v?: number) => {
        if (v === undefined || v === null) return '（项目级）';
        const a = agents.find((x) => x.id === v);
        return a ? `${a.code}（${a.name}）` : `#${v}`;
      },
    },
    { title: '引用版本', dataIndex: 'refVersion', width: 120, render: (v?: string) => v ?? '-' },
    { title: '绑定人', dataIndex: 'createdBy', width: 100 },
    { title: '绑定时间', dataIndex: 'createdAt', width: 170, render: fmtTime },
    {
      title: '操作',
      width: 100,
      render: (_, r) => (
        <Popconfirm title="确认解绑该引用？" onConfirm={() => unbind(r.id)}>
          <Typography.Link type="danger">解绑</Typography.Link>
        </Popconfirm>
      ),
    },
  ];

  const kbOptions = kbs.map((k) => ({
    value: k.id,
    label: `${k.code}（${k.name}）${k.status === 'DISABLED' ? ' [已禁用]' : ''}`,
  }));

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Space>
        <Button type="primary" icon={<PlusOutlined />} onClick={() => setGrantOpen(true)}>
          授权知识库
        </Button>
        <Button icon={<PlusOutlined />} onClick={() => setBindOpen(true)}>
          绑定引用
        </Button>
        <Button icon={<ReloadOutlined />} onClick={load}>
          刷新
        </Button>
      </Space>
      <Card size="small" title="授权列表">
        <Table rowKey="id" loading={loading} columns={grantColumns} dataSource={grants} pagination={false} />
      </Card>
      <Card size="small" title="引用绑定（kb-refs：知识库 - 项目 - Agent 关联）">
        <Table rowKey="id" loading={loading} columns={refColumns} dataSource={refs} pagination={false} />
      </Card>

      <Modal title="授权知识库" open={grantOpen} onOk={grant} onCancel={() => setGrantOpen(false)} destroyOnClose>
        <Form form={grantForm} layout="vertical" preserve={false}>
          <Form.Item name="kbId" label="知识库" rules={[{ required: true, message: '请选择知识库' }]}>
            <Select showSearch optionFilterProp="label" options={kbOptions} placeholder="选择平台目录中的知识库" />
          </Form.Item>
          <Form.Item name="scope" label="授权范围 scope（JSON，可选）" rules={[jsonRule()]}>
            <JsonTextArea rows={3} placeholder={'{\n  "collections": ["default"]\n}'} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal title="绑定知识库引用" open={bindOpen} onOk={bind} onCancel={() => setBindOpen(false)} destroyOnClose>
        <Form form={bindForm} layout="vertical" preserve={false}>
          <Form.Item name="kbId" label="知识库" rules={[{ required: true, message: '请选择知识库' }]}>
            <Select showSearch optionFilterProp="label" options={kbOptions} placeholder="须先完成知识库授权" />
          </Form.Item>
          <Form.Item name="agentId" label="绑定 Agent（可选，留空为项目级引用）">
            <Select
              allowClear
              showSearch
              optionFilterProp="label"
              placeholder="选择本项目 Agent"
              options={agents.map((a) => ({ value: a.id, label: `${a.code}（${a.name}）` }))}
            />
          </Form.Item>
          <Form.Item name="refVersion" label="引用版本（可选）" rules={[{ max: 64 }]}>
            <Input placeholder="如 v1 / 2024Q3" />
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  );
}
