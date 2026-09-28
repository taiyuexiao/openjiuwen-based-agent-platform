import { useCallback, useEffect, useState } from 'react';
import {
  Alert,
  Button,
  Card,
  Descriptions,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Space,
  Tabs,
  Table,
  Tag,
  Typography,
  message,
} from 'antd';
import { ArrowLeftOutlined, ReloadOutlined } from '@ant-design/icons';
import { useNavigate, useParams, useSearchParams } from 'react-router-dom';
import type { ColumnsType } from 'antd/es/table';
import { api } from '../../api/client';
import PageHeader from '../../components/PageHeader';
import StatusBadge from '../../components/StatusBadge';
import JsonTextArea, { jsonRule } from '../../components/JsonTextArea';
import JsonView from '../../components/JsonView';
import type { Asset, AssetGrant, AssetReference, AssetVersion } from '../../types';
import { fmtTime, parseJson } from '../../utils/format';

/** 资产详情：基本信息 + 发布/下线 + 版本/授权/引用三个 tab */
export default function AssetDetail() {
  const { id } = useParams<{ id: string }>();
  const assetId = Number(id);
  const [searchParams, setSearchParams] = useSearchParams();
  const [projectId, setProjectId] = useState<number | undefined>(() => {
    const p = Number(searchParams.get('projectId'));
    return Number.isFinite(p) && p > 0 ? p : undefined;
  });
  const navigate = useNavigate();

  const [asset, setAsset] = useState<Asset | null>(null);
  const [versions, setVersions] = useState<AssetVersion[]>([]);
  const [grants, setGrants] = useState<AssetGrant[]>([]);
  const [references, setReferences] = useState<AssetReference[]>([]);
  const [loading, setLoading] = useState(false);

  const [publishOpen, setPublishOpen] = useState(false);
  const [grantOpen, setGrantOpen] = useState(false);
  const [refOpen, setRefOpen] = useState(false);
  const [publishForm] = Form.useForm<{ version: string; definition?: string }>();
  const [grantForm] = Form.useForm<{ toProjectId: number }>();
  const [refForm] = Form.useForm<{ assetVersion: string; agentId?: number }>();

  const load = useCallback(async () => {
    if (!projectId) return;
    setLoading(true);
    try {
      const [a, v, g, r] = await Promise.all([
        api<Asset>('/v1/api/assets/detail', { id: assetId, projectId }),
        api<AssetVersion[]>(`/v1/api/assets/${assetId}/versions/list`, { projectId }),
        api<AssetGrant[]>(`/v1/api/assets/${assetId}/grants/list`, { projectId }),
        api<AssetReference[]>(`/v1/api/assets/${assetId}/references/list`, { projectId }),
      ]);
      setAsset(a);
      setVersions(v);
      setGrants(g);
      setReferences(r);
    } catch {
      // helper 已提示
    } finally {
      setLoading(false);
    }
  }, [assetId, projectId]);

  useEffect(() => {
    load();
  }, [load]);

  const operate = async (action: 'publish' | 'offline') => {
    if (!projectId) return;
    await api<Asset>(`/v1/api/assets/${action}`, { id: assetId, projectId });
    message.success(action === 'publish' ? '资产已发布' : '资产已下线');
    load();
  };

  const publishVersion = async () => {
    const values = await publishForm.validateFields();
    await api(`/v1/api/assets/${assetId}/versions/publish`, {
      projectId,
      version: values.version,
      definition: values.definition?.trim() ? parseJson(values.definition) : undefined,
    });
    message.success('新版本发布成功');
    setPublishOpen(false);
    publishForm.resetFields();
    load();
  };

  const grant = async () => {
    const values = await grantForm.validateFields();
    await api(`/v1/api/assets/${assetId}/grants/grant`, { projectId, toProjectId: values.toProjectId });
    message.success('授权成功');
    setGrantOpen(false);
    grantForm.resetFields();
    load();
  };

  const revokeGrant = async (toProjectId: number) => {
    await api(`/v1/api/assets/${assetId}/grants/revoke`, { projectId, toProjectId });
    message.success('已吊销授权');
    load();
  };

  const addReference = async () => {
    const values = await refForm.validateFields();
    await api(`/v1/api/assets/${assetId}/references/add`, {
      projectId,
      assetVersion: values.assetVersion,
      agentId: values.agentId ?? undefined,
    });
    message.success('引用已登记');
    setRefOpen(false);
    refForm.resetFields();
    load();
  };

  const removeReference = async (refId: number) => {
    await api(`/v1/api/assets/${assetId}/references/remove`, { projectId, refId });
    message.success('引用已移除');
    load();
  };

  if (!projectId) {
    return (
      <Card title="资产详情">
        <Alert
          type="info"
          showIcon
          message="该页面需要 projectId 上下文"
          description={
            <Space direction="vertical">
              <span>请从资产列表进入，或在此输入归属项目 ID：</span>
              <Space>
                <InputNumber
                  min={1}
                  precision={0}
                  placeholder="归属项目 ID"
                  onChange={(v) => setProjectId(v ?? undefined)}
                />
                <Button
                  type="primary"
                  onClick={() => projectId && setSearchParams({ projectId: String(projectId) })}
                >
                  加载
                </Button>
              </Space>
            </Space>
          }
        />
      </Card>
    );
  }

  const versionColumns: ColumnsType<AssetVersion> = [
    { title: 'ID', dataIndex: 'id', width: 70 },
    { title: '版本', dataIndex: 'version', width: 120 },
    {
      title: '状态',
      dataIndex: 'status',
      width: 110,
      render: (s: string) => <Tag color={s === 'PUBLISHED' ? 'green' : 'red'}>{s}</Tag>,
    },
    {
      title: '定义 definition',
      dataIndex: 'definition',
      render: (v?: string) => <JsonView value={v} title="版本定义" />,
    },
    { title: '发布人', dataIndex: 'publishedBy', width: 100 },
    { title: '发布时间', dataIndex: 'publishedAt', width: 170, render: fmtTime },
  ];

  const grantColumns: ColumnsType<AssetGrant> = [
    { title: 'ID', dataIndex: 'id', width: 70 },
    { title: '被授权项目', dataIndex: 'toProjectId' },
    { title: '授权人', dataIndex: 'grantedBy', width: 120 },
    { title: '授权时间', dataIndex: 'createdAt', width: 170, render: fmtTime },
    {
      title: '操作',
      width: 100,
      render: (_, r) => (
        <Popconfirm title={`确认吊销对项目 ${r.toProjectId} 的授权？`} onConfirm={() => revokeGrant(r.toProjectId)}>
          <Typography.Link type="danger">吊销</Typography.Link>
        </Popconfirm>
      ),
    },
  ];

  const refColumns: ColumnsType<AssetReference> = [
    { title: 'ID', dataIndex: 'id', width: 70 },
    { title: '引用版本', dataIndex: 'assetVersion', width: 120 },
    { title: '消费方项目', dataIndex: 'projectId', width: 110 },
    { title: '关联 Agent', dataIndex: 'agentId', width: 110, render: (v?: number) => v ?? '-' },
    { title: '登记人', dataIndex: 'createdBy', width: 100 },
    { title: '登记时间', dataIndex: 'createdAt', width: 170, render: fmtTime },
    {
      title: '操作',
      width: 100,
      render: (_, r) => (
        <Popconfirm title="确认移除该引用？" onConfirm={() => removeReference(r.id)}>
          <Typography.Link type="danger">移除</Typography.Link>
        </Popconfirm>
      ),
    },
  ];

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <PageHeader
        backTo="/assets"
        title={asset ? `${asset.name}（${asset.code}）` : `资产 #${assetId}`}
        badge={asset && <StatusBadge value={asset.status} />}
        subTitle={asset ? `ID ${asset.id} · ${asset.assetType} · ${asset.visibility} · 归属项目 #${asset.ownerProjectId}` : undefined}
        extra={
          <Space>
            <Button icon={<ReloadOutlined />} onClick={load}>
              刷新
            </Button>
            {asset?.status !== 'PUBLISHED' && (
              <Popconfirm title="确认发布该资产？" onConfirm={() => operate('publish')}>
                <Button type="primary">发布</Button>
              </Popconfirm>
            )}
            {asset?.status === 'PUBLISHED' && (
              <Popconfirm title="确认下线该资产？" onConfirm={() => operate('offline')}>
                <Button danger>下线</Button>
              </Popconfirm>
            )}
          </Space>
        }
      />
      <Card className="detail-hero" loading={loading}>
        {asset && (
          <Descriptions size="small" column={4}>
            <Descriptions.Item label="ID">{asset.id}</Descriptions.Item>
            <Descriptions.Item label="类型">
              <Tag>{asset.assetType}</Tag>
            </Descriptions.Item>
            <Descriptions.Item label="可见性">{asset.visibility}</Descriptions.Item>
            <Descriptions.Item label="状态">
              <Tag color={asset.status === 'PUBLISHED' ? 'green' : asset.status === 'OFFLINE' ? 'red' : 'default'}>
                {asset.status}
              </Tag>
            </Descriptions.Item>
            <Descriptions.Item label="归属项目">{asset.ownerProjectId}</Descriptions.Item>
            <Descriptions.Item label="归属人">{asset.ownerUserId}</Descriptions.Item>
            <Descriptions.Item label="父资产">{asset.parentAssetId ?? '-'}</Descriptions.Item>
            <Descriptions.Item label="创建时间">{fmtTime(asset.createdAt)}</Descriptions.Item>
            <Descriptions.Item label="描述" span={4}>
              {asset.description || '-'}
            </Descriptions.Item>
          </Descriptions>
        )}
      </Card>

      <Card>
        <Tabs
          items={[
            {
              key: 'versions',
              label: '版本',
              children: (
                <>
                  <Space style={{ marginBottom: 16 }}>
                    <Button type="primary" onClick={() => setPublishOpen(true)}>
                      发布新版本
                    </Button>
                  </Space>
                  <Table rowKey="id" columns={versionColumns} dataSource={versions} pagination={false} />
                </>
              ),
            },
            {
              key: 'grants',
              label: '跨项目授权',
              children: (
                <>
                  <Space style={{ marginBottom: 16 }}>
                    <Button type="primary" onClick={() => setGrantOpen(true)}>
                      新增授权
                    </Button>
                  </Space>
                  <Table rowKey="id" columns={grantColumns} dataSource={grants} pagination={false} />
                </>
              ),
            },
            {
              key: 'references',
              label: '引用',
              children: (
                <>
                  <Space style={{ marginBottom: 16 }}>
                    <Button type="primary" onClick={() => setRefOpen(true)}>
                      登记引用
                    </Button>
                  </Space>
                  <Table rowKey="id" columns={refColumns} dataSource={references} pagination={false} />
                </>
              ),
            },
          ]}
        />
      </Card>

      <Modal title="发布新版本" open={publishOpen} onOk={publishVersion} onCancel={() => setPublishOpen(false)} destroyOnClose>
        <Form form={publishForm} layout="vertical" preserve={false}>
          <Form.Item
            name="version"
            label="版本号（x.y.z）"
            rules={[
              { required: true, message: '请输入版本号' },
              { pattern: /^\d+\.\d+\.\d+$/, message: '版本号必须为 x.y.z 格式' },
            ]}
          >
            <Input placeholder="如 1.0.0" />
          </Form.Item>
          <Form.Item name="definition" label="版本定义（JSON，可选）" rules={[jsonRule()]}>
            <JsonTextArea rows={8} placeholder={'{\n  "entrypoint": "skill.main:run",\n  "params": {}\n}'} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal title="新增跨项目授权" open={grantOpen} onOk={grant} onCancel={() => setGrantOpen(false)} destroyOnClose>
        <Form form={grantForm} layout="vertical" preserve={false}>
          <Form.Item name="toProjectId" label="被授权项目 ID" rules={[{ required: true, message: '请输入被授权项目 ID' }]}>
            <InputNumber style={{ width: '100%' }} min={1} precision={0} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal title="登记资产引用" open={refOpen} onOk={addReference} onCancel={() => setRefOpen(false)} destroyOnClose>
        <Form form={refForm} layout="vertical" preserve={false}>
          <Form.Item
            name="assetVersion"
            label="引用版本（x.y.z）"
            rules={[
              { required: true, message: '请输入引用版本' },
              { pattern: /^\d+\.\d+\.\d+$/, message: '版本号必须为 x.y.z 格式' },
            ]}
          >
            <Input placeholder="如 1.0.0" />
          </Form.Item>
          <Form.Item name="agentId" label="关联 Agent ID（可选）">
            <InputNumber style={{ width: '100%' }} min={1} precision={0} />
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  );
}
