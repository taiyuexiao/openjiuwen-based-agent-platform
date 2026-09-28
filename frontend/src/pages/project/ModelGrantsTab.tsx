import { useCallback, useEffect, useState } from 'react';
import { Button, Card, Form, Modal, Popconfirm, Select, Space, Table, Tag, Typography, message } from 'antd';
import { PlusOutlined, ReloadOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import { api } from '../../api/client';
import JsonTextArea, { jsonRule } from '../../components/JsonTextArea';
import JsonView from '../../components/JsonView';
import type { EffectiveModel, ModelService, ProjectModelGrant } from '../../types';
import { fmtTime, parseJson } from '../../utils/format';

/** 项目模型授权：grant / revoke / list + effective-models 合并结果 */
export default function ModelGrantsTab({ projectId }: { projectId: number }) {
  const [grants, setGrants] = useState<ProjectModelGrant[]>([]);
  const [effective, setEffective] = useState<EffectiveModel[]>([]);
  const [services, setServices] = useState<ModelService[]>([]);
  const [loading, setLoading] = useState(false);
  const [grantOpen, setGrantOpen] = useState(false);
  const [form] = Form.useForm<{ modelServiceId: number; paramPolicy?: string }>();

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [g, e, s] = await Promise.all([
        api<ProjectModelGrant[]>(`/v1/api/projects/${projectId}/model-grants/list`),
        api<EffectiveModel[]>(`/v1/api/projects/${projectId}/model-grants/effective-models`),
        api<ModelService[]>('/v1/api/model-services/list'),
      ]);
      setGrants(g);
      setEffective(e);
      setServices(s);
    } catch {
      // helper 已提示
    } finally {
      setLoading(false);
    }
  }, [projectId]);

  useEffect(() => {
    load();
  }, [load]);

  const grant = async () => {
    const values = await form.validateFields();
    await api(`/v1/api/projects/${projectId}/model-grants/grant`, {
      modelServiceId: values.modelServiceId,
      paramPolicy: values.paramPolicy?.trim() ? parseJson(values.paramPolicy) : undefined,
    });
    message.success('授权成功');
    setGrantOpen(false);
    form.resetFields();
    load();
  };

  const revoke = async (modelServiceId: number) => {
    await api(`/v1/api/projects/${projectId}/model-grants/revoke`, { modelServiceId });
    message.success('已吊销');
    load();
  };

  const serviceName = (id: number) => {
    const s = services.find((x) => x.id === id);
    return s ? `${s.modelCode}${s.displayName ? `（${s.displayName}）` : ''}` : `#${id}`;
  };

  const grantColumns: ColumnsType<ProjectModelGrant> = [
    { title: 'ID', dataIndex: 'id', width: 70 },
    { title: '模型服务', dataIndex: 'modelServiceId', render: serviceName },
    {
      title: '参数策略 paramPolicy',
      dataIndex: 'paramPolicy',
      render: (v?: string) => <JsonView value={v} title="参数策略" />,
    },
    { title: '授权人', dataIndex: 'grantedBy', width: 100 },
    { title: '授权时间', dataIndex: 'createdAt', width: 170, render: fmtTime },
    {
      title: '操作',
      width: 100,
      render: (_, r) => (
        <Popconfirm title="确认吊销该模型授权？" onConfirm={() => revoke(r.modelServiceId)}>
          <Typography.Link type="danger">吊销</Typography.Link>
        </Popconfirm>
      ),
    },
  ];

  const effectiveColumns: ColumnsType<EffectiveModel> = [
    { title: '模型编码', dataIndex: 'modelCode' },
    { title: '显示名', dataIndex: 'displayName', render: (v?: string) => v ?? '-' },
    { title: 'Provider', dataIndex: 'providerCode', render: (v: string, r) => `${v}（${r.providerType}）` },
    { title: 'Endpoint', dataIndex: 'endpoint', ellipsis: true, render: (v?: string) => v ?? '-' },
    { title: '认证', dataIndex: 'authType', width: 180 },
    {
      title: '合并后参数',
      dataIndex: 'effectiveParams',
      render: (v?: Record<string, unknown>) =>
        v ? <JsonView value={JSON.stringify(v)} title="effectiveParams" /> : '-',
    },
    {
      title: '被策略移除的键',
      dataIndex: 'removedKeys',
      width: 150,
      render: (v?: string[]) => (v && v.length > 0 ? v.map((k) => <Tag key={k}>{k}</Tag>) : '-'),
    },
  ];

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Space>
        <Button type="primary" icon={<PlusOutlined />} onClick={() => setGrantOpen(true)}>
          授权模型
        </Button>
        <Button icon={<ReloadOutlined />} onClick={load}>
          刷新
        </Button>
      </Space>
      <Card size="small" title="授权列表">
        <Table rowKey="id" loading={loading} columns={grantColumns} dataSource={grants} pagination={false} />
      </Card>
      <Card size="small" title="项目生效模型（effective-models：授权 + 参数策略合并结果）">
        <Table
          rowKey="modelServiceId"
          loading={loading}
          columns={effectiveColumns}
          dataSource={effective}
          pagination={false}
        />
      </Card>

      <Modal title="授权模型服务" open={grantOpen} onOk={grant} onCancel={() => setGrantOpen(false)} destroyOnClose>
        <Form form={form} layout="vertical" preserve={false}>
          <Form.Item name="modelServiceId" label="模型服务" rules={[{ required: true, message: '请选择模型服务' }]}>
            <Select
              showSearch
              optionFilterProp="label"
              placeholder="选择平台目录中的模型服务"
              options={services.map((s) => ({
                value: s.id,
                label: `${s.modelCode}${s.displayName ? `（${s.displayName}）` : ''}${s.status === 'DISABLED' ? ' [已禁用]' : ''}`,
              }))}
            />
          </Form.Item>
          <Form.Item
            name="paramPolicy"
            label="参数策略（JSON，可选；键为允许的参数白名单，value 可带 value/min/max 覆盖与边界）"
            rules={[jsonRule()]}
          >
            <JsonTextArea
              rows={4}
              placeholder={'{\n  "temperature": { "min": 0, "max": 1 },\n  "max_tokens": { "value": 4096 }\n}'}
            />
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  );
}
