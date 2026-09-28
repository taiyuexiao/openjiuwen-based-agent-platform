import { useCallback, useEffect, useMemo, useState } from 'react';
import { Alert, Button, Card, Checkbox, Space, Table, Tag, Typography, message } from 'antd';
import { ReloadOutlined, SafetyOutlined, SaveOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import { api, getToken } from '../../api/client';
import PageHeader from '../../components/PageHeader';
import type { PermissionMatrixResp, Role } from '../../types';

/** 与后端 application.yml agentops.auth.admins 对应（平台管理员清单） */
const PLATFORM_ADMINS = ['u1001'];

const ROLE_LABELS: Record<string, string> = {
  OWNER: '所有者',
  ADMIN: '管理员',
  DEVELOPER: '开发者',
  OPERATOR: '运维',
};

interface RowModel {
  role: Role;
  granted: Set<string>;
  dirty: boolean;
}

/** 权限设置：角色 × 权限点 Checkbox 矩阵；仅平台管理员（u1001）可改 */
export default function Permissions() {
  const token = getToken() ?? '';
  const isAdmin = PLATFORM_ADMINS.includes(token);
  const [permissions, setPermissions] = useState<string[]>([]);
  const [rows, setRows] = useState<RowModel[]>([]);
  const [loading, setLoading] = useState(false);
  const [savingRole, setSavingRole] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const m = await api<PermissionMatrixResp>('/v1/api/permissions/matrix');
      setPermissions(m.permissions);
      setRows(m.roles.map((r) => ({ role: r.role, granted: new Set(r.permissions), dirty: false })));
    } catch {
      // helper 已提示
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  /** 权限点按模块前缀分组排序，列更可读 */
  const sortedPermissions = useMemo(
    () => [...permissions].sort((a, b) => a.localeCompare(b)),
    [permissions],
  );

  const toggle = (role: Role, perm: string, checked: boolean) => {
    if (!isAdmin) return;
    setRows((prev) =>
      prev.map((r) => {
        if (r.role !== role) return r;
        const granted = new Set(r.granted);
        if (checked) granted.add(perm);
        else granted.delete(perm);
        return { ...r, granted, dirty: true };
      }),
    );
  };

  const save = async (row: RowModel) => {
    setSavingRole(row.role);
    try {
      await api('/v1/api/permissions/roles/update', { role: row.role, permissions: [...row.granted].sort() });
      message.success(`角色 ${row.role} 权限已保存`);
      setRows((prev) => prev.map((r) => (r.role === row.role ? { ...r, dirty: false } : r)));
    } finally {
      setSavingRole(null);
    }
  };

  const columns: ColumnsType<RowModel> = [
    {
      title: '角色',
      dataIndex: 'role',
      fixed: 'left',
      width: 150,
      render: (role: Role) => (
        <Space direction="vertical" size={2}>
          <Tag color={role === 'OWNER' ? 'gold' : role === 'ADMIN' ? 'red' : role === 'DEVELOPER' ? 'blue' : 'green'}>
            {role}
          </Tag>
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            {ROLE_LABELS[role] ?? ''}
          </Typography.Text>
        </Space>
      ),
    },
    ...sortedPermissions.map((perm) => ({
      title: <Typography.Text style={{ fontSize: 12 }}>{perm}</Typography.Text>,
      dataIndex: perm,
      width: 110,
      align: 'center' as const,
      render: (_: unknown, r: RowModel) => (
        <Checkbox
          checked={r.granted.has(perm)}
          disabled={!isAdmin}
          onChange={(e) => toggle(r.role, perm, e.target.checked)}
        />
      ),
    })),
    {
      title: '操作',
      fixed: 'right' as const,
      width: 110,
      render: (_: unknown, r: RowModel) => (
        <Button
          size="small"
          type="primary"
          icon={<SaveOutlined />}
          disabled={!isAdmin || !r.dirty}
          loading={savingRole === r.role}
          onClick={() => save(r)}
        >
          保存
        </Button>
      ),
    },
  ];

  return (
    <>
      <PageHeader
        title="权限设置"
        subTitle="项目内角色 × 权限点矩阵；修改立即生效于所有项目"
        extra={
          <Button icon={<ReloadOutlined />} onClick={load}>
            刷新
          </Button>
        }
      />
      <Alert
        style={{ marginBottom: 16 }}
        type={isAdmin ? 'success' : 'warning'}
        showIcon
        icon={<SafetyOutlined />}
        message={
          isAdmin
            ? `当前用户 ${token} 是平台管理员，可勾选并保存权限矩阵`
            : `当前用户 ${token || '（未登录）'} 不是平台管理员（admins: ${PLATFORM_ADMINS.join('、')}），矩阵为只读`
        }
      />
      <Card className="soft-card" loading={loading}>
        <Table
          rowKey="role"
          columns={columns}
          dataSource={rows}
          pagination={false}
          scroll={{ x: 'max-content' }}
          size="small"
          bordered
        />
      </Card>
    </>
  );
}
