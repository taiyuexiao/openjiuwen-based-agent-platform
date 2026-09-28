import { useState } from 'react';
import { Navigate, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { Avatar, Breadcrumb, Dropdown, Layout, Menu, Space, Typography } from 'antd';
import {
  AppstoreOutlined,
  ClusterOutlined,
  CompassOutlined,
  DashboardOutlined,
  DatabaseOutlined,
  LeftOutlined,
  LogoutOutlined,
  MonitorOutlined,
  ProjectOutlined,
  RightOutlined,
  RocketOutlined,
  RobotOutlined,
  SafetyOutlined,
  TeamOutlined,
  ToolOutlined,
} from '@ant-design/icons';
import { clearToken, getToken } from '../api/client';
import ProjectSelect from '../components/ProjectSelect';
import { SIDER_GRADIENT } from '../theme';

const { Header, Sider, Content } = Layout;

/** 种子用户（与后端 application.yml agentops.auth.users 对应），仅用于顶栏展示 */
const SEED_USERS: Record<string, string> = {
  u1001: '张三',
  u1002: '李四',
  u1003: '王五',
  u1004: '赵六',
  u1005: '钱七',
  u1006: '孙八',
};

const MENU_ITEMS = [
  { type: 'group' as const, label: '概 览', children: [{ key: '/', icon: <DashboardOutlined />, label: '工作台' }] },
  {
    type: 'group' as const,
    label: '空 间',
    children: [
      { key: '/projects', icon: <ProjectOutlined />, label: '项目空间' },
      { key: '/teams', icon: <TeamOutlined />, label: '团队空间' },
    ],
  },
  {
    type: 'group' as const,
    label: '发 现',
    children: [
      {
        key: 'square',
        icon: <CompassOutlined />,
        label: '广场',
        children: [
          { key: '/square/agents', label: 'Agent 广场' },
          { key: '/square/skills', label: 'Skill 广场' },
        ],
      },
    ],
  },
  {
    type: 'group' as const,
    label: '研 发',
    children: [
      {
        key: 'assets',
        icon: <AppstoreOutlined />,
        label: '资产中心',
        children: [
          { key: '/assets', label: '资产列表' },
          { key: '/assets/upload', label: 'Skill 上传' },
          { key: '/assets/versions', label: '版本管理' },
          { key: '/assets/http-api', label: 'HTTP API 注册' },
        ],
      },
      { key: '/agents', icon: <RobotOutlined />, label: 'Agent 管理' },
      {
        key: 'catalog',
        icon: <DatabaseOutlined />,
        label: '平台目录',
        children: [
          { key: '/catalog/providers', label: '模型 Provider' },
          { key: '/catalog/services', label: '模型服务' },
          { key: '/catalog/kbs', label: '知识库' },
        ],
      },
    ],
  },
  {
    type: 'group' as const,
    label: '运 营',
    children: [
      { key: '/observability', icon: <MonitorOutlined />, label: '可观测' },
      {
        key: 'delivery',
        icon: <RocketOutlined />,
        label: '交付发布',
        children: [
          { key: '/delivery/artifacts', label: '制品' },
          { key: '/delivery/targets', label: '部署目标' },
          { key: '/delivery/levels', label: 'Agent 分级' },
          { key: '/delivery/releases', label: '发布单' },
        ],
      },
      {
        key: 'gov',
        icon: <ClusterOutlined />,
        label: '服务治理',
        children: [
          { key: '/gov/routes', label: '路由' },
          { key: '/gov/caller-policies', label: '调用方策略' },
          { key: '/gov/mcp-policies', label: 'MCP 策略' },
          { key: '/gov/directory', label: '服务目录' },
        ],
      },
      { key: '/ops/components', icon: <ToolOutlined />, label: '组件运维' },
      { key: '/settings/permissions', icon: <SafetyOutlined />, label: '权限设置' },
    ],
  },
];

/** 路由 → 面包屑 */
const CRUMB_MAP: [RegExp, string[]][] = [
  [/^\/$/, ['工作台']],
  [/^\/projects\/\d+/, ['项目空间', '项目详情']],
  [/^\/projects/, ['项目空间']],
  [/^\/teams/, ['团队空间']],
  [/^\/square\/agents/, ['广场', 'Agent 广场']],
  [/^\/square\/skills/, ['广场', 'Skill 广场']],
  [/^\/assets\/upload/, ['资产中心', 'Skill 上传']],
  [/^\/assets\/versions/, ['资产中心', '版本管理']],
  [/^\/assets\/http-api/, ['资产中心', 'HTTP API 注册']],
  [/^\/assets\/\d+/, ['资产中心', '资产详情']],
  [/^\/assets/, ['资产中心', '资产列表']],
  [/^\/agents\/new/, ['Agent 管理', '创建向导']],
  [/^\/agents\/\d+/, ['Agent 管理', 'Agent 详情']],
  [/^\/agents/, ['Agent 管理']],
  [/^\/observability/, ['可观测']],
  [/^\/delivery\/artifacts/, ['交付发布', '制品']],
  [/^\/delivery\/targets/, ['交付发布', '部署目标']],
  [/^\/delivery\/levels/, ['交付发布', 'Agent 分级']],
  [/^\/delivery\/releases/, ['交付发布', '发布单']],
  [/^\/releases\/\d+/, ['交付发布', '发布单', '发布详情']],
  [/^\/gov\/routes/, ['服务治理', '路由']],
  [/^\/gov\/caller-policies/, ['服务治理', '调用方策略']],
  [/^\/gov\/mcp-policies/, ['服务治理', 'MCP 策略']],
  [/^\/gov\/directory/, ['服务治理', '服务目录']],
  [/^\/ops\/components/, ['组件运维']],
  [/^\/settings\/permissions/, ['权限设置']],
  [/^\/catalog\/providers/, ['平台目录', '模型 Provider']],
  [/^\/catalog\/services/, ['平台目录', '模型服务']],
  [/^\/catalog\/kbs/, ['平台目录', '知识库']],
];

function breadcrumbsOf(pathname: string): string[] {
  const hit = CRUMB_MAP.find(([re]) => re.test(pathname));
  return hit ? hit[1] : ['工作台'];
}

/** 菜单选中态（详情页高亮所属列表） */
function selectedKey(pathname: string): string {
  if (pathname === '/') return '/';
  if (pathname.startsWith('/projects')) return '/projects';
  if (pathname.startsWith('/assets/http-api')) return '/assets/http-api';
  if (pathname.startsWith('/assets/upload')) return '/assets/upload';
  if (pathname.startsWith('/assets/versions')) return '/assets/versions';
  if (pathname.startsWith('/assets')) return '/assets';
  if (pathname.startsWith('/agents')) return '/agents';
  if (pathname.startsWith('/releases')) return '/delivery/releases';
  if (pathname.startsWith('/observability')) return '/observability';
  const all = MENU_ITEMS.flatMap((g) => g.children ?? [])
    .flatMap((m) => ('children' in m && m.children ? m.children : [m]))
    .map((m) => m.key);
  return all.filter((k) => pathname.startsWith(k)).sort((a, b) => b.length - a.length)[0] ?? '/';
}

/** 头像底色：按 userId 稳定取色 */
const AVATAR_COLORS = ['#2B5AED', '#7C5CFC', '#12A5A0', '#EE8112', '#E5484D', '#5B45D6'];
function avatarColor(token: string): string {
  let h = 0;
  for (const ch of token) h = (h * 31 + ch.charCodeAt(0)) % 997;
  return AVATAR_COLORS[h % AVATAR_COLORS.length];
}

export default function MainLayout() {
  const token = getToken();
  const location = useLocation();
  const navigate = useNavigate();
  const [collapsed, setCollapsed] = useState(false);

  if (!token) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  }

  const logout = () => {
    clearToken();
    navigate('/login', { replace: true });
  };

  const crumbs = breadcrumbsOf(location.pathname);
  const displayName = SEED_USERS[token] ?? token;

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Sider
        theme="dark"
        width={224}
        collapsedWidth={64}
        collapsed={collapsed}
        trigger={null}
        style={{ background: SIDER_GRADIENT, position: 'relative' }}
      >
        <div className="sider-logo">
          <svg width="30" height="30" viewBox="0 0 40 40" style={{ flexShrink: 0 }}>
            <rect x="2" y="2" width="36" height="36" rx="10" fill="rgba(255,255,255,0.14)" />
            <path d="M12 26 L20 12 L28 26" stroke="#fff" strokeWidth="2.5" fill="none" strokeLinecap="round" strokeLinejoin="round" />
            <circle cx="20" cy="22" r="2.6" fill="#9DB4FF" />
          </svg>
          {!collapsed && (
            <div>
              <div className="logo-name">高码智能体平台</div>
              <div className="logo-sub">AGENTOPS</div>
            </div>
          )}
        </div>
        <div
          className="sider-collapse-btn"
          onClick={() => setCollapsed(!collapsed)}
          title={collapsed ? '展开菜单' : '收起菜单'}
        >
          {collapsed ? <RightOutlined style={{ fontSize: 11 }} /> : <LeftOutlined style={{ fontSize: 11 }} />}
        </div>
        <Menu
          theme="dark"
          mode="inline"
          selectedKeys={[selectedKey(location.pathname)]}
          defaultOpenKeys={['square', 'assets', 'delivery', 'gov', 'catalog']}
          items={MENU_ITEMS}
          onClick={({ key }) => navigate(key)}
          style={{ background: 'transparent', padding: '4px 0 16px' }}
        />
      </Sider>
      <Layout>
        <Header className="app-header" style={{ padding: '0 24px', display: 'flex', justifyContent: 'space-between', alignItems: 'center', height: 56, lineHeight: 'normal' }}>
          <Breadcrumb
            items={crumbs.map((c, i) => ({
              title: (
                <span style={{ fontSize: 13, color: i === crumbs.length - 1 ? 'var(--text-primary)' : 'var(--text-tertiary)', fontWeight: i === crumbs.length - 1 ? 550 : 400 }}>
                  {c}
                </span>
              ),
            }))}
          />
          <Space size="large">
            <Space size={8}>
              <Typography.Text type="secondary" style={{ fontSize: 13 }}>
                当前项目
              </Typography.Text>
              <ProjectSelect style={{ minWidth: 200 }} />
            </Space>
            <Dropdown
              menu={{
                items: [{ key: 'logout', icon: <LogoutOutlined />, label: '退出登录', onClick: logout }],
              }}
            >
              <Space size={8} style={{ cursor: 'pointer', padding: '4px 6px', borderRadius: 8 }}>
                <Avatar size={28} style={{ background: avatarColor(token), fontSize: 13, fontWeight: 600 }}>
                  {displayName.slice(0, 1)}
                </Avatar>
                <span style={{ fontSize: 13 }}>
                  {token}（{displayName}）
                </span>
              </Space>
            </Dropdown>
          </Space>
        </Header>
        <Content style={{ padding: '20px 24px 32px', background: 'var(--bg-layout)' }}>
          <div className="page-fade" key={location.pathname}>
            <Outlet />
          </div>
        </Content>
      </Layout>
    </Layout>
  );
}
