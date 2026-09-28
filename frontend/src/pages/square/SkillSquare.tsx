import { useCallback, useEffect, useMemo, useState } from 'react';
import { Alert, Button, Col, Input, Row, Segmented, Tag } from 'antd';
import { AppstoreOutlined, ReloadOutlined, SearchOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { api } from '../../api/client';
import PageHeader from '../../components/PageHeader';
import ProjectSelect from '../../components/ProjectSelect';
import SquareCard from '../../components/SquareCard';
import EmptyState from '../../components/EmptyState';
import { useProject } from '../../context/ProjectContext';
import type { Asset, AssetType } from '../../types';

/** Skill 广场：当前项目可见的 PUBLISHED 资产卡片墙 */
export default function SkillSquare() {
  const { projectId } = useProject();
  const [type, setType] = useState<AssetType | 'ALL'>('ALL');
  const [keyword, setKeyword] = useState('');
  const [data, setData] = useState<Asset[]>([]);
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();

  const load = useCallback(async () => {
    if (!projectId) {
      setData([]);
      return;
    }
    setLoading(true);
    try {
      setData(await api<Asset[]>('/v1/api/assets/list', { projectId }));
    } catch {
      // helper 已提示
    } finally {
      setLoading(false);
    }
  }, [projectId]);

  useEffect(() => {
    load();
  }, [load]);

  const filtered = useMemo(() => {
    const kw = keyword.trim().toLowerCase();
    return data.filter(
      (a) =>
        a.status === 'PUBLISHED' &&
        (type === 'ALL' || a.assetType === type) &&
        (kw === '' ||
          a.name.toLowerCase().includes(kw) ||
          a.code.toLowerCase().includes(kw) ||
          (a.description ?? '').toLowerCase().includes(kw)),
    );
  }, [data, type, keyword]);

  return (
    <>
      <PageHeader
        title="Skill 广场"
        subTitle="当前项目可用的已发布资产（含被共享/授权进来的资产）；切换顶栏项目查看不同项目视图"
        extra={
          <>
            <ProjectSelect />
            <Segmented
              value={type}
              onChange={(v) => setType(v as AssetType | 'ALL')}
              options={[
                { value: 'ALL', label: '全部' },
                { value: 'SKILL', label: 'SKILL' },
                { value: 'MCP_SERVICE', label: 'MCP 服务' },
                { value: 'MCP_TOOL', label: 'MCP 工具' },
                { value: 'HTTP_API', label: 'HTTP API' },
              ]}
            />
            <Input
              placeholder="搜索名称 / 编码"
              allowClear
              prefix={<SearchOutlined style={{ color: 'var(--text-tertiary)' }} />}
              style={{ width: 220 }}
              value={keyword}
              onChange={(e) => setKeyword(e.target.value)}
            />
            <Button icon={<ReloadOutlined />} onClick={load} disabled={!projectId}>
              刷新
            </Button>
          </>
        }
      />
      {!projectId && (
        <Alert style={{ marginBottom: 16 }} type="info" showIcon message="请先在顶栏选择项目，资产可见性按项目计算" />
      )}
      {filtered.length === 0 && !loading ? (
        <div className="soft-card" style={{ background: '#fff', borderRadius: 12 }}>
          <EmptyState
            description={projectId ? '暂无已发布资产，到资产中心上传/发布后可在此展示' : '请先在顶栏选择项目'}
            actionText={projectId ? '去上传 Skill' : undefined}
            onAction={projectId ? () => navigate('/assets/upload') : undefined}
          />
        </div>
      ) : (
        <Row gutter={[16, 16]}>
          {filtered.map((a) => (
            <Col xs={24} sm={12} lg={8} xl={6} key={a.id}>
              <SquareCard
                loading={loading}
                banner="teal"
                icon={<AppstoreOutlined />}
                title={a.name}
                code={a.code}
                corner={<Tag style={{ marginRight: 0 }}>{a.assetType}</Tag>}
                description={a.description}
                meta={
                  <>
                    <Tag
                      style={{ marginRight: 0 }}
                      color={a.visibility === 'SHARED' ? 'orange' : undefined}
                    >
                      {a.visibility}
                    </Tag>
                    <span>归属项目 #{a.ownerProjectId}</span>
                  </>
                }
                actions={
                  <Button
                    size="small"
                    type="link"
                    onClick={() => navigate(`/assets/${a.id}?projectId=${a.ownerProjectId}`)}
                  >
                    查看 →
                  </Button>
                }
                onClick={() => navigate(`/assets/${a.id}?projectId=${a.ownerProjectId}`)}
              />
            </Col>
          ))}
        </Row>
      )}
    </>
  );
}
