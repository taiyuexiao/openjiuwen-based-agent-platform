import type { ReactNode } from 'react';
import { Button, Space, Typography } from 'antd';
import { ArrowLeftOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';

interface Props {
  title: ReactNode;
  subTitle?: ReactNode;
  extra?: ReactNode;
  /** 返回按钮目标路由（详情页用） */
  backTo?: string;
  /** 标题旁的状态徽标等 */
  badge?: ReactNode;
}

/** 统一页头：返回按钮 + 标题 + 状态徽标 + 副标题 + 右侧操作区 */
export default function PageHeader({ title, subTitle, extra, backTo, badge }: Props) {
  const navigate = useNavigate();
  return (
    <div
      className="page-fade"
      style={{
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'flex-start',
        marginBottom: 20,
        gap: 16,
        flexWrap: 'wrap',
      }}
    >
      <div>
        <Space size={8} align="center">
          {backTo && (
            <Button
              type="text"
              size="small"
              icon={<ArrowLeftOutlined />}
              onClick={() => navigate(backTo)}
              style={{ color: 'var(--text-secondary)' }}
            />
          )}
          <Typography.Title level={4} style={{ margin: 0, fontWeight: 650 }}>
            {title}
          </Typography.Title>
          {badge}
        </Space>
        {subTitle && (
          <div style={{ marginTop: 4 }}>
            <Typography.Text type="secondary" style={{ fontSize: 13 }}>
              {subTitle}
            </Typography.Text>
          </div>
        )}
      </div>
      {extra && <Space wrap>{extra}</Space>}
    </div>
  );
}
