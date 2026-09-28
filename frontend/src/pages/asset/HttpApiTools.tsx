import { useState } from 'react';
import {
  Alert,
  Button,
  Card,
  Checkbox,
  Col,
  Form,
  Input,
  InputNumber,
  Row,
  Select,
  Space,
  Table,
  Tag,
  Typography,
  message,
} from 'antd';
import { CloudUploadOutlined, SwapOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import { api } from '../../api/client';
import JsonTextArea, { jsonRule } from '../../components/JsonTextArea';
import PageHeader from '../../components/PageHeader';
import ProjectSelect from '../../components/ProjectSelect';
import { useProject } from '../../context/ProjectContext';
import type { Asset, ToolDraft } from '../../types';

const OPENAPI_PLACEHOLDER = `{
  "openapi": "3.0.0",
  "info": { "title": "demo-api", "version": "1.0.0" },
  "paths": {
    "/users/{id}": {
      "get": {
        "operationId": "getUser",
        "summary": "查询用户",
        "parameters": [
          { "name": "id", "in": "path", "required": true, "schema": { "type": "string" } }
        ]
      }
    }
  }
}`;

/** HTTP API（OpenAPI 3.x）注册 + 转换为 MCP_TOOL */
export default function HttpApiTools() {
  const { projectId } = useProject();
  const [registerForm] = Form.useForm<{ code: string; name: string; description?: string; openApiJson: string }>();
  const [registering, setRegistering] = useState(false);
  const [registeredAsset, setRegisteredAsset] = useState<Asset | null>(null);

  const [convertAssetId, setConvertAssetId] = useState<number | undefined>();
  const [mcpServiceId, setMcpServiceId] = useState<number | undefined>();
  const [tools, setTools] = useState<ToolDraft[]>([]);
  const [checked, setChecked] = useState<string[]>([]);
  const [toolsLoading, setToolsLoading] = useState(false);
  const [converting, setConverting] = useState(false);
  const [converted, setConverted] = useState<Asset[]>([]);

  const register = async () => {
    if (!projectId) {
      message.warning('请先选择项目');
      return;
    }
    const values = await registerForm.validateFields();
    setRegistering(true);
    try {
      const asset = await api<Asset>('/v1/api/http-apis/register', {
        code: values.code,
        name: values.name,
        projectId,
        description: values.description?.trim() || undefined,
        openApiJson: values.openApiJson,
      });
      setRegisteredAsset(asset);
      setConvertAssetId(asset.id);
      setTools([]);
      setChecked([]);
      setConverted([]);
      message.success(`注册成功，资产 ID=${asset.id}，可在右侧加载工具草案并转换`);
    } finally {
      setRegistering(false);
    }
  };

  const loadTools = async () => {
    if (!projectId || !convertAssetId) {
      message.warning('请先选择项目并填写 HTTP_API 资产 ID');
      return;
    }
    setToolsLoading(true);
    try {
      const list = await api<ToolDraft[]>(`/v1/api/http-apis/${convertAssetId}/tools/list`, { projectId });
      setTools(list);
      setChecked(list.map((t) => t.name));
      setConverted([]);
      message.success(`解析到 ${list.length} 个工具草案`);
    } finally {
      setToolsLoading(false);
    }
  };

  const convert = async () => {
    if (!projectId || !convertAssetId) return;
    if (checked.length === 0) {
      message.warning('请至少勾选一个工具');
      return;
    }
    setConverting(true);
    try {
      const created = await api<Asset[]>(`/v1/api/http-apis/${convertAssetId}/convert`, {
        projectId,
        operations: checked,
        mcpServiceId: mcpServiceId ?? undefined,
      });
      setConverted(created);
      message.success(`转换成功，创建 ${created.length} 个 MCP_TOOL 资产`);
    } finally {
      setConverting(false);
    }
  };

  const toolColumns: ColumnsType<ToolDraft> = [
    {
      title: (
        <Checkbox
          checked={tools.length > 0 && checked.length === tools.length}
          indeterminate={checked.length > 0 && checked.length < tools.length}
          onChange={(e) => setChecked(e.target.checked ? tools.map((t) => t.name) : [])}
        />
      ),
      width: 50,
      render: (_, t) => (
        <Checkbox
          checked={checked.includes(t.name)}
          onChange={(e) =>
            setChecked((prev) => (e.target.checked ? [...prev, t.name] : prev.filter((n) => n !== t.name)))
          }
        />
      ),
    },
    { title: '工具名（operation）', dataIndex: 'name', width: 200 },
    { title: '方法', dataIndex: 'method', width: 80, render: (m: string) => <Tag>{m?.toUpperCase()}</Tag> },
    { title: '路径', dataIndex: 'path', ellipsis: true },
    { title: '描述', dataIndex: 'description', ellipsis: true, render: (v?: string) => v ?? '-' },
  ];

  const convertedColumns: ColumnsType<Asset> = [
    { title: 'ID', dataIndex: 'id', width: 80 },
    { title: '编码', dataIndex: 'code' },
    { title: '名称', dataIndex: 'name' },
    { title: '类型', dataIndex: 'assetType', width: 120, render: (t: string) => <Tag color="blue">{t}</Tag> },
  ];

  return (
    <>
      <PageHeader title="HTTP API 注册与转换" subTitle="粘贴 OpenAPI 3.x JSON 注册为资产；勾选工具草案一键转换为 MCP_TOOL" />
      <Row gutter={16}>
      <Col xs={24} lg={12}>
        <Card title="注册 HTTP API（OpenAPI 3.x）" className="soft-card">
          <Space direction="vertical" style={{ width: '100%' }} size="middle">
            <div>
              <Typography.Text strong>项目：</Typography.Text>
              <ProjectSelect />
            </div>
            {!projectId && <Alert type="info" showIcon message="请先选择项目" />}
            <Form form={registerForm} layout="vertical">
              <Form.Item
                name="code"
                label="资产编码"
                rules={[
                  { required: true, message: '请输入编码' },
                  { pattern: /^[A-Za-z0-9-]+$/, message: '仅允许字母、数字、中划线' },
                  { max: 128 },
                ]}
              >
                <Input placeholder="如 legacy-user-api" />
              </Form.Item>
              <Form.Item name="name" label="名称" rules={[{ required: true }, { max: 128 }]}>
                <Input />
              </Form.Item>
              <Form.Item name="description" label="描述（可选）" rules={[{ max: 1024 }]}>
                <Input.TextArea rows={2} />
              </Form.Item>
              <Form.Item
                name="openApiJson"
                label="OpenAPI 文档（JSON）"
                rules={[jsonRule(true)]}
              >
                <JsonTextArea rows={14} placeholder={OPENAPI_PLACEHOLDER} />
              </Form.Item>
            </Form>
            <Button
              type="primary"
              icon={<CloudUploadOutlined />}
              loading={registering}
              disabled={!projectId}
              onClick={register}
            >
              注册
            </Button>
            {registeredAsset && (
              <Alert
                type="success"
                showIcon
                message={`已注册：${registeredAsset.name}（ID=${registeredAsset.id}，初始版本已生成）`}
              />
            )}
          </Space>
        </Card>
      </Col>
      <Col span={12}>
        <Card title="转换为 MCP_TOOL">
          <Space direction="vertical" style={{ width: '100%' }} size="middle">
            <Space wrap>
              <span>HTTP_API 资产 ID：</span>
              <InputNumber
                min={1}
                precision={0}
                style={{ width: 140 }}
                value={convertAssetId}
                onChange={(v) => setConvertAssetId(v ?? undefined)}
              />
              <span>挂载到 MCP_SERVICE（可选）：</span>
              <InputNumber
                min={1}
                precision={0}
                style={{ width: 140 }}
                value={mcpServiceId}
                onChange={(v) => setMcpServiceId(v ?? undefined)}
              />
            </Space>
            <Space>
              <Button onClick={loadTools} loading={toolsLoading} disabled={!projectId || !convertAssetId}>
                加载工具草案
              </Button>
              <Button
                type="primary"
                icon={<SwapOutlined />}
                onClick={convert}
                loading={converting}
                disabled={tools.length === 0}
              >
                转换勾选工具（{checked.length}）
              </Button>
            </Space>
            <Table
              rowKey="name"
              size="small"
              loading={toolsLoading}
              columns={toolColumns}
              dataSource={tools}
              pagination={false}
            />
            {converted.length > 0 && (
              <>
                <Typography.Text strong>转换结果：</Typography.Text>
                <Table rowKey="id" size="small" columns={convertedColumns} dataSource={converted} pagination={false} />
              </>
            )}
          </Space>
        </Card>
      </Col>
    </Row>
    </>
  );
}
