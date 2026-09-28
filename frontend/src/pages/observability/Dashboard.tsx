import { useCallback, useEffect, useState } from 'react';
import { Alert, Card, Col, Row, Select, Space, Statistic, Tabs, Typography } from 'antd';
import { ReloadOutlined } from '@ant-design/icons';
import { Button } from 'antd';
import ReactECharts from 'echarts-for-react';
import { api } from '../../api/client';
import PageHeader from '../../components/PageHeader';
import ProjectSelect from '../../components/ProjectSelect';
import { useProject } from '../../context/ProjectContext';
import type { Agent, StatsOverviewResp } from '../../types';
import { colors } from '../../theme';
import Traces from './Traces';
import Audit from './Audit';
import Alerts from './Alerts';

const PIE_COLORS = [colors.brand, colors.purple, colors.teal, colors.orange, colors.success, colors.error];

/** 可观测大盘：stats/overview 图表区 + 链路/审计/告警查询 tabs */
export default function ObservabilityDashboard() {
  const { projectId } = useProject();
  const [agents, setAgents] = useState<Agent[]>([]);
  const [agentId, setAgentId] = useState<number | undefined>();
  const [stats, setStats] = useState<StatsOverviewResp | null>(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (!projectId) {
      setAgents([]);
      setAgentId(undefined);
      return;
    }
    api<Agent[]>('/v1/api/agents/list', { projectId })
      .then(setAgents)
      .catch(() => undefined);
  }, [projectId]);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setStats(
        await api<StatsOverviewResp>('/v1/api/observability/stats/overview', projectId ? { projectId } : {}),
      );
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
  const trendOption = {
    tooltip: { trigger: 'axis' as const, backgroundColor: '#fff', borderColor: colors.borderLight, borderWidth: 1, padding: [8, 12] as unknown as number[], textStyle: { color: colors.textPrimary, fontSize: 12 }, extraCssText: 'border-radius: 8px; box-shadow: 0 8px 24px rgba(23, 43, 99, 0.14);' },
    legend: { data: ['总调用', '成功', '失败'], right: 8 },
    grid: { left: 40, right: 16, top: 36, bottom: 28 },
    xAxis: { type: 'category' as const, data: trend.map((d) => d.date) },
    yAxis: { type: 'value' as const, splitLine: { lineStyle: { color: colors.borderLight } } },
    series: [
      {
        name: '总调用',
        type: 'line' as const,
        smooth: true,
        data: trend.map((d) => d.total),
        lineStyle: { color: colors.brand, width: 3 },
        itemStyle: { color: colors.brand },
        areaStyle: { color: colors.brandSoft },
      },
      {
        name: '成功',
        type: 'line' as const,
        smooth: true,
        data: trend.map((d) => d.success),
        lineStyle: { color: colors.success },
        itemStyle: { color: colors.success },
      },
      {
        name: '失败',
        type: 'line' as const,
        smooth: true,
        data: trend.map((d) => d.failed),
        lineStyle: { color: colors.error },
        itemStyle: { color: colors.error },
      },
    ],
  };

  const success = trend.reduce((acc, d) => acc + d.success, 0);
  const failed = trend.reduce((acc, d) => acc + d.failed, 0);
  const rateOption = {
    tooltip: { trigger: 'item' as const, backgroundColor: '#fff', borderColor: colors.borderLight, borderWidth: 1, padding: [8, 12] as unknown as number[], textStyle: { color: colors.textPrimary, fontSize: 12 }, extraCssText: 'border-radius: 8px; box-shadow: 0 8px 24px rgba(23, 43, 99, 0.14);' },
    series: [
      {
        type: 'pie' as const,
        radius: ['58%', '80%'],
        center: ['50%', '46%'],
        label: { show: false },
        data: [
          { name: '成功', value: success, itemStyle: { color: colors.success } },
          { name: '失败', value: failed, itemStyle: { color: colors.error } },
        ],
      },
    ],
  };

  const distOption = {
    tooltip: { trigger: 'item' as const, backgroundColor: '#fff', borderColor: colors.borderLight, borderWidth: 1, padding: [8, 12] as unknown as number[], textStyle: { color: colors.textPrimary, fontSize: 12 }, extraCssText: 'border-radius: 8px; box-shadow: 0 8px 24px rgba(23, 43, 99, 0.14);' },
    legend: { bottom: 0 },
    series: [
      {
        type: 'pie' as const,
        radius: ['30%', '62%'],
        center: ['50%', '46%'],
        data: Object.entries(stats?.spanKindDist ?? {}).map(([k, v], i) => ({
          name: k,
          value: v,
          itemStyle: { color: PIE_COLORS[i % PIE_COLORS.length] },
        })),
        label: { formatter: '{b}: {c}' },
      },
    ],
  };

  return (
    <>
      <PageHeader
        title="可观测大盘"
        subTitle="调用趋势、成功率、Span 分布与告警概览；下方可进行链路 / 审计 / 告警明细查询"
        extra={
          <>
            <ProjectSelect />
            <Select
              allowClear
              placeholder="按 Agent 过滤（图表为项目级，此处供下方表格用）"
              style={{ minWidth: 200 }}
              value={agentId}
              onChange={setAgentId}
              disabled={!projectId}
              showSearch
              optionFilterProp="label"
              options={agents.map((a) => ({ value: a.id, label: `${a.code}（${a.name}）` }))}
            />
            <Button icon={<ReloadOutlined />} onClick={load}>
              刷新
            </Button>
          </>
        }
      />
      <Row gutter={[16, 16]}>
        <Col xs={24} lg={12}>
          <Card title="近 7 天调用趋势" className="soft-card" loading={loading}>
            <ReactECharts option={trendOption} style={{ height: 260 }} notMerge />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={4}>
          <Card title="成功率" className="soft-card" loading={loading}>
            <ReactECharts option={rateOption} style={{ height: 200 }} notMerge />
            <div style={{ textAlign: 'center', marginTop: -130, pointerEvents: 'none', position: 'relative' }}>
              <Typography.Text strong style={{ fontSize: 22 }}>
                {stats ? `${(stats.successRate * 100).toFixed(1)}%` : '—'}
              </Typography.Text>
            </div>
            <div style={{ height: 60 }} />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={4}>
          <Card title="Span 类型分布" className="soft-card" loading={loading}>
            <ReactECharts option={distOption} style={{ height: 260 }} notMerge />
          </Card>
        </Col>
        <Col xs={24} lg={4}>
          <Space direction="vertical" size={16} style={{ width: '100%' }}>
            <Card className="soft-card" loading={loading}>
              <Statistic title="总调用量" value={stats?.totalCalls ?? 0} />
            </Card>
            <Card className="soft-card" loading={loading}>
              <Statistic title="平均延迟（ms）" value={stats ? stats.avgLatencyMs.toFixed(0) : 0} />
            </Card>
            <Card className="soft-card" loading={loading}>
              <Statistic
                title="未处置告警"
                value={stats?.alertOpenCount ?? 0}
                valueStyle={{ color: (stats?.alertOpenCount ?? 0) > 0 ? colors.error : undefined }}
              />
            </Card>
          </Space>
        </Col>
      </Row>

      {!projectId && (
        <Alert
          style={{ marginTop: 16 }}
          type="info"
          showIcon
          message="图表当前为平台汇总；在顶栏选择项目后切换为项目维度，并可使用下方明细查询"
        />
      )}

      <Card className="soft-card" style={{ marginTop: 16 }}>
        <Tabs
          items={[
            { key: 'traces', label: '链路追踪', children: <Traces /> },
            { key: 'audit', label: '审计查询', children: <Audit /> },
            { key: 'alerts', label: '告警处置', children: <Alerts /> },
          ]}
        />
      </Card>
    </>
  );
}
