import { Button, Empty } from 'antd';
import type { ReactNode } from 'react';

interface Props {
  description: ReactNode;
  actionText?: string;
  onAction?: () => void;
  /** 小号（用于卡片内嵌） */
  small?: boolean;
}

/** 统一空状态：文案引导 + 可选操作按钮 */
export default function EmptyState({ description, actionText, onAction, small }: Props) {
  return (
    <div style={{ padding: small ? '24px 0' : '48px 0' }}>
      <Empty
        image={Empty.PRESENTED_IMAGE_SIMPLE}
        description={<span style={{ color: 'var(--text-tertiary)', fontSize: 13 }}>{description}</span>}
      >
        {actionText && onAction && (
          <Button type="primary" onClick={onAction}>
            {actionText}
          </Button>
        )}
      </Empty>
    </div>
  );
}
