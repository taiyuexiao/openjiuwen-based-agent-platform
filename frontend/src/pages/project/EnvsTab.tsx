import { useCallback, useEffect, useState } from 'react';
import { Button, Card, Form, Select, Space, Table, Tag, message } from 'antd';
import { ReloadOutlined, SettingOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import { api } from '../../api/client';
import JsonTextArea, { jsonRule } from '../../components/JsonTextArea';
import JsonView from '../../components/JsonView';
import type { EnvType, ProjectEnvironment } from '../../types';
import { fmtTime, parseJson } from '../../utils/format';

const ENVS: EnvType[] = ['DEV', 'SIT', 'UAT', 'PROD'];

const QUOTA_PLACEHOLDER = `{
  "cpu": "4",
  "memory": "8Gi",
  "maxAgents": 10
}`;

/** 项目环境配额：查看 + 设置（resourceQuota 为 JSON 文本） */
export default function EnvsTab({ projectId }: { projectId: number }) {
  const [data, setData] = useState<ProjectEnvironment[]>([]);
  const [loading, setLoading] = useState(false);
  const [form] = Form.useForm<{ env: EnvType; resourceQuota?: string }>();

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setData(await api<ProjectEnvironment[]>(`/v1/api/projects/${projectId}/envs/list`));
    } catch {
      // helper 已提示
    } finally {
      setLoading(false);
    }
  }, [projectId]);

  useEffect(() => {
    load();
  }, [load]);

  const setQuota = async () => {
    const values = await form.validateFields();
    await api(`/v1/api/projects/${projectId}/envs/set-quota`, {
      env: values.env,
      resourceQuota: values.resourceQuota?.trim() ? JSON.stringify(parseJson(values.resourceQuota)) : undefined,
    });
    message.success('配额已设置');
    load();
  };

  const columns: ColumnsType<ProjectEnvironment> = [
    { title: 'ID', dataIndex: 'id', width: 70 },
    {
      title: '环境',
      dataIndex: 'env',
      width: 100,
      render: (e: string) => <Tag color={e === 'PROD' ? 'red' : e === 'DEV' ? 'green' : 'orange'}>{e}</Tag>,
    },
    {
      title: '资源配额（JSON）',
      dataIndex: 'resourceQuota',
      render: (v?: string) => <JsonView value={v} title="资源配额" />,
    },
    { title: '更新时间', dataIndex: 'updatedAt', width: 170, render: fmtTime },
  ];

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Card
        size="small"
        title="设置环境配额"
        extra={
          <Button icon={<ReloadOutlined />} onClick={load}>
            刷新
          </Button>
        }
      >
        <Form form={form} layout="vertical">
          <Form.Item name="env" label="环境" rules={[{ required: true, message: '请选择环境' }]} style={{ maxWidth: 240 }}>
            <Select options={ENVS.map((e) => ({ value: e, label: e }))} placeholder="选择环境" />
          </Form.Item>
          <Form.Item name="resourceQuota" label="资源配额（JSON）" rules={[jsonRule()]}>
            <JsonTextArea rows={5} placeholder={QUOTA_PLACEHOLDER} />
          </Form.Item>
          <Button type="primary" icon={<SettingOutlined />} onClick={setQuota}>
            保存配额
          </Button>
        </Form>
      </Card>
      <Table rowKey="id" loading={loading} columns={columns} dataSource={data} pagination={false} />
    </Space>
  );
}
