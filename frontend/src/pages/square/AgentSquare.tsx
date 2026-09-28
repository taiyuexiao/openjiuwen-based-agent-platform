import { useCallback, useEffect, useState } from 'react';
import { Button, Col, Input, Row, Tag } from 'antd';
import { ReloadOutlined, RobotOutlined, SearchOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { api } from '../../api/client';
import PageHeader from '../../components/PageHeader';
import SquareCard from '../../components/SquareCard';
import EmptyState from '../../components/EmptyState';
import type { AgentSquareItem } from '../../types';
import { fmtTime } from '../../utils/format';

const MODE_TAG_COLORS: Record<string, string> = {
  NATIVE: 'green',
  ADAPTED: 'blue',
  HOSTED: 'orange',
};

/** Agent 广场：PUBLIC Agent 卡片墙（平台级只读） */
export default function AgentSquare() {
  const [keyword, setKeyword] = useState('');
  const [data, setData] = useState<AgentSquareItem[]>([]);
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();

  const load = useCallback(async (kw?: string) => {
    setLoading(true);
    try {
      setData(await api<AgentSquareItem[]>('/v1/api/agent-square/list', kw ? { keyword: kw } : {}));
    } catch {
      // helper 已提示
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  return (
    <>
      <PageHeader
        title="Agent 广场"
        subTitle="全平台已公开发布（PUBLIC）的 Agent；在 Agent 详情页可「发布到广场」"
        extra={
          <>
            <Input
              placeholder="搜索名称 / 编码 / 描述"
              allowClear
              prefix={<SearchOutlined style={{ color: 'var(--text-tertiary)' }} />}
              style={{ width: 280 }}
              value={keyword}
              onChange={(e) => setKeyword(e.target.value)}
              onPressEnter={() => load(keyword.trim() || undefined)}
            />
            <Button icon={<ReloadOutlined />} onClick={() => load(keyword.trim() || undefined)}>
              刷新
            </Button>
          </>
        }
      />
      {data.length === 0 && !loading ? (
        <div className="soft-card" style={{ background: '#fff', borderRadius: 12 }}>
          <EmptyState
            description="暂无公开的 Agent；到 Agent 详情页点击「发布到广场」即可上架"
            actionText="去 Agent 管理"
            onAction={() => navigate('/agents')}
          />
        </div>
      ) : (
        <Row gutter={[16, 16]}>
          {data.map((a) => (
            <Col xs={24} sm={12} lg={8} xl={6} key={a.id}>
              <SquareCard
                loading={loading}
                banner="purple"
                icon={<RobotOutlined />}
                title={a.name}
                code={a.code}
                corner={
                  a.accessMode && (
                    <Tag color={MODE_TAG_COLORS[a.accessMode]} style={{ marginRight: 0 }}>
                      {a.accessMode}
                    </Tag>
                  )
                }
                description={a.description}
                meta={
                  <>
                    <span>项目：{a.projectName ?? `#${a.projectId}`}</span>
                    <span>·</span>
                    <span>最新 {a.latestVersion ?? '-'}</span>
                    <span>·</span>
                    <span>{a.versionCount} 版本</span>
                    <span>·</span>
                    <span>{fmtTime(a.createdAt).slice(0, 10)}</span>
                  </>
                }
                actions={
                  <Button
                    size="small"
                    type="link"
                    onClick={() => navigate(`/agents/${a.id}?projectId=${a.projectId}`)}
                  >
                    查看 →
                  </Button>
                }
                onClick={() => navigate(`/agents/${a.id}?projectId=${a.projectId}`)}
              />
            </Col>
          ))}
        </Row>
      )}
    </>
  );
}
