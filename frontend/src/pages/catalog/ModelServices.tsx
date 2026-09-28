import { useCallback, useEffect, useState } from 'react';
import { Button, Card, Form, Input, Modal, Popconfirm, Select, Space, Table, Tag, Typography, message } from 'antd';
import { PlusOutlined, ReloadOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import EmptyState from '../../components/EmptyState';
import PageHeader from '../../components/PageHeader';
import { api } from '../../api/client';
import JsonTextArea, { jsonRule } from '../../components/JsonTextArea';
import JsonView from '../../components/JsonView';
import type { ModelProvider, ModelService } from '../../types';
import { fmtTime, parseJson } from '../../utils/format';

/** 模型服务目录 */
export default function ModelServices() {
  const [data, setData] = useState<ModelService[]>([]);
  const [providers, setProviders] = useState<ModelProvider[]>([]);
  const [providerFilter, setProviderFilter] = useState<number | undefined>();
  const [loading, setLoading] = useState(false);
  const [createOpen, setCreateOpen] = useState(false);
  const [form] = Form.useForm<{
    providerId: number;
    modelCode: string;
    displayName?: string;
    capabilities?: string;
    defaultParams?: string;
  }>();

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [services, providerList] = await Promise.all([
        api<ModelService[]>('/v1/api/model-services/list', providerFilter ? { providerId: providerFilter } : {}),
        api<ModelProvider[]>('/v1/api/model-providers/list'),
      ]);
      setData(services);
      setProviders(providerList);
    } catch {
      // helper 已提示
    } finally {
      setLoading(false);
    }
  }, [providerFilter]);

  useEffect(() => {
    load();
  }, [load]);

  const create = async () => {
    const values = await form.validateFields();
    await api<ModelService>('/v1/api/model-services/create', {
      providerId: values.providerId,
      modelCode: values.modelCode,
      displayName: values.displayName?.trim() || undefined,
      capabilities: values.capabilities?.trim() ? parseJson(values.capabilities) : undefined,
      defaultParams: values.defaultParams?.trim() ? parseJson(values.defaultParams) : undefined,
    });
    message.success('模型服务创建成功');
    setCreateOpen(false);
    form.resetFields();
    load();
  };

  const disable = async (id: number) => {
    await api<ModelService>('/v1/api/model-services/disable', { id });
    message.success('已禁用');
    load();
  };

  const providerName = (id: number) => providers.find((p) => p.id === id)?.code ?? `#${id}`;

  const columns: ColumnsType<ModelService> = [
    { title: 'ID', dataIndex: 'id', width: 70 },
    { title: '模型编码', dataIndex: 'modelCode', width: 180 },
    { title: '显示名', dataIndex: 'displayName', render: (v?: string) => v ?? '-' },
    { title: 'Provider', dataIndex: 'providerId', width: 140, render: providerName },
    {
      title: '能力 capabilities',
      dataIndex: 'capabilities',
      render: (v?: string) => <JsonView value={v} title="能力声明" />,
    },
    {
      title: '默认参数',
      dataIndex: 'defaultParams',
      render: (v?: string) => <JsonView value={v} title="默认参数" />,
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
          <Popconfirm title="确认禁用该模型服务？" onConfirm={() => disable(r.id)}>
            <Typography.Link type="danger">禁用</Typography.Link>
          </Popconfirm>
        ) : null,
    },
  ];

  return (
    <>
      <PageHeader title="模型服务" subTitle="平台级模型服务目录；项目通过「模型授权」获得使用权" />
      <Card
      className="soft-card"
      extra={
        <Space>
          <Select
            allowClear
            placeholder="按 Provider 筛选"
            style={{ minWidth: 200 }}
            value={providerFilter}
            onChange={setProviderFilter}
            options={providers.map((p) => ({ value: p.id, label: `${p.code}（${p.name}）` }))}
          />
          <Button icon={<ReloadOutlined />} onClick={load}>
            刷新
          </Button>
          <Button type="primary" icon={<PlusOutlined />} onClick={() => setCreateOpen(true)}>
            创建模型服务
          </Button>
        </Space>
      }
    >
      <Table locale={{ emptyText: <EmptyState small description="暂无模型服务，点击右上角创建" /> }} rowKey="id" loading={loading} columns={columns} dataSource={data} pagination={false} />
      <Modal title="创建模型服务" open={createOpen} onOk={create} onCancel={() => setCreateOpen(false)} destroyOnClose>
        <Form form={form} layout="vertical" preserve={false}>
          <Form.Item name="providerId" label="所属 Provider" rules={[{ required: true, message: '请选择 Provider' }]}>
            <Select
              showSearch
              optionFilterProp="label"
              options={providers.map((p) => ({ value: p.id, label: `${p.code}（${p.name}）` }))}
            />
          </Form.Item>
          <Form.Item name="modelCode" label="模型编码" rules={[{ required: true }, { max: 128 }]}>
            <Input placeholder="如 gpt-4o-mini" />
          </Form.Item>
          <Form.Item name="displayName" label="显示名" rules={[{ max: 128 }]}>
            <Input />
          </Form.Item>
          <Form.Item name="capabilities" label="能力声明（JSON，可选）" rules={[jsonRule()]}>
            <JsonTextArea rows={3} placeholder={'{\n  "vision": true,\n  "tools": true,\n  "contextWindow": 128000\n}'} />
          </Form.Item>
          <Form.Item name="defaultParams" label="默认参数（JSON，可选）" rules={[jsonRule()]}>
            <JsonTextArea rows={3} placeholder={'{\n  "temperature": 0.7,\n  "max_tokens": 4096\n}'} />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
    </>
  );
}
