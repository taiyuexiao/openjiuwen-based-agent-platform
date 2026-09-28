import { Card } from 'antd';
import type { ReactNode } from 'react';
import ReactECharts from 'echarts-for-react';

interface Props {
  title: string;
  value: ReactNode;
  icon: ReactNode;
  accent: string;
  /** 迷你趋势线数据（可选） */
  spark?: number[];
  hint?: string;
}

/** 工作台统计卡：图标 + 数值 + 可选 sparkline 迷你趋势线 */
export default function StatCard({ title, value, icon, accent, spark, hint }: Props) {
  const sparkOption = spark && spark.length > 1 ? {
    grid: { left: 0, right: 0, top: 4, bottom: 0 },
    xAxis: { show: false, type: 'category' as const, data: spark.map((_, i) => i) },
    yAxis: { show: false, type: 'value' as const, min: 0 },
    series: [
      {
        type: 'line' as const,
        smooth: true,
        symbol: 'none',
        data: spark,
        lineStyle: { color: accent, width: 2 },
        areaStyle: { color: `${accent}1F` },
      },
    ],
    tooltip: { show: false },
  } : null;

  return (
    <Card className="stat-card" styles={{ body: { padding: '16px 18px' } }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
        <div>
          <div style={{ color: 'var(--text-secondary)', fontSize: 12.5, display: 'flex', alignItems: 'center', gap: 6, whiteSpace: 'nowrap' }}>
            <span
              style={{
                width: 26,
                height: 26,
                borderRadius: 8,
                background: `${accent}14`,
                color: accent,
                display: 'inline-flex',
                alignItems: 'center',
                justifyContent: 'center',
                fontSize: 14,
              }}
            >
              {icon}
            </span>
            {title}
          </div>
          <div style={{ fontSize: 26, fontWeight: 650, marginTop: 8, color: 'var(--text-primary)', lineHeight: 1.2 }}>
            {value}
          </div>
          {hint && <div style={{ fontSize: 12, color: 'var(--text-tertiary)', marginTop: 4 }}>{hint}</div>}
        </div>
        {sparkOption && (
          <div style={{ width: 88, height: 40, marginTop: 4 }}>
            <ReactECharts option={sparkOption} style={{ height: 40 }} notMerge />
          </div>
        )}
      </div>
    </Card>
  );
}
