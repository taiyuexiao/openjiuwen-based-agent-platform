import { useState } from 'react';
import { colors } from '../../theme';
import { Alert, Button, Card, Col, Descriptions, Form, Input, Row, Select, Space, Tag, Typography, Upload, message } from 'antd';
import { CheckCircleOutlined, CloudUploadOutlined, InboxOutlined } from '@ant-design/icons';
import type { UploadFile } from 'antd/es/upload/interface';
import { api, apiUpload } from '../../api/client';
import PageHeader from '../../components/PageHeader';
import ProjectSelect from '../../components/ProjectSelect';
import { useProject } from '../../context/ProjectContext';
import type { AssetType, AssetUploadResp } from '../../types';

/** Skill 上传：multipart 上传 → DRAFT 版本；可一键 publish-draft */
export default function AssetUpload() {
  const { projectId } = useProject();
  const [form] = Form.useForm<{ name: string; assetType: AssetType; code?: string; version?: string }>();
  const [fileList, setFileList] = useState<UploadFile[]>([]);
  const [uploading, setUploading] = useState(false);
  const [result, setResult] = useState<AssetUploadResp | null>(null);
  const [publishing, setPublishing] = useState(false);

  const upload = async () => {
    if (!projectId) {
      message.warning('请先在顶栏选择项目');
      return;
    }
    const values = await form.validateFields();
    if (fileList.length === 0 || !fileList[0].originFileObj) {
      message.warning('请先选择或拖入文件');
      return;
    }
    setUploading(true);
    try {
      const fd = new FormData();
      fd.append('file', fileList[0].originFileObj as File);
      fd.append('projectId', String(projectId));
      fd.append('assetType', values.assetType);
      fd.append('name', values.name);
      if (values.code?.trim()) fd.append('code', values.code.trim());
      if (values.version?.trim()) fd.append('version', values.version.trim());
      const resp = await apiUpload<AssetUploadResp>('/v1/api/assets/upload', fd);
      setResult(resp);
      message.success('上传成功，已生成草稿版本');
    } finally {
      setUploading(false);
    }
  };

  const publishDraft = async () => {
    if (!result || !projectId) return;
    setPublishing(true);
    try {
      await api('/v1/api/assets/versions/publish-draft', {
        assetId: result.assetId,
        version: result.version,
        projectId,
      });
      setResult({ ...result, versionStatus: 'PUBLISHED' });
      message.success(`版本 ${result.version} 已发布`);
    } finally {
      setPublishing(false);
    }
  };

  return (
    <>
      <PageHeader
        title="Skill 上传"
        subTitle="上传 Skill 包文件生成 DRAFT 资产与草稿版本；发布走 publish-draft（上传不产生已发布版本）"
      />
      <Row gutter={16}>
        <Col xs={24} lg={13}>
          <Card title="上传文件" className="soft-card">
            {!projectId && (
              <Alert style={{ marginBottom: 16 }} type="info" showIcon message="请先在顶栏选择项目" />
            )}
            <Form form={form} layout="vertical" initialValues={{ assetType: 'SKILL' }}>
              <Form.Item name="name" label="资产名称" rules={[{ required: true, message: '请输入名称' }]}>
                <Input placeholder="如 退款技能包" />
              </Form.Item>
              <Form.Item name="assetType" label="资产类型" rules={[{ required: true }]}>
                <Select
                  options={[
                    { value: 'SKILL', label: 'SKILL（技能包）' },
                    { value: 'MCP_SERVICE', label: 'MCP_SERVICE' },
                    { value: 'MCP_TOOL', label: 'MCP_TOOL' },
                  ]}
                />
              </Form.Item>
              <Form.Item
                name="code"
                label="资产编码（可选，留空由文件名/名称生成）"
                rules={[{ pattern: /^[A-Za-z0-9-]*$/, message: '仅允许字母、数字、中划线' }, { max: 128 }]}
              >
                <Input placeholder="如 refund-skill" />
              </Form.Item>
              <Form.Item
                name="version"
                label="版本号（可选，默认 0.0.1）"
                rules={[{ pattern: /^(\d+\.\d+\.\d+)?$/, message: '版本号必须为 x.y.z 格式' }]}
              >
                <Input placeholder="如 1.0.0" />
              </Form.Item>
              <Form.Item label="Skill 文件" required>
                <Upload.Dragger
                  fileList={fileList}
                  beforeUpload={() => false}
                  maxCount={1}
                  onChange={({ fileList: fl }) => setFileList(fl)}
                >
                  <p className="ant-upload-drag-icon">
                    <InboxOutlined style={{ color: colors.brand }} />
                  </p>
                  <p className="ant-upload-text">点击或拖拽文件到此区域上传</p>
                  <p className="ant-upload-hint">支持 zip / json / yaml 等 Skill 包文件</p>
                </Upload.Dragger>
              </Form.Item>
              <Button
                type="primary"
                icon={<CloudUploadOutlined />}
                loading={uploading}
                disabled={!projectId || fileList.length === 0}
                onClick={upload}
                block
              >
                上传
              </Button>
            </Form>
          </Card>
        </Col>
        <Col xs={24} lg={11}>
          <Card title="上传结果" className="soft-card">
            {result ? (
              <Space direction="vertical" size={16} style={{ width: '100%' }}>
                <Alert
                  type={result.versionStatus === 'PUBLISHED' ? 'success' : 'info'}
                  showIcon
                  icon={<CheckCircleOutlined />}
                  message={
                    result.versionStatus === 'PUBLISHED'
                      ? `版本 ${result.version} 已发布`
                      : '已生成 DRAFT 草稿版本，发布后资产方可被引用'
                  }
                />
                <Descriptions size="small" column={1} bordered>
                  <Descriptions.Item label="资产">{result.assetCode}（ID={result.assetId}）</Descriptions.Item>
                  <Descriptions.Item label="资产状态">
                    <Tag>{result.assetStatus}</Tag>
                  </Descriptions.Item>
                  <Descriptions.Item label="版本">
                    {result.version}
                    <Tag style={{ marginLeft: 8 }} color={result.versionStatus === 'PUBLISHED' ? 'green' : 'default'}>
                      {result.versionStatus}
                    </Tag>
                  </Descriptions.Item>
                  <Descriptions.Item label="文件名">{result.filename}</Descriptions.Item>
                  <Descriptions.Item label="大小">{(result.size / 1024).toFixed(1)} KB</Descriptions.Item>
                  <Descriptions.Item label="SHA-256">
                    <Typography.Text copyable style={{ fontFamily: 'monospace', fontSize: 12 }}>
                      {result.sha256}
                    </Typography.Text>
                  </Descriptions.Item>
                </Descriptions>
                {result.versionStatus !== 'PUBLISHED' && (
                  <Button type="primary" loading={publishing} onClick={publishDraft} block>
                    发布草稿版本（publish-draft）
                  </Button>
                )}
              </Space>
            ) : (
              <Typography.Text type="secondary">上传成功后在此展示资产、版本与文件指纹信息</Typography.Text>
            )}
          </Card>
        </Col>
      </Row>
    </>
  );
}
