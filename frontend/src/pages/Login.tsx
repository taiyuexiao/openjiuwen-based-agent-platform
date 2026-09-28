import { useState } from 'react';
import { Button, Divider, Form, Input, Typography, message } from 'antd';
import { ArrowRightOutlined } from '@ant-design/icons';
import { useLocation, useNavigate } from 'react-router-dom';
import { api, setToken } from '../api/client';
import { colors } from '../theme';
import type { Project } from '../types';

const SEED_USERS = [
  { id: 'u1001', name: '张三' },
  { id: 'u1002', name: '李四' },
  { id: 'u1003', name: '王五' },
  { id: 'u1004', name: '赵六' },
  { id: 'u1005', name: '钱七' },
  { id: 'u1006', name: '孙八' },
];

const CAPABILITIES = ['Agent 全生命周期管理', '资产广场与版本治理', '发布门禁与分级保障', '全链路观测与处置'];

/** 品牌区装饰：SVG 网格球 + CSS 光晕 */
function BrandOrnament() {
  return (
    <svg width="320" height="320" viewBox="0 0 320 320" style={{ position: 'absolute', right: -60, bottom: -60, opacity: 0.5 }}>
      <defs>
        <linearGradient id="ring" x1="0" y1="0" x2="1" y2="1">
          <stop offset="0%" stopColor="rgba(255,255,255,0.55)" />
          <stop offset="100%" stopColor="rgba(255,255,255,0.08)" />
        </linearGradient>
      </defs>
      <circle cx="160" cy="160" r="150" fill="none" stroke="url(#ring)" strokeWidth="1" />
      <circle cx="160" cy="160" r="110" fill="none" stroke="url(#ring)" strokeWidth="1" />
      <circle cx="160" cy="160" r="70" fill="none" stroke="url(#ring)" strokeWidth="1" />
      <line x1="10" y1="160" x2="310" y2="160" stroke="url(#ring)" strokeWidth="1" />
      <line x1="160" y1="10" x2="160" y2="310" stroke="url(#ring)" strokeWidth="1" />
      <circle cx="160" cy="50" r="5" fill="#9DB4FF" />
      <circle cx="270" cy="160" r="4" fill="#9DB4FF" />
      <circle cx="90" cy="230" r="3" fill="#9DB4FF" />
    </svg>
  );
}

/** 登录：token 即 userId（本地开发简单 token 认证，见后端 application.yml） */
export default function Login() {
  const [form] = Form.useForm<{ token: string }>();
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();
  const location = useLocation();
  const from = (location.state as { from?: string } | null)?.from ?? '/';

  const doLogin = async (token: string) => {
    if (!token.trim()) {
      message.warning('请输入 token（userId）');
      return;
    }
    setLoading(true);
    try {
      setToken(token.trim());
      await api<Project[]>('/v1/api/projects/list');
      message.success('登录成功');
      navigate(from, { replace: true });
    } catch {
      // api helper 已提示；401 时会清理 token
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="login-wrap">
      <div className="login-brand">
        <div className="glow-a" />
        <div className="glow-b" />
        <BrandOrnament />
        <div className="brand-content">
          <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 40 }}>
            <svg width="40" height="40" viewBox="0 0 40 40">
              <rect x="2" y="2" width="36" height="36" rx="10" fill="rgba(255,255,255,0.12)" />
              <path d="M12 26 L20 12 L28 26" stroke="#fff" strokeWidth="2.5" fill="none" strokeLinecap="round" strokeLinejoin="round" />
              <circle cx="20" cy="22" r="2.6" fill="#9DB4FF" />
            </svg>
            <div>
              <div style={{ fontSize: 18, fontWeight: 650, letterSpacing: 1 }}>高码智能体平台</div>
              <div style={{ fontSize: 11, opacity: 0.55, letterSpacing: 2 }}>AGENTOPS CONSOLE</div>
            </div>
          </div>
          <Typography.Title style={{ color: '#fff', fontWeight: 650, fontSize: 34, lineHeight: 1.35, marginBottom: 16 }}>
            智能体研发、交付
            <br />
            与治理的一站式控制台
          </Typography.Title>
          <Typography.Paragraph style={{ color: 'rgba(255,255,255,0.62)', fontSize: 14, maxWidth: 420, lineHeight: 1.9 }}>
            面向银行级 Agent 研发场景：项目空间、资产中心、发布门禁、服务治理与全链路观测，一个平台完成从开发到上线的闭环。
          </Typography.Paragraph>
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: 10, marginTop: 36 }}>
            {CAPABILITIES.map((c) => (
              <span
                key={c}
                style={{
                  padding: '6px 14px',
                  borderRadius: 999,
                  border: '1px solid rgba(255,255,255,0.22)',
                  background: 'rgba(255,255,255,0.08)',
                  fontSize: 12.5,
                  color: 'rgba(255,255,255,0.85)',
                  backdropFilter: 'blur(4px)',
                }}
              >
                {c}
              </span>
            ))}
          </div>
        </div>
      </div>

      <div className="login-form-side">
        <div style={{ width: 380, maxWidth: '100%' }}>
          <Typography.Title level={3} style={{ marginBottom: 4, fontWeight: 650 }}>
            欢迎回来
          </Typography.Title>
          <Typography.Paragraph type="secondary" style={{ marginBottom: 28 }}>
            本地开发认证：token 即 userId（如 u1001）
          </Typography.Paragraph>
          <Form form={form} layout="vertical" onFinish={(v) => doLogin(v.token)} size="large">
            <Form.Item name="token" rules={[{ required: true, message: '请输入 token' }]}>
              <Input
                placeholder="输入用户 ID，例如 u1001"
                onPressEnter={() => form.submit()}
                style={{ height: 44 }}
                autoFocus
              />
            </Form.Item>
            <Button
              type="primary"
              htmlType="submit"
              block
              loading={loading}
              style={{ height: 44, fontSize: 15 }}
              icon={<ArrowRightOutlined />}
              iconPosition="end"
            >
              进入控制台
            </Button>
          </Form>
          <Divider plain style={{ color: colors.textTertiary, fontSize: 12, margin: '28px 0 16px' }}>
            快捷选择种子用户
          </Divider>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 10 }}>
            {SEED_USERS.map((u) => (
              <Button
                key={u.id}
                loading={loading}
                onClick={() => doLogin(u.id)}
                style={{ height: 38, borderRadius: 8 }}
              >
                {u.id} {u.name}
              </Button>
            ))}
          </div>
          <Typography.Paragraph type="secondary" style={{ fontSize: 12, marginTop: 32, textAlign: 'center' }}>
            后端种子用户见 application.yml · agentops.auth.users
          </Typography.Paragraph>
        </div>
      </div>
    </div>
  );
}
