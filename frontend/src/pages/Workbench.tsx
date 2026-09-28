import { useCallback, useEffect, useState } from 'react';
import { Card, Col, Row, Typography } from 'antd';
import {
  AlertOutlined,
  ApiOutlined,
  AppstoreOutlined,
  CheckCircleOutlined,
  CloudUploadOutlined,
  ProjectOutlined,
  RobotOutlined,
  RocketOutlined,
} from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import ReactECharts from 'echarts-for-react';
import { api } from '../api/client';
import PageHeader from '../components/PageHeader';
import StatCard from '../components/StatCard';
import EmptyState from '../components/EmptyState';
import { useProject } from '../context/ProjectContext';
import type { Agent, Asset, Project, StatsOverviewResp } from '../types';
import { colors } from '../theme';

const CHART_TOOLTIP = {
  trigger: 'axis' as const,
  backgroundColor: '#fff',
  borderColor: colors.borderLight,
  borderWidth: 1,
  padding: [8, 12],
  textStyle: { color: colors.textPrimary, fontSize: 12 },
  extraCssText: 'border-radius: 8px; box-shadow: 0 8px 24px rgba(23, 43, 99, 0.14);',
};

/** 工作台：平台/项目级统计 + 调用趋势 + Top Agent + 快捷入口 */
export default function Workbench() {
  const { projectId } = useProject();
  const navigate = useNavigate();
  const [projects, setProjects] = useState<Project[]>([]);
  const [agentCount, setAgentCount] = useState<number | null>(null);
  const [assetCount, setAssetCount] = useState<number | null>(null);
  const [stats, setStats] = useState<StatsOverviewResp | null>(null);
  const [loading, setLoading] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const ps = await api<Project[]>('/v1/api/projects/list');
      setProjects(ps);
      if (projectId) {
        const [agents, assets, overview] = await Promise.all([
          api<Agent[]>('/v1/api/agents/list', { projectId }),
          api<Asset[]>('/v1/api/assets/list', { projectId }),
          api<StatsOverviewResp>('/v1/api/observability/stats/overview', { projectId }),
        ]);
        setAgentCount(agents.length);
        setAssetCount(assets.length);
        setStats(overview);
      } else {
        setAgentCount(null);
        setAssetCount(null);
        setStats(await api<StatsOverviewResp>('/v1/api/observability/stats/overview', {}));
      }
    } catch {
      // helper 已提示
    } finally {
      setLoading(false);
    }
  }, [projectId]);

  useEffect(() => {
    load();
  }, [load]);

  const trend = stats?.dailyTrend ?? [];
  const today = trend[trend.length - 1];

  const trendOption = {
    tooltip: CHART_TOOLTIP,
    legend: { data: ['总调用', '成功', '失败'], right: 8, textStyle: { color: colors.textSecondary, fontSize: 12 } },
    grid: { left: 40, right: 16, top: 36, bottom: 28 },
    xAxis: {
      type: 'category' as const,
      data: trend.map((d) => d.date.slice(5)),
      axisLine: { lineStyle: { color: colors.borderStrong } },
      axisLabel: { color: colors.textTertiary, fontSize: 11 },
      axisTick: { show: false },
    },
    yAxis: {
      type: 'value' as const,
      splitLine: { lineStyle: { color: colors.borderLight, type: 'dashed' as const } },
      axisLabel: { color: colors.textTertiary, fontSize: 11 },
    },
    series: [
      {
        name: '总调用',
        type: 'line' as const,
        smooth: true,
        symbol: 'circle',
        symbolSize: 5,
        data: trend.map((d) => d.total),
        lineStyle: { color: colors.brand, width: 2.5 },
        itemStyle: { color: colors.brand },
        areaStyle: {
          color: {
            type: 'linear' as const, x: 0, y: 0, x2: 0, y2: 1,
            colorStops: [
              { offset: 0, color: 'rgba(43, 90, 237, 0.16)' },
              { offset: 1, color: 'rgba(43, 90, 237, 0)' },
            ],
          },
        },
      },
      {
        name: '成功',
        type: 'line' as const,
        smooth: true,
        symbol: 'none',
        data: trend.map((d) => d.success),
        lineStyle: { color: colors.success, width: 2 },
        itemStyle: { color: colors.success },
      },
      {
        name: '失败',
        type: 'line' as const,
        smooth: true,
        symbol: 'none',
        data: trend.map((d) => d.failed),
        lineStyle: { color: colors.error, width: 2 },
        itemStyle: { color: colors.error },
      },
    ],
  };

  const topOption = {
    tooltip: { ...CHART_TOOLTIP, trigger: 'item' as const },
    grid: { left: 96, right: 24, top: 8, bottom: 28 },
    xAxis: {
      type: 'value' as const,
      splitLine: { lineStyle: { color: colors.borderLight, type: 'dashed' as const } },
      axisLabel: { color: colors.textTertiary, fontSize: 11 },
    },
    yAxis: {
      type: 'category' as const,
      data: (stats?.topAgents ?? []).map((t) => `Agent #${t.agentId}`).reverse(),
      axisLine: { lineStyle: { color: colors.borderStrong } },
      axisLabel: { color: colors.textSecondary, fontSize: 12 },
      axisTick: { show: false },
    },
    series: [
      {
        type: 'bar' as const,
        data: (stats?.topAgents ?? []).map((t) => t.calls).reverse(),
        itemStyle: {
          borderRadius: [0, 6, 6, 0],
          color: {
            type: 'linear' as const, x: 0, y: 0, x2: 1, y2: 0,
            colorStops: [
              { offset: 0, color: colors.brand },
              { offset: 1, color: '#6C8BFF' },
            ],
          },
        },
        barMaxWidth: 18,
      },
    ],
  };

  const statCards = [
    { title: '我的项目', value: projects.length, icon: <ProjectOutlined />, accent: colors.brand },
    {
      title: 'Agent 数',
      value: agentCount ?? '—',
      icon: <RobotOutlined />,
      accent: colors.purple,
      hint: projectId ? undefined : '选择项目后展示',
    },
    {
      title: '资产数',
      value: assetCount ?? '—',
      icon: <AppstoreOutlined />,
      accent: colors.teal,
      hint: projectId ? undefined : '选择项目后展示',
    },
    {
      title: '今日调用',
      value: today?.total ?? 0,
      icon: <ApiOutlined />,
      accent: colors.brand,
      spark: trend.map((d) => d.total),
    },
    {
      title: '成功率',
      value: stats ? `${(stats.successRate * 100).toFixed(1)}%` : '—',
      icon: <CheckCircleOutlined />,
      accent: colors.success,
      spark: trend.map((d) => (d.total > 0 ? (d.success / d.total) * 100 : 0)),
    },
    { title: '未处置告警', value: stats?.alertOpenCount ?? 0, icon: <AlertOutlined />, accent: colors.error },
  ];

  const quickEntries = [
    { title: '新建 Agent', desc: '三步向导创建并接入', icon: <RobotOutlined />, to: '/agents/new', accent: colors.brand },
    { title: '上传 Skill', desc: '文件上传即得草稿版本', icon: <CloudUploadOutlined />, to: '/assets/upload', accent: colors.purple },
    { title: '发起发布', desc: '制品 → 门禁 → 审批 → 部署', icon: <RocketOutlined />, to: '/delivery/releases', accent: colors.orange },
  ];

  return (
    <>
      <PageHeader
        title="工作台"
        subTitle={
          projectId
            ? '当前项目维度统计（Agent / 资产 / 调用趋势）'
            : '平台维度统计；在顶栏选择项目后可查看项目级 Agent 与资产数'
        }
      />
      <Row gutter={[16, 16]}>
        {statCards.map((c) => (
          <Col xs={12} sm={8} lg={4} key={c.title}>
            <StatCard
              title={c.title}
              value={loading ? '…' : c.value}
              icon={c.icon}
              accent={c.accent}
              spark={'spark' in c ? c.spark : undefined}
              hint={c.hint}
            />
          </Col>
        ))}
      </Row>

      <Row gutter={[16, 16]} style={{ marginTop: 16 }}>
        <Col xs={24} lg={14}>
          <Card title="近 7 天调用趋势" className="soft-card" loading={loading}>
            <ReactECharts option={trendOption} style={{ height: 300 }} notMerge />
          </Card>
        </Col>
        <Col xs={24} lg={10}>
          <Card title="Top Agent 调用排行" className="soft-card" loading={loading}>
            {stats && stats.topAgents.length > 0 ? (
              <ReactECharts option={topOption} style={{ height: 300 }} notMerge />
            ) : (
              <EmptyState small description="暂无调用数据（产生运行时调用后在此展示排行）" />
            )}
          </Card>
        </Col>
      </Row>

      <Row gutter={[16, 16]} style={{ marginTop: 16 }}>
        {quickEntries.map((q) => (
          <Col xs={24} sm={8} key={q.title}>
            <Card className="square-card" hoverable onClick={() => navigate(q.to)}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 14 }}>
                <div
                  style={{
                    width: 44,
                    height: 44,
                    borderRadius: 12,
                    background: `${q.accent}14`,
                    color: q.accent,
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    fontSize: 20,
                  }}
                >
                  {q.icon}
                </div>
                <div>
                  <div style={{ fontWeight: 600, fontSize: 15, color: 'var(--text-primary)' }}>{q.title}</div>
                  <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                    {q.desc}
                  </Typography.Text>
                </div>
              </div>
            </Card>
          </Col>
        ))}
      </Row>
    </>
  );
}
