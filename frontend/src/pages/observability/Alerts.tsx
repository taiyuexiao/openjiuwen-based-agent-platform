import { useCallback, useEffect, useState } from 'react';
import { colors } from '../../theme';
import {
  Alert,
  Button,
  Card,
  Descriptions,
  Drawer,
  Form,
  Input,
  InputNumber,
  Modal,
  Select,
  Space,
  Table,
  Tag,
  Typography,
  message,
} from 'antd';
import { CheckOutlined, ReloadOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import { api } from '../../api/client';
import JsonView from '../../components/JsonView';
import EmptyState from '../../components/EmptyState';
import ProjectSelect from '../../components/ProjectSelect';
import { useProject } from '../../context/ProjectContext';
import type { AlertEvent, AlertStatus } from '../../types';
import { fmtTime, prettyJson } from '../../utils/format';

const STATUS_COLORS: Record<string, string> = {
  FIRING: 'red',
  HANDLED: 'orange',
  CLOSED: 'green',
};

const LEVEL_COLORS: Record<string, string> = {
  critical: 'red',
  major: 'orange',
  minor: 'gold',
  info: 'blue',
};

/** 告警：list / detail / handle（登记处置） */
export default function Alerts() {
  const { projectId } = useProject();
  const [statusFilter, setStatusFilter] = useState<AlertStatus | undefined>();
  const [levelFilter, setLevelFilter] = useState<string | undefined>();
  const [agentFilter, setAgentFilter] = useState<number | undefined>();
  const [data, setData] = useState<AlertEvent[]>([]);
  const [loading, setLoading] = useState(false);
  const [handleTarget, setHandleTarget] = useState<AlertEvent | null>(null);
  const [detailTarget, setDetailTarget] = useState<AlertEvent | null>(null);
  const [handleForm] = Form.useForm<{ note: string }>();

  const load = useCallback(async () => {
    if (!projectId) {
      setData([]);
      return;
    }
    setLoading(true);
    try {
      setData(
        await api<AlertEvent[]>('/v1/api/observability/alerts/list', {
          projectId,
          status: statusFilter ?? undefined,
          level: levelFilter ?? undefined,
          agentId: agentFilter ?? undefined,
        }),
      );
    } catch {
      // helper 已提示
    } finally {
      setLoading(false);
    }
  }, [projectId, statusFilter, levelFilter, agentFilter]);

  useEffect(() => {
    load();
  }, [load]);

  const handle = async () => {
    if (!projectId || !handleTarget) return;
    const values = await handleForm.validateFields();
    await api<AlertEvent>('/v1/api/observability/alerts/handle', {
      projectId,
      id: handleTarget.id,
      note: values.note,
    });
    message.success('处置已登记');
    setHandleTarget(null);
    handleForm.resetFields();
    load();
  };

  const columns: ColumnsType<AlertEvent> = [
    { title: 'ID', dataIndex: 'id', width: 70 },
    {
      title: '级别',
      dataIndex: 'level',
      width: 90,
      render: (l?: string) => (l ? <Tag color={LEVEL_COLORS[l] ?? 'default'}>{l}</Tag> : '-'),
    },
    {
      title: '标题',
      dataIndex: 'title',
      render: (v: string, r) => <Typography.Link onClick={() => setDetailTarget(r)}>{v}</Typography.Link>,
    },
    { title: '来源', dataIndex: 'source', width: 110, render: (v?: string) => v ?? '-' },
    { title: 'Agent', dataIndex: 'agentId', width: 80, render: (v?: number) => v ?? '-' },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (s: string) => <Tag color={STATUS_COLORS[s]}>{s}</Tag>,
    },
    { title: '处置人', dataIndex: 'handledBy', width: 90, render: (v?: string) => v ?? '-' },
    { title: '创建时间', dataIndex: 'createdAt', width: 170, render: fmtTime },
    {
      title: '操作',
      width: 130,
      render: (_, r) => (
        <Space>
          <Typography.Link onClick={() => setDetailTarget(r)}>详情</Typography.Link>
          {r.status === 'FIRING' && (
            <Typography.Link onClick={() => setHandleTarget(r)}>
              <CheckOutlined /> 处置
            </Typography.Link>
          )}
        </Space>
      ),
    },
  ];

  return (
    <div>
      <Space wrap style={{ marginBottom: 16 }}>
        <ProjectSelect />
        <Select
          allowClear
          placeholder="状态"
          style={{ minWidth: 120 }}
          value={statusFilter}
          onChange={setStatusFilter}
          options={['FIRING', 'HANDLED', 'CLOSED'].map((s) => ({ value: s, label: s }))}
        />
        <Select
          allowClear
          placeholder="级别"
          style={{ minWidth: 120 }}
          value={levelFilter}
          onChange={setLevelFilter}
          options={['critical', 'major', 'minor', 'info'].map((l) => ({ value: l, label: l }))}
        />
        <InputNumber
          placeholder="Agent ID"
          min={1}
          precision={0}
          style={{ width: 120 }}
          value={agentFilter}
          onChange={(v) => setAgentFilter(v ?? undefined)}
        />
      <Button icon={<ReloadOutlined />} onClick={load} disabled={!projectId}>
          刷新
        </Button>
      </Space>
      {!projectId && <Alert style={{ marginBottom: 16 }} type="info" showIcon message="请先选择项目" />}
      <Table locale={{ emptyText: <EmptyState small description="暂无告警，系统运行正常" /> }} rowKey="id" loading={loading} columns={columns} dataSource={data} pagination={{ pageSize: 20 }} />

      <Modal
        title={`处置告警 #${handleTarget?.id ?? ''}`}
        open={handleTarget !== null}
        onOk={handle}
        onCancel={() => setHandleTarget(null)}
        destroyOnClose
      >
        <Alert style={{ marginBottom: 16 }} type="warning" showIcon message={handleTarget?.title} />
        <Form form={handleForm} layout="vertical" preserve={false}>
          <Form.Item name="note" label="处置说明" rules={[{ required: true, message: '请输入处置说明' }]}>
            <Input.TextArea rows={4} placeholder="如：已确认为误报 / 已重启实例恢复" />
          </Form.Item>
        </Form>
      </Modal>

      <Drawer
        title={`告警详情 #${detailTarget?.id ?? ''}`}
        open={detailTarget !== null}
        onClose={() => setDetailTarget(null)}
        width={640}
      >
        {detailTarget && (
          <>
            <Descriptions size="small" column={2} bordered>
              <Descriptions.Item label="标题" span={2}>{detailTarget.title}</Descriptions.Item>
              <Descriptions.Item label="级别">{detailTarget.level || '-'}</Descriptions.Item>
              <Descriptions.Item label="状态">
                <Tag color={STATUS_COLORS[detailTarget.status]}>{detailTarget.status}</Tag>
              </Descriptions.Item>
              <Descriptions.Item label="来源">{detailTarget.source || '-'}</Descriptions.Item>
              <Descriptions.Item label="alertKey">{detailTarget.alertKey || '-'}</Descriptions.Item>
              <Descriptions.Item label="Agent">{detailTarget.agentId ?? '-'}</Descriptions.Item>
              <Descriptions.Item label="创建时间">{fmtTime(detailTarget.createdAt)}</Descriptions.Item>
              <Descriptions.Item label="处置人">{detailTarget.handledBy || '-'}</Descriptions.Item>
              <Descriptions.Item label="处置时间">{fmtTime(detailTarget.handledAt)}</Descriptions.Item>
              <Descriptions.Item label="处置说明" span={2}>{detailTarget.handleNote || '-'}</Descriptions.Item>
            </Descriptions>
            <Typography.Title level={5} style={{ marginTop: 16 }}>
              告警明细
            </Typography.Title>
            <pre style={{ maxHeight: 320, overflow: 'auto', background: colors.bgCode, padding: 12, borderRadius: 6 }}>
              {prettyJson(detailTarget.detail)}
            </pre>
          </>
        )}
      </Drawer>
    </div>
  );
}
