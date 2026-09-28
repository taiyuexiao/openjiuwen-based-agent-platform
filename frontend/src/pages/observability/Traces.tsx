import { useState } from 'react';
import { Alert, Button, Card, Drawer, Form, Input, InputNumber, Select, Space, Table, Tag, Typography } from 'antd';
import { SearchOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import { api } from '../../api/client';
import JsonView from '../../components/JsonView';
import EmptyState from '../../components/EmptyState';
import ProjectSelect from '../../components/ProjectSelect';
import { useProject } from '../../context/ProjectContext';
import type { TraceSpan } from '../../types';
import { fmtEpoch } from '../../utils/format';

/** 链路追踪：按 traceId / agentId 查询；点击 traceId 查看完整 span 列表 */
export default function Traces() {
  const { projectId } = useProject();
  const [form] = Form.useForm<{ traceId?: string; agentId?: number; env?: string; limit?: number }>();
  const [data, setData] = useState<TraceSpan[]>([]);
  const [loading, setLoading] = useState(false);
  const [detailTraceId, setDetailTraceId] = useState<string | null>(null);
  const [detailSpans, setDetailSpans] = useState<TraceSpan[]>([]);
  const [detailLoading, setDetailLoading] = useState(false);

  const query = async () => {
    if (!projectId) return;
    const values = form.getFieldsValue();
    setLoading(true);
    try {
      setData(
        await api<TraceSpan[]>('/v1/api/observability/traces/query', {
          projectId,
          traceId: values.traceId?.trim() || undefined,
          agentId: values.agentId ?? undefined,
          env: values.env || undefined,
          limit: values.limit ?? undefined,
        }),
      );
    } catch {
      // helper 已提示
    } finally {
      setLoading(false);
    }
  };

  const openDetail = async (traceId: string) => {
    if (!projectId) return;
    setDetailTraceId(traceId);
    setDetailLoading(true);
    try {
      setDetailSpans(await api<TraceSpan[]>(`/v1/api/observability/traces/${traceId}`, { projectId }));
    } catch {
      // helper 已提示
    } finally {
      setDetailLoading(false);
    }
  };

  const spanColumns = (withTraceLink: boolean): ColumnsType<TraceSpan> => [
    {
      title: 'Trace ID',
      dataIndex: 'traceId',
      width: 240,
      ellipsis: true,
      render: (v: string) =>
        withTraceLink ? <Typography.Link onClick={() => openDetail(v)}>{v}</Typography.Link> : v,
    },
    { title: 'Span ID', dataIndex: 'spanId', width: 130, ellipsis: true },
    { title: '父 Span', dataIndex: 'parentSpanId', width: 130, ellipsis: true, render: (v?: string) => v ?? '-' },
    { title: '名称', dataIndex: 'spanName', ellipsis: true, render: (v?: string) => v ?? '-' },
    {
      title: '类型',
      dataIndex: 'spanKind',
      width: 100,
      render: (k?: string) => (k ? <Tag color={k === 'LLM' ? 'purple' : k === 'TOOL' ? 'blue' : 'default'}>{k}</Tag> : '-'),
    },
    { title: 'Agent', dataIndex: 'agentId', width: 80, render: (v?: number) => v ?? '-' },
    { title: '环境', dataIndex: 'env', width: 80, render: (v?: string) => v ?? '-' },
    { title: '开始时间', dataIndex: 'startTime', width: 170, render: fmtEpoch },
    {
      title: '耗时（ms）',
      width: 100,
      render: (_, r) => (r.startTime !== undefined && r.endTime !== undefined ? r.endTime - r.startTime : '-'),
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (s?: string) => (s ? <Tag color={s === 'OK' || s === 'SUCCESS' ? 'green' : 'red'}>{s}</Tag> : '-'),
    },
    {
      title: '属性',
      dataIndex: 'attrs',
      render: (v?: string) => <JsonView value={v} title="Span 属性" />,
    },
  ];

  return (
    <div>
      <Space wrap style={{ marginBottom: 16 }}>
        <ProjectSelect />
        <Form form={form} layout="inline" initialValues={{ limit: 100 }}>
          <Form.Item name="traceId">
            <Input placeholder="Trace ID（精确）" style={{ width: 240 }} allowClear />
          </Form.Item>
          <Form.Item name="agentId">
            <InputNumber placeholder="Agent ID" min={1} precision={0} style={{ width: 120 }} />
          </Form.Item>
          <Form.Item name="env">
            <Select
              allowClear
              placeholder="环境"
              style={{ width: 110 }}
              options={['DEV', 'SIT', 'UAT', 'PROD'].map((e) => ({ value: e, label: e }))}
            />
          </Form.Item>
          <Form.Item name="limit">
            <InputNumber placeholder="条数上限" min={1} max={1000} precision={0} style={{ width: 110 }} />
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
        columns={spanColumns(true)}
        dataSource={data}
        pagination={{ pageSize: 20 }}
        size="small"
      />

      <Drawer
        title={`Trace 详情：${detailTraceId ?? ''}`}
        open={detailTraceId !== null}
        onClose={() => setDetailTraceId(null)}
        width="80%"
      >
        <Table
          rowKey="id"
          loading={detailLoading}
          columns={spanColumns(false)}
          dataSource={detailSpans}
          pagination={false}
          size="small"
        />
      </Drawer>
    </div>
  );
}
