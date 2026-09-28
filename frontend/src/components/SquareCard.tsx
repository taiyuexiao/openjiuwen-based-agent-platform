import { Card, Typography } from 'antd';
import type { ReactNode } from 'react';

export type BannerTone = 'brand' | 'purple' | 'teal' | 'orange';

interface Props {
  icon: ReactNode;
  title: string;
  code?: string;
  banner?: BannerTone;
  /** 右上角角标（如接入模式/类型 Tag） */
  corner?: ReactNode;
  description?: string;
  /** 底部元信息行（版本/项目/时间等） */
  meta?: ReactNode;
  /** hover 时显示的操作区 */
  actions?: ReactNode;
  onClick?: () => void;
  loading?: boolean;
}

/** 广场统一卡片：渐变 Banner + 图标 + 名称，描述两行截断，底部元信息，hover 浮现操作 */
export default function SquareCard({
  icon,
  title,
  code,
  banner = 'brand',
  corner,
  description,
  meta,
  actions,
  onClick,
  loading,
}: Props) {
  return (
    <Card className="square-card" hoverable onClick={onClick} loading={loading} styles={{ body: { padding: 0 } }}>
      <div className={`square-banner square-banner-${banner}`}>
        <span
          style={{
            width: 36,
            height: 36,
            borderRadius: 10,
            background: 'rgba(255, 255, 255, 0.18)',
            display: 'inline-flex',
            alignItems: 'center',
            justifyContent: 'center',
            fontSize: 17,
            backdropFilter: 'blur(4px)',
          }}
        >
          {icon}
        </span>
        <div style={{ minWidth: 0, flex: 1 }}>
          <div style={{ fontWeight: 600, fontSize: 15, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
            {title}
          </div>
          {code && (
            <div style={{ fontSize: 11, opacity: 0.75, fontFamily: 'monospace', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
              {code}
            </div>
          )}
        </div>
        {corner && (
          <span
            style={{
              background: 'rgba(255, 255, 255, 0.92)',
              borderRadius: 999,
              display: 'inline-flex',
              alignItems: 'center',
              flexShrink: 0,
            }}
          >
            {corner}
          </span>
        )}
      </div>
      <div style={{ padding: '12px 16px 14px' }}>
        <Typography.Paragraph
          style={{ fontSize: 12.5, color: 'var(--text-secondary)', minHeight: 40, marginBottom: 8 }}
          ellipsis={{ rows: 2 }}
        >
          {description || '暂无描述'}
        </Typography.Paragraph>
        <div
          style={{
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            borderTop: '1px solid var(--border-light)',
            paddingTop: 10,
            minHeight: 30,
          }}
        >
          <div style={{ fontSize: 12, color: 'var(--text-tertiary)', display: 'flex', gap: 6, alignItems: 'center', flexWrap: 'wrap' }}>
            {meta}
          </div>
          <div className="square-card-actions" onClick={(e) => e.stopPropagation()}>
            {actions}
          </div>
        </div>
      </div>
    </Card>
  );
}
