import { useCallback, useEffect, useState } from 'react';
import { Alert, Button, Card, Form, Input, InputNumber, Modal, Select, Skeleton, Space, Table, Tag, message } from 'antd';
import { PlusOutlined, ReloadOutlined } from '@ant-design/icons';
import { Link } from 'react-router-dom';
import type { ColumnsType } from 'antd/es/table';
import { api } from '../../api/client';
import EmptyState from '../../components/EmptyState';
import PageHeader from '../../components/PageHeader';
import ProjectSelect from '../../components/ProjectSelect';
import { useProject } from '../../context/ProjectContext';
import type { Asset, AssetStatus, AssetType, AssetVisibility } from '../../types';
import { fmtTime } from '../../utils/format';

const TYPE_COLORS: Record<string, string> = {
  SKILL: 'cyan',
  MCP_SERVICE: 'geekblue',
  MCP_TOOL: 'blue',
  HTTP_API: 'purple',
};

const STATUS_COLORS: Record<string, string> = {
  DRAFT: 'default',
  PUBLISHED: 'green',
  OFFLINE: 'red',
};

/** 资产列表：类型走服务端筛选，状态/可见性为前端筛选（DTO 仅支持 projectId + assetType） */
export default function AssetList() {
  const { projectId } = useProject();
  const [assetType, setAssetType] = useState<AssetType | undefined>();
  const [statusFilter, setStatusFilter] = useState<AssetStatus | undefined>();
  const [visibilityFilter, setVisibilityFilter] = useState<AssetVisibility | undefined>();
  const [data, setData] = useState<Asset[]>([]);
  const [loading, setLoading] = useState(false);
  const [createOpen, setCreateOpen] = useState(false);
  const [form] = Form.useForm<{
    code: string;
    name: string;
    assetType: AssetType;
    parentAssetId?: number;
    visibility: AssetVisibility;
    description?: string;
  }>();

  const load = useCallback(async () => {
    if (!projectId) {
      setData([]);
      return;
    }
    setLoading(true);
    try {
      setData(
        await api<Asset[]>('/v1/api/assets/list', {
          projectId,
          assetType: assetType ?? undefined,
        }),
      );
    } catch {
      // helper 已提示
    } finally {
      setLoading(false);
    }
  }, [projectId, assetType]);

  useEffect(() => {
    load();
  }, [load]);

  const create = async () => {
    if (!projectId) return;
    const values = await form.validateFields();
    await api<Asset>('/v1/api/assets/create', {
      code: values.code,
      name: values.name,
      assetType: values.assetType,
      parentAssetId: values.parentAssetId ?? undefined,
      projectId,
      visibility: values.visibility,
      description: values.description?.trim() || undefined,
    });
    message.success('资产创建成功');
    setCreateOpen(false);
    form.resetFields();
    load();
  };

  const filtered = data.filter(
    (a) =>
      (statusFilter === undefined || a.status === statusFilter) &&
      (visibilityFilter === undefined || a.visibility === visibilityFilter),
  );

  const columns: ColumnsType<Asset> = [
    { title: 'ID', dataIndex: 'id', width: 70 },
    { title: '编码', dataIndex: 'code', width: 180, ellipsis: true },
    {
      title: '名称',
      dataIndex: 'name',
      render: (_, r) => <Link to={`/assets/${r.id}?projectId=${r.ownerProjectId}`}>{r.name}</Link>,
    },
    {
      title: '类型',
      dataIndex: 'assetType',
      width: 130,
      render: (t: string) => <Tag color={TYPE_COLORS[t]}>{t}</Tag>,
    },
    { title: '父资产', dataIndex: 'parentAssetId', width: 90, render: (v?: number) => v ?? '-' },
    {
      title: '可见性',
      dataIndex: 'visibility',
      width: 100,
      render: (v: string) => <Tag color={v === 'SHARED' ? 'orange' : 'default'}>{v}</Tag>,
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 110,
      render: (s: string) => <Tag color={STATUS_COLORS[s]}>{s}</Tag>,
    },
    { title: '归属项目', dataIndex: 'ownerProjectId', width: 100 },
    { title: '创建人', dataIndex: 'createdBy', width: 100 },
    { title: '创建时间', dataIndex: 'createdAt', width: 170, render: fmtTime },
    {
      title: '操作',
      width: 80,
      render: (_, r) => <Link to={`/assets/${r.id}?projectId=${r.ownerProjectId}`}>详情</Link>,
    },
  ];

  return (
    <>
      <PageHeader title="资产列表" subTitle="项目内资产的登记、发布与授权；Skill 包也可通过「Skill 上传」进入" />
      <Card className="soft-card">
      <Space wrap style={{ marginBottom: 16 }}>
        <ProjectSelect />
        <Select
          allowClear
          placeholder="类型筛选"
          style={{ minWidth: 160 }}
          value={assetType}
          onChange={setAssetType}
          options={['SKILL', 'MCP_SERVICE', 'MCP_TOOL', 'HTTP_API'].map((t) => ({ value: t, label: t }))}
        />
        <Select
          allowClear
          placeholder="状态筛选（前端）"
          style={{ minWidth: 150 }}
          value={statusFilter}
          onChange={setStatusFilter}
          options={['DRAFT', 'PUBLISHED', 'OFFLINE'].map((s) => ({ value: s, label: s }))}
        />
        <Select
          allowClear
          placeholder="可见性筛选（前端）"
          style={{ minWidth: 150 }}
          value={visibilityFilter}
          onChange={setVisibilityFilter}
          options={[
            { value: 'PROJECT', label: 'PROJECT（项目内）' },
            { value: 'SHARED', label: 'SHARED（共享）' },
          ]}
        />
        <Button icon={<ReloadOutlined />} onClick={load} disabled={!projectId}>
          刷新
        </Button>
        <Button type="primary" icon={<PlusOutlined />} disabled={!projectId} onClick={() => setCreateOpen(true)}>
          创建资产
        </Button>
      </Space>
      {!projectId && <Alert style={{ marginBottom: 16 }} type="info" showIcon message="请先选择项目以加载资产列表" />}
      {loading && filtered.length === 0 ? (
        <Skeleton active paragraph={{ rows: 6 }} title={false} />
      ) : (
        <Table locale={{ emptyText: <EmptyState small description="当前筛选下暂无资产，可创建资产或上传 Skill" /> }} rowKey="id" columns={columns} dataSource={filtered} pagination={{ pageSize: 20 }} />
      )}

      <Modal title="创建资产" open={createOpen} onOk={create} onCancel={() => setCreateOpen(false)} destroyOnClose>
        <Form form={form} layout="vertical" preserve={false} initialValues={{ assetType: 'SKILL', visibility: 'PROJECT' }}>
          <Form.Item
            name="code"
            label="资产编码"
            rules={[
              { required: true, message: '请输入编码' },
              { pattern: /^[A-Za-z0-9-]+$/, message: '仅允许字母、数字、中划线' },
              { max: 128 },
            ]}
          >
            <Input placeholder="如 refund-skill" />
          </Form.Item>
          <Form.Item name="name" label="名称" rules={[{ required: true }, { max: 128 }]}>
            <Input />
          </Form.Item>
          <Form.Item name="assetType" label="类型" rules={[{ required: true }]}>
            <Select
              options={[
                { value: 'SKILL', label: 'SKILL（技能）' },
                { value: 'MCP_SERVICE', label: 'MCP_SERVICE（MCP 服务）' },
                { value: 'MCP_TOOL', label: 'MCP_TOOL（MCP 工具）' },
                { value: 'HTTP_API', label: 'HTTP_API（历史 HTTP 接口）' },
              ]}
            />
          </Form.Item>
          <Form.Item name="parentAssetId" label="父资产 ID（可选，如 MCP_TOOL 挂到 MCP_SERVICE）">
            <InputNumber style={{ width: '100%' }} min={1} precision={0} />
          </Form.Item>
          <Form.Item name="visibility" label="可见性" rules={[{ required: true }]}>
            <Select
              options={[
                { value: 'PROJECT', label: 'PROJECT（仅本项目）' },
                { value: 'SHARED', label: 'SHARED（可授权共享）' },
              ]}
            />
          </Form.Item>
          <Form.Item name="description" label="描述" rules={[{ max: 1024 }]}>
            <Input.TextArea rows={3} />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
    </>
  );
}
