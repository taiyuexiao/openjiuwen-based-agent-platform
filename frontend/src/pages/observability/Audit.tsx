import { useState } from 'react';
import { Alert, Button, Card, DatePicker, Form, Input, InputNumber, Space, Table, Tag } from 'antd';
import { SearchOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import type { Dayjs } from 'dayjs';
import { api } from '../../api/client';
import JsonView from '../../components/JsonView';
import EmptyState from '../../components/EmptyState';
import ProjectSelect from '../../components/ProjectSelect';
import { useProject } from '../../context/ProjectContext';
import type { AuditEvent } from '../../types';
import { fmtTime } from '../../utils/format';

/** 审计查询 */
export default function Audit() {
  const { projectId } = useProject();
  const [form] = Form.useForm<{
    module?: string;
    userId?: string;
    resourceType?: string;
    resourceId?: string;
    range?: [Dayjs, Dayjs];
    limit?: number;
  }>();
  const [data, setData] = useState<AuditEvent[]>([]);
  const [loading, setLoading] = useState(false);

  const query = async () => {
    if (!projectId) return;
    const values = form.getFieldsValue();
    setLoading(true);
    try {
      setData(
        await api<AuditEvent[]>('/v1/api/observability/audit/query', {
          projectId,
          module: values.module?.trim() || undefined,
          userId: values.userId?.trim() || undefined,
          resourceType: values.resourceType?.trim() || undefined,
          resourceId: values.resourceId?.trim() || undefined,
          createdFrom: values.range?.[0]?.format('YYYY-MM-DDTHH:mm:ss'),
          createdTo: values.range?.[1]?.format('YYYY-MM-DDTHH:mm:ss'),
          limit: values.limit ?? undefined,
        }),
      );
    } catch {
      // helper 已提示
    } finally {
      setLoading(false);
    }
  };

  const columns: ColumnsType<AuditEvent> = [
    { title: 'ID', dataIndex: 'id', width: 70 },
    { title: '时间', dataIndex: 'createdAt', width: 170, render: fmtTime },
    { title: '用户', dataIndex: 'userId', width: 90 },
    { title: '模块', dataIndex: 'module', width: 130, render: (m: string) => <Tag>{m}</Tag> },
    { title: '动作', dataIndex: 'action', width: 170 },
    { title: '资源类型', dataIndex: 'resourceType', width: 110, render: (v?: string) => v ?? '-' },
    { title: '资源 ID', dataIndex: 'resourceId', width: 90, render: (v?: string) => v ?? '-' },
    {
      title: '结果',
      dataIndex: 'result',
      width: 90,
      render: (r?: string) =>
        r ? <Tag color={r === 'SUCCESS' || r === 'OK' ? 'green' : 'red'}>{r}</Tag> : '-',
    },
    {
      title: '详情',
      dataIndex: 'detail',
      render: (v?: string) => <JsonView value={v} title="审计详情" />,
    },
    { title: '请求 ID', dataIndex: 'requestId', width: 200, ellipsis: true },
  ];

  return (
    <div>
      <Space wrap style={{ marginBottom: 16 }}>
        <ProjectSelect />
        <Form form={form} layout="inline" initialValues={{ limit: 100 }}>
          <Form.Item name="module">
            <Input placeholder="模块，如 delivery" style={{ width: 140 }} allowClear />
          </Form.Item>
          <Form.Item name="userId">
            <Input placeholder="用户 ID" style={{ width: 110 }} allowClear />
          </Form.Item>
          <Form.Item name="resourceType">
            <Input placeholder="资源类型" style={{ width: 110 }} allowClear />
          </Form.Item>
          <Form.Item name="resourceId">
            <Input placeholder="资源 ID" style={{ width: 90 }} allowClear />
          </Form.Item>
          <Form.Item name="range">
            <DatePicker.RangePicker showTime />
          </Form.Item>
          <Form.Item name="limit">
            <InputNumber placeholder="条数上限" min={1} max={1000} precision={0} style={{ width: 100 }} />
          </Form.Item>
          <Button type="primary" icon={<SearchOutlined />} onClick={query} disabled={!projectId} loading={loading}>
            查询
          </Button>
        </Form>
      </Space>
      {!projectId && <Alert style={{ marginBottom: 16 }} type="info" showIcon message="请先选择项目" />}
      <Table
        rowKey="id"
        loading={loading}
        columns={columns}
        dataSource={data}
        pagination={{ pageSize: 20 }}
        size="small"
      />
    </div>
  );
}
