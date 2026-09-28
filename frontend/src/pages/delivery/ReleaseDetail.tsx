import { useCallback, useEffect, useState } from 'react';
import { colors } from '../../theme';
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
  Steps,
  Table,
  Tag,
  Typography,
  message,
} from 'antd';
import { ArrowLeftOutlined, ReloadOutlined } from '@ant-design/icons';
import { useNavigate, useParams, useSearchParams } from 'react-router-dom';
import type { ColumnsType } from 'antd/es/table';
import { api } from '../../api/client';
import JsonView from '../../components/JsonView';
import PageHeader from '../../components/PageHeader';
import StatusBadge from '../../components/StatusBadge';
import type { Deployment, Release } from '../../types';
import { fmtTime, prettyJson } from '../../utils/format';
import { RELEASE_STATUS_COLORS } from './ReleaseList';

const FLOW_STEPS = ['创建（DRAFT）', '质量门禁（GATED）', '审批（APPROVED）', '部署（DEPLOYING）', '运行（RUNNING）'];

function stepState(status: string): { current: number; stepStatus: 'process' | 'finish' | 'error' } {
  switch (status) {
    case 'DRAFT':
      return { current: 0, stepStatus: 'process' };
    case 'GATED':
      return { current: 1, stepStatus: 'process' };
    case 'APPROVED':
      return { current: 2, stepStatus: 'process' };
    case 'DEPLOYING':
      return { current: 3, stepStatus: 'process' };
    case 'RUNNING':
      return { current: 4, stepStatus: 'finish' };
    case 'FAILED':
      return { current: 3, stepStatus: 'error' };
    case 'ROLLED_BACK':
      return { current: 4, stepStatus: 'finish' };
    default:
      return { current: 0, stepStatus: 'process' };
  }
}

