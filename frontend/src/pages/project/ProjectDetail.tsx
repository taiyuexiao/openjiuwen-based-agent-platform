import { useCallback, useEffect, useState } from 'react';
import { Button, Card, Descriptions, Space, Tabs } from 'antd';
import { ReloadOutlined } from '@ant-design/icons';
import { useParams } from 'react-router-dom';
import { api } from '../../api/client';
import PageHeader from '../../components/PageHeader';
import StatusBadge from '../../components/StatusBadge';
import type { Project } from '../../types';
import { fmtTime } from '../../utils/format';
import MembersTab from './MembersTab';
import EnvsTab from './EnvsTab';
import ModelGrantsTab from './ModelGrantsTab';
import KbGrantsTab from './KbGrantsTab';

export default function ProjectDetail() {
  const { id } = useParams<{ id: string }>();
  const projectId = Number(id);
  const [project, setProject] = useState<Project | null>(null);
  const [loading, setLoading] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setProject(await api<Project>('/v1/api/projects/detail', { id: projectId }));
    } catch {
      // helper 已提示
    } finally {
      setLoading(false);
    }
  }, [projectId]);

  useEffect(() => {
    load();
  }, [load]);

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <PageHeader
        backTo="/projects"
        title={project ? `${project.name}（${project.code}）` : `项目 #${projectId}`}
        badge={project && <StatusBadge value={project.status} />}
        subTitle={project ? `ID ${project.id} · 负责人 ${project.ownerId}` : undefined}
        extra={<Button icon={<ReloadOutlined />} onClick={load}>刷新</Button>}
      />
      <Card className="detail-hero" loading={loading}>
        {project && (
          <Descriptions size="small" column={4}>
            <Descriptions.Item label="ID">{project.id}</Descriptions.Item>
            <Descriptions.Item label="负责人">{project.ownerId}</Descriptions.Item>
            <Descriptions.Item label="状态">
              <StatusBadge value={project.status} />
            </Descriptions.Item>
            <Descriptions.Item label="创建时间">{fmtTime(project.createdAt)}</Descriptions.Item>
            <Descriptions.Item label="描述" span={4}>{project.description || '-'}</Descriptions.Item>
          </Descriptions>
        )}
      </Card>
      <Card className="soft-card">
        <Tabs
          items={[
            { key: 'members', label: '成员管理', children: <MembersTab projectId={projectId} /> },
            { key: 'envs', label: '环境配额', children: <EnvsTab projectId={projectId} /> },
            { key: 'model-grants', label: '模型授权', children: <ModelGrantsTab projectId={projectId} /> },
            { key: 'kb-grants', label: '知识库授权', children: <KbGrantsTab projectId={projectId} /> },
          ]}
        />
      </Card>
    </Space>
  );
}
