import { useCallback, useEffect, useState } from 'react';
import { Button, Card, Form, Input, Modal, Popconfirm, Select, Space, Table, Tag, Typography, message } from 'antd';
import { PlusOutlined, ReloadOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import EmptyState from '../../components/EmptyState';
import PageHeader from '../../components/PageHeader';
import { api } from '../../api/client';
import type { AuthType, ModelProvider, ProviderType } from '../../types';
import { fmtTime } from '../../utils/format';

/** 模型 Provider 平台目录 */
export default function ModelProviders() {
  const [data, setData] = useState<ModelProvider[]>([]);
  const [loading, setLoading] = useState(false);
  const [createOpen, setCreateOpen] = useState(false);
  const [form] = Form.useForm<{
    code: string;
    name: string;
    providerType: ProviderType;
    endpoint?: string;
    authType: AuthType;
    credentialRef?: string;
  }>();

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setData(await api<ModelProvider[]>('/v1/api/model-providers/list'));
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
    await api<ModelProvider>('/v1/api/model-providers/create', values);
    message.success('Provider 创建成功');
    setCreateOpen(false);
    form.resetFields();
    load();
  };

  const disable = async (id: number) => {
    await api<ModelProvider>('/v1/api/model-providers/disable', { id });
    message.success('已禁用');
    load();
  };

  const columns: ColumnsType<ModelProvider> = [
    { title: 'ID', dataIndex: 'id', width: 70 },
    { title: '编码', dataIndex: 'code', width: 140 },
    { title: '名称', dataIndex: 'name' },
    { title: '类型', dataIndex: 'providerType', width: 150 },
    { title: 'Endpoint', dataIndex: 'endpoint', ellipsis: true, render: (v?: string) => v ?? '-' },
    { title: '认证方式', dataIndex: 'authType', width: 200 },
    { title: '凭证引用', dataIndex: 'credentialRef', width: 140, render: (v?: string) => v ?? '-' },
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
          <Popconfirm title="确认禁用该 Provider？" onConfirm={() => disable(r.id)}>
            <Typography.Link type="danger">禁用</Typography.Link>
          </Popconfirm>
        ) : null,
    },
  ];

  return (
    <>
      <PageHeader title="模型 Provider" subTitle="平台级模型供应商目录" />
      <Card
      className="soft-card"
      extra={
        <Space>
          <Button icon={<ReloadOutlined />} onClick={load}>
            刷新
          </Button>
          <Button type="primary" icon={<PlusOutlined />} onClick={() => setCreateOpen(true)}>
            创建 Provider
          </Button>
        </Space>
      }
    >
      <Table locale={{ emptyText: <EmptyState small description="暂无模型 Provider，点击右上角创建" /> }} rowKey="id" loading={loading} columns={columns} dataSource={data} pagination={false} />
      <Modal title="创建模型 Provider" open={createOpen} onOk={create} onCancel={() => setCreateOpen(false)} destroyOnClose>
        <Form form={form} layout="vertical" preserve={false} initialValues={{ providerType: 'OPENAI_COMPAT', authType: 'CREDENTIAL_REF' }}>
          <Form.Item
            name="code"
            label="编码"
            rules={[
              { required: true, message: '请输入编码' },
              { pattern: /^[A-Za-z0-9-]+$/, message: '仅允许字母、数字、中划线' },
              { max: 64 },
            ]}
          >
            <Input placeholder="如 openai-main" />
          </Form.Item>
          <Form.Item name="name" label="名称" rules={[{ required: true }, { max: 128 }]}>
            <Input />
          </Form.Item>
          <Form.Item name="providerType" label="Provider 类型" rules={[{ required: true }]}>
            <Select
              options={[
                { value: 'OPENAI_COMPAT', label: 'OPENAI_COMPAT（OpenAI 兼容）' },
                { value: 'CUSTOM', label: 'CUSTOM（自定义）' },
              ]}
            />
          </Form.Item>
          <Form.Item name="endpoint" label="Endpoint" rules={[{ max: 512 }]}>
            <Input placeholder="如 https://api.openai.com/v1" />
          </Form.Item>
          <Form.Item name="authType" label="认证方式" rules={[{ required: true }]}>
            <Select
              options={[
                { value: 'CREDENTIAL_REF', label: 'CREDENTIAL_REF（凭证引用）' },
                { value: 'GATEWAY_PASSTHROUGH', label: 'GATEWAY_PASSTHROUGH（网关透传）' },
              ]}
            />
          </Form.Item>
          <Form.Item name="credentialRef" label="凭证引用" rules={[{ max: 256 }]}>
            <Input placeholder="如 vault://agentops/openai-key" />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
    </>
  );
}
