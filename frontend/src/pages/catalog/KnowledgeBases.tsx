import { useCallback, useEffect, useState } from 'react';
import { Button, Card, Form, Input, Modal, Popconfirm, Space, Table, Tag, Typography, message } from 'antd';
import { PlusOutlined, ReloadOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import EmptyState from '../../components/EmptyState';
import PageHeader from '../../components/PageHeader';
import { api } from '../../api/client';
import JsonTextArea, { jsonRule } from '../../components/JsonTextArea';
import JsonView from '../../components/JsonView';
import type { KnowledgeBase } from '../../types';
import { fmtTime, parseJson } from '../../utils/format';

/** 知识库平台目录 */
export default function KnowledgeBases() {
  const [data, setData] = useState<KnowledgeBase[]>([]);
  const [loading, setLoading] = useState(false);
  const [createOpen, setCreateOpen] = useState(false);
  const [form] = Form.useForm<{
    code: string;
    name: string;
    kbType: string;
    endpoint?: string;
    connectionConfig?: string;
  }>();

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setData(await api<KnowledgeBase[]>('/v1/api/knowledge-bases/list'));
    } catch {
      // helper 已提示
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const create = async () => {
    const values = await form.validateFields();
    await api<KnowledgeBase>('/v1/api/knowledge-bases/create', {
      code: values.code,
      name: values.name,
      kbType: values.kbType,
      endpoint: values.endpoint?.trim() || undefined,
      connectionConfig: values.connectionConfig?.trim() ? parseJson(values.connectionConfig) : undefined,
    });
    message.success('知识库创建成功');
    setCreateOpen(false);
    form.resetFields();
    load();
  };

  const disable = async (id: number) => {
    await api<KnowledgeBase>('/v1/api/knowledge-bases/disable', { id });
    message.success('已禁用');
    load();
  };

  const columns: ColumnsType<KnowledgeBase> = [
    { title: 'ID', dataIndex: 'id', width: 70 },
    { title: '编码', dataIndex: 'code', width: 140 },
    { title: '名称', dataIndex: 'name' },
    { title: '类型', dataIndex: 'kbType', width: 140 },
    { title: 'Endpoint', dataIndex: 'endpoint', ellipsis: true, render: (v?: string) => v ?? '-' },
    {
      title: '连接配置',
      dataIndex: 'connectionConfig',
      render: (v?: string) => <JsonView value={v} title="连接配置" />,
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (s: string) => <Tag color={s === 'ENABLED' ? 'green' : 'default'}>{s}</Tag>,
    },
    { title: '创建时间', dataIndex: 'createdAt', width: 170, render: fmtTime },
    {
      title: '操作',
      width: 100,
      render: (_, r) =>
        r.status === 'ENABLED' ? (
          <Popconfirm title="确认禁用该知识库？" onConfirm={() => disable(r.id)}>
            <Typography.Link type="danger">禁用</Typography.Link>
          </Popconfirm>
        ) : null,
    },
  ];

  return (
    <>
      <PageHeader title="知识库" subTitle="平台级知识库目录；项目通过「知识库授权」获得使用权" />
      <Card
      className="soft-card"
      extra={
        <Space>
          <Button icon={<ReloadOutlined />} onClick={load}>
            刷新
          </Button>
          <Button type="primary" icon={<PlusOutlined />} onClick={() => setCreateOpen(true)}>
            创建知识库
          </Button>
        </Space>
      }
    >
      <Table locale={{ emptyText: <EmptyState small description="暂无知识库，点击右上角创建" /> }} rowKey="id" loading={loading} columns={columns} dataSource={data} pagination={false} />
      <Modal title="创建知识库" open={createOpen} onOk={create} onCancel={() => setCreateOpen(false)} destroyOnClose>
        <Form form={form} layout="vertical" preserve={false}>
          <Form.Item
            name="code"
            label="编码"
            rules={[
              { required: true, message: '请输入编码' },
              { pattern: /^[A-Za-z0-9-]+$/, message: '仅允许字母、数字、中划线' },
              { max: 64 },
            ]}
          >
            <Input placeholder="如 policy-kb" />
          </Form.Item>
          <Form.Item name="name" label="名称" rules={[{ required: true }, { max: 128 }]}>
            <Input />
          </Form.Item>
          <Form.Item name="kbType" label="类型" rules={[{ required: true }, { max: 64 }]}>
            <Input placeholder="如 VECTOR / GRAPH / FULLTEXT" />
          </Form.Item>
          <Form.Item name="endpoint" label="Endpoint" rules={[{ max: 512 }]}>
            <Input />
          </Form.Item>
          <Form.Item name="connectionConfig" label="连接配置（JSON，可选）" rules={[jsonRule()]}>
            <JsonTextArea rows={4} placeholder={'{\n  "index": "policy",\n  "dimension": 1024\n}'} />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
    </>
  );
}