/** 发布单详情：状态机 Steps + gateResult + 按状态流转操作（gate/approve/deploy/rollback）+ 部署实例 */
export default function ReleaseDetail() {
  const { id } = useParams<{ id: string }>();
  const releaseId = Number(id);
  const [searchParams] = useSearchParams();
  const [projectId, setProjectId] = useState<number | undefined>(() => {
    const p = Number(searchParams.get('projectId'));
    return Number.isFinite(p) && p > 0 ? p : undefined;
  });
  const navigate = useNavigate();

  const [release, setRelease] = useState<Release | null>(null);
  const [deployments, setDeployments] = useState<Deployment[]>([]);
  const [loading, setLoading] = useState(false);
  const [acting, setActing] = useState(false);
  const [approveOpen, setApproveOpen] = useState(false);
  const [approveForm] = Form.useForm<{ approvalRef: string }>();

  const load = useCallback(async () => {
    if (!projectId) return;
    setLoading(true);
    try {
      const [r, d] = await Promise.all([
        api<Release>('/v1/api/releases/detail', { projectId, id: releaseId }),
        api<Deployment[]>('/v1/api/deployments/list', { projectId, releaseId }),
      ]);
      setRelease(r);
      setDeployments(d);
    } catch {
      // helper 已提示
    } finally {
      setLoading(false);
    }
  }, [projectId, releaseId]);

  useEffect(() => {
    load();
  }, [load]);

  const act = async (action: 'gate' | 'deploy' | 'rollback') => {
    if (!projectId) return;
    setActing(true);
    try {
      await api<Release>(`/v1/api/releases/${action}`, { projectId, id: releaseId });
      message.success({ gate: '门禁执行完成', deploy: '部署已触发', rollback: '回滚已执行' }[action]);
      await load();
    } finally {
      setActing(false);
    }
  };

  const approve = async () => {
    if (!projectId) return;
    const values = await approveForm.validateFields();
    setActing(true);
    try {
      await api<Release>('/v1/api/releases/approve', {
        projectId,
        id: releaseId,
        approvalRef: values.approvalRef,
      });
      message.success('审批通过');
      setApproveOpen(false);
      approveForm.resetFields();
      await load();
    } finally {
      setActing(false);
    }
  };

  const stopDeployment = async (deploymentId: number) => {
    if (!projectId) return;
    await api<Deployment>('/v1/api/deployments/stop', { projectId, id: deploymentId });
    message.success('部署实例已停止');
    load();
  };

  if (!projectId) {
    return (
      <Card title="发布单详情">
        <Alert
          type="info"
          showIcon
          message="该页面需要 projectId 上下文"
          description={
            <Space>
              <span>请输入项目 ID：</span>
              <InputNumber min={1} precision={0} onChange={(v) => setProjectId(v ?? undefined)} />
            </Space>
          }
        />
      </Card>
    );
  }

  const { current, stepStatus } = release ? stepState(release.status) : { current: 0, stepStatus: 'process' as const };

  const deploymentColumns: ColumnsType<Deployment> = [
    { title: 'ID', dataIndex: 'id', width: 70 },
    { title: '目标', dataIndex: 'targetId', width: 80 },
    { title: '执行器', dataIndex: 'executor', width: 100, render: (v?: string) => v ?? '-' },
    { title: '实例地址', dataIndex: 'instanceUrl', ellipsis: true, render: (v?: string) => v ?? '-' },
    { title: '副本', dataIndex: 'replicas', width: 70, render: (v?: number) => v ?? '-' },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (s: string) => (
        <Tag color={s === 'RUNNING' ? 'green' : s === 'FAILED' ? 'red' : s === 'STOPPED' ? 'default' : 'orange'}>{s}</Tag>
      ),
    },
    {
      title: '健康',
      dataIndex: 'healthStatus',
      width: 110,
      render: (s?: string) =>
        s ? <Tag color={s === 'HEALTHY' ? 'green' : s === 'UNHEALTHY' ? 'red' : 'default'}>{s}</Tag> : '-',
    },
    { title: '启动时间', dataIndex: 'startedAt', width: 170, render: fmtTime },
    {
      title: '操作',
      width: 90,
      render: (_, r) =>
        r.status === 'RUNNING' || r.status === 'PENDING' ? (
          <Popconfirm title="确认停止该部署实例？" onConfirm={() => stopDeployment(r.id)}>
            <Typography.Link type="danger">停止</Typography.Link>
          </Popconfirm>
        ) : null,
    },
  ];

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <PageHeader
        backTo="/delivery/releases"
        title={`发布单 #${releaseId}`}
        badge={release && <StatusBadge value={release.status} />}
        subTitle={release ? `Agent #${release.agentId} · v${release.agentVersion} · 目标 #${release.targetId}` : undefined}
        extra={<Button icon={<ReloadOutlined />} onClick={load}>刷新</Button>}
      />
      <Card className="detail-hero" loading={loading}>
        {release && (
          <>
            <Steps
              current={current}
              status={stepStatus}
              items={FLOW_STEPS.map((title) => ({ title }))}
              style={{ marginBottom: 24 }}
            />
            {release.status === 'ROLLED_BACK' && (
              <Alert
                style={{ marginBottom: 16 }}
                type="warning"
                showIcon
                message={`该发布单已回滚（rollbackOf=${release.rollbackOf ?? '-'}），回滚会生成新的 DRAFT 发布单`}
              />
            )}
            <Descriptions size="small" column={4}>
              <Descriptions.Item label="Agent">{release.agentId}</Descriptions.Item>
              <Descriptions.Item label="版本">{release.agentVersion}</Descriptions.Item>
              <Descriptions.Item label="制品">{release.artifactId}</Descriptions.Item>
              <Descriptions.Item label="目标">{release.targetId}</Descriptions.Item>
              <Descriptions.Item label="等级快照" span={2}>
                <JsonView value={release.levelSnapshot} title="等级快照" />
              </Descriptions.Item>
              <Descriptions.Item label="审批引用">{release.approvalRef || '-'}</Descriptions.Item>
              <Descriptions.Item label="创建人">{release.createdBy}</Descriptions.Item>
            </Descriptions>

            <Space style={{ marginTop: 16 }} wrap>
              {(release.status === 'DRAFT' || release.status === 'GATED') && (
                <Popconfirm title="确认执行质量门禁（gate）？" onConfirm={() => act('gate')}>
                  <Button type="primary" loading={acting}>
                    执行门禁（gate）
                  </Button>
                </Popconfirm>
              )}
              {release.status === 'GATED' && (
                <Button type="primary" loading={acting} onClick={() => setApproveOpen(true)}>
                  审批（approve）
                </Button>
              )}
              {release.status === 'APPROVED' && (
                <Popconfirm title="确认触发部署（deploy）？" onConfirm={() => act('deploy')}>
                  <Button type="primary" loading={acting}>
                    部署（deploy）
                  </Button>
                </Popconfirm>
              )}
              {release.status === 'RUNNING' && (
                <Popconfirm title="确认回滚该发布（rollback）？" onConfirm={() => act('rollback')}>
                  <Button danger loading={acting}>
                    回滚（rollback）
                  </Button>
                </Popconfirm>
              )}
            </Space>
          </>
        )}
      </Card>

      <Card size="small" title="门禁结果 gate_result">
        {release?.gateResult ? (
          <pre style={{ maxHeight: 360, overflow: 'auto', background: colors.bgCode, padding: 12, borderRadius: 6 }}>
            {prettyJson(release.gateResult)}
          </pre>
        ) : (
          <Typography.Text type="secondary">尚未执行门禁或无结果</Typography.Text>
        )}
      </Card>

      <Card size="small" title="部署实例">
        <Table rowKey="id" columns={deploymentColumns} dataSource={deployments} pagination={false} />
      </Card>

      <Modal title="发布审批" open={approveOpen} onOk={approve} onCancel={() => setApproveOpen(false)} destroyOnClose>
        <Form form={approveForm} layout="vertical" preserve={false}>
          <Form.Item
            name="approvalRef"
            label="审批引用（如 ITSM 变更单号）"
            rules={[{ required: true, message: '请输入审批引用' }, { max: 64 }]}
          >
            <Input placeholder="如 CHG20260927001" />
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  );
}
