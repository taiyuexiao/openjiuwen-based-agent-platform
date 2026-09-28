import { useCallback, useEffect, useState } from 'react';
import { Alert, Button, Card, Select, Space, Table, Tag } from 'antd';
import { ReloadOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import { api } from '../../api/client';
import EmptyState from '../../components/EmptyState';
import PageHeader from '../../components/PageHeader';
import ProjectSelect from '../../components/ProjectSelect';
import { useProject } from '../../context/ProjectContext';
import type { EnvType, ServiceDirectoryEntry } from '../../types';

/** 服务目录（只读）：对调用方暴露的已发布 Agent 服务 */
export default function ServiceDirectory() {
  const { projectId } = useProject();
  const [envFilter, setEnvFilter] = useState<EnvType | undefined>();
  const [data, setData] = useState<ServiceDirectoryEntry[]>([]);
  const [loading, setLoading] = useState(false);

  const load = useCallback(async () => {
    if (!projectId) {
      setData([]);
      return;
    }
    setLoading(true);
    try {
      setData(
        await api<ServiceDirectoryEntry[]>('/v1/api/service-directory/list', {
          projectId,
          env: envFilter ?? undefined,
        }),
      );
    } catch {
      // helper 已提示
    } finally {
      setLoading(false);
    }
  }, [projectId, envFilter]);

  useEffect(() => {
    load();
  }, [load]);

  const columns: ColumnsType<ServiceDirectoryEntry> = [
    { title: 'Agent ID', dataIndex: 'agentId', width: 90 },
    { title: 'Agent 编码', dataIndex: 'agentCode', width: 180 },
    { title: 'Agent 名称', dataIndex: 'agentName' },
    {
      title: '环境',
      dataIndex: 'env',
      width: 90,
      render: (e: string) => <Tag color={e === 'PROD' ? 'red' : e === 'DEV' ? 'green' : 'orange'}>{e}</Tag>,
    },
    { title: '版本', dataIndex: 'agentVersion', width: 110 },
    { title: '服务地址', dataIndex: 'address', ellipsis: true, render: (v?: string) => v ?? '-' },
    { title: '认证方式', dataIndex: 'authType', width: 140, render: (v?: string) => v ?? '-' },
  ];

  return (
    <>
      <PageHeader title="服务目录" subTitle="对调用方暴露的已发布 Agent 服务（标识 / 环境 / 版本 / 地址 / 认证方式）" />
      <Card className="soft-card">
      <Space wrap style={{ marginBottom: 16 }}>
        <ProjectSelect />
        <Select
          allowClear
          placeholder="按环境筛选"
          style={{ minWidth: 130 }}
          value={envFilter}
          onChange={setEnvFilter}
          options={['DEV', 'SIT', 'UAT', 'PROD'].map((e) => ({ value: e, label: e }))}
        />
        <Button icon={<ReloadOutlined />} onClick={load} disabled={!projectId}>
          刷新
        </Button>
      </Space>
      {!projectId && <Alert style={{ marginBottom: 16 }} type="info" showIcon message="请先选择项目" />}
      <Table locale={{ emptyText: <EmptyState small description="暂无已发布服务，路由 sync 后在此可见" /> }} rowKey={(r) => `${r.agentId}-${r.env}`} loading={loading} columns={columns} dataSource={data} pagination={false} />
    </Card>
    </>
  );
}
