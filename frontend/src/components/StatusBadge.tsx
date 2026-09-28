import { colors } from '../theme';

type Tone = 'brand' | 'success' | 'warning' | 'error' | 'neutral' | 'purple' | 'teal' | 'orange';

const TONE_MAP: Record<Tone, { color: string; bg: string }> = {
  brand: { color: colors.brand, bg: colors.brandSoft },
  success: { color: colors.success, bg: colors.successSoft },
  warning: { color: colors.warning, bg: colors.warningSoft },
  error: { color: colors.error, bg: colors.errorSoft },
  neutral: { color: colors.textSecondary, bg: 'rgba(90, 97, 122, 0.08)' },
  purple: { color: colors.purple, bg: colors.purpleSoft },
  teal: { color: colors.teal, bg: colors.tealSoft },
  orange: { color: colors.orange, bg: colors.orangeSoft },
};

/** 常见状态 → 柔和色调（浅色底 + 深色字，不用刺眼纯色） */
const STATUS_TONE: Record<string, Tone> = {
  ACTIVE: 'success',
  ENABLED: 'success',
  PUBLISHED: 'success',
  REGISTERED: 'success',
  RUNNING: 'success',
  HEALTHY: 'success',
  UP: 'success',
  SYNCED: 'success',
  CLOSED: 'success',
  PUBLIC: 'orange',
  SHARED: 'orange',
  GATED: 'brand',
  APPROVED: 'brand',
  DEPLOYING: 'warning',
  DRAFT: 'neutral',
  PROJECT: 'neutral',
  HANDLED: 'warning',
  STALE: 'warning',
  FIRING: 'error',
  FAILED: 'error',
  OFFLINE: 'error',
  DISABLED: 'neutral',
  ARCHIVED: 'neutral',
  ROLLED_BACK: 'purple',
  REVOKED: 'neutral',
  DRAINED: 'neutral',
  STOPPED: 'neutral',
  UNHEALTHY: 'error',
  DOWN: 'error',
  UNKNOWN: 'neutral',
  UNVERIFIED: 'neutral',
  PENDING: 'warning',
};

interface Props {
  value?: string | null;
  tone?: Tone;
  withDot?: boolean;
}

/** 柔和状态徽标；tone 不传时按内置状态表映射 */
export default function StatusBadge({ value, tone, withDot = true }: Props) {
  if (value === undefined || value === null || value === '') {
    return <span style={{ color: 'var(--text-tertiary)' }}>-</span>;
  }
  const t = TONE_MAP[tone ?? STATUS_TONE[value] ?? 'neutral'];
  return (
    <span className="status-badge" style={{ color: t.color, background: t.bg }}>
      {withDot && <span className="status-dot" />}
      {value}
    </span>
  );
}
