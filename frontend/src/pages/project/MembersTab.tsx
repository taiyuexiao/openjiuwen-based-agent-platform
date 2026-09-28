import { useCallback, useEffect, useState } from 'react';
import { Button, Form, Input, Modal, Popconfirm, Select, Space, Table, Tag, Typography, message } from 'antd';
import { PlusOutlined, ReloadOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import { api } from '../../api/client';
import type { ProjectMember, Role, SubjectType } from '../../types';
import { fmtTime } from '../../utils/format';

const ROLE_COLORS: Record<string, string> = {
  OWNER: 'gold',
  ADMIN: 'red',
  DEVELOPER: 'blue',
  OPERATOR: 'green',
};

/** 项目成员管理：加人/加组、移除、改角色 */
export default function MembersTab({ projectId }: { projectId: number }) {
  const [data, setData] = useState<ProjectMember[]>([]);
  const [loading, setLoading] = useState(false);
  const [addOpen, setAddOpen] = useState(false);
  const [roleTarget, setRoleTarget] = useState<ProjectMember | null>(null);
  const [addForm] = Form.useForm<{ subjectType: SubjectType; subjectId: string; role: Role }>();
  const [roleForm] = Form.useForm<{ role: Role }>();

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setData(await api<ProjectMember[]>(`/v1/api/projects/${projectId}/members/list`));
    } catch {
      // helper 已提示
    } finally {
      setLoading(false);
    }
  }, [projectId]);

  useEffect(() => {
    load();
  }, [load]);

  const add = async () => {
    const values = await addForm.validateFields();
    await api(`/v1/api/projects/${projectId}/members/add`, values);
    message.success('成员已添加');
    setAddOpen(false);
    addForm.resetFields();
    load();
  };

  const remove = async (r: ProjectMember) => {
    await api(`/v1/api/projects/${projectId}/members/remove`, {
      subjectType: r.subjectType,
      subjectId: r.subjectId,
    });
    message.success('已移除');
    load();
  };

  const changeRole = async () => {
    if (!roleTarget) return;
    const values = await roleForm.validateFields();
    await api(`/v1/api/projects/${projectId}/members/change-role`, {
      subjectType: roleTarget.subjectType,
      subjectId: roleTarget.subjectId,
      role: values.role,
    });
    message.success('角色已变更');
    setRoleTarget(null);
    load();
  };

  const columns: ColumnsType<ProjectMember> = [
    { title: 'ID', dataIndex: 'id', width: 70 },
    {
      title: '类型',
      dataIndex: 'subjectType',
      width: 90,
      render: (t: string) => <Tag color={t === 'USER' ? 'blue' : 'purple'}>{t === 'USER' ? '用户' : '用户组'}</Tag>,
    },
    { title: '主体 ID', dataIndex: 'subjectId' },
    {
      title: '角色',
      dataIndex: 'role',
      width: 120,
      render: (r: string) => <Tag color={ROLE_COLORS[r]}>{r}</Tag>,
    },
    { title: '添加人', dataIndex: 'createdBy', width: 100 },
    { title: '添加时间', dataIndex: 'createdAt', width: 170, render: fmtTime },
    {
      title: '操作',
      width: 160,
      render: (_, r) => (
        <Space>
          <Typography.Link
            onClick={() => {
              setRoleTarget(r);
              roleForm.setFieldsValue({ role: r.role });
            }}
          >
            改角色
          </Typography.Link>
          <Popconfirm title={`确认移除 ${r.subjectType} ${r.subjectId}？`} onConfirm={() => remove(r)}>
            <Typography.Link type="danger">移除</Typography.Link>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <>
      <Space style={{ marginBottom: 16 }}>
        <Button type="primary" icon={<PlusOutlined />} onClick={() => setAddOpen(true)}>
          添加成员
        </Button>
        <Button icon={<ReloadOutlined />} onClick={load}>
          刷新
        </Button>
      </Space>
      <Table rowKey="id" loading={loading} columns={columns} dataSource={data} pagination={false} />

      <Modal title="添加成员" open={addOpen} onOk={add} onCancel={() => setAddOpen(false)} destroyOnClose>
        <Form form={addForm} layout="vertical" preserve={false} initialValues={{ subjectType: 'USER', role: 'DEVELOPER' }}>
          <Form.Item name="subjectType" label="主体类型" rules={[{ required: true }]}>
            <Select
              options={[
                { value: 'USER', label: '用户' },
                { value: 'GROUP', label: '用户组' },
              ]}
            />
          </Form.Item>
          <Form.Item name="subjectId" label="主体 ID（userId 或组 ID）" rules={[{ required: true, message: '请输入主体 ID' }]}>
            <Input placeholder="如 u1002" />
          </Form.Item>
          <Form.Item name="role" label="角色" rules={[{ required: true }]}>
            <Select
              options={[
                { value: 'OWNER', label: 'OWNER - 所有者' },
                { value: 'ADMIN', label: 'ADMIN - 管理员' },
                { value: 'DEVELOPER', label: 'DEVELOPER - 开发者' },
                { value: 'OPERATOR', label: 'OPERATOR - 运维' },
              ]}
            />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title={`修改角色：${roleTarget?.subjectId ?? ''}`}
        open={roleTarget !== null}
        onOk={changeRole}
        onCancel={() => setRoleTarget(null)}
        destroyOnClose
      >
        <Form form={roleForm} layout="vertical" preserve={false}>
          <Form.Item name="role" label="新角色" rules={[{ required: true }]}>
            <Select
              options={[
                { value: 'OWNER', label: 'OWNER' },
                { value: 'ADMIN', label: 'ADMIN' },
                { value: 'DEVELOPER', label: 'DEVELOPER' },
                { value: 'OPERATOR', label: 'OPERATOR' },
              ]}
            />
          </Form.Item>
        </Form>
      </Modal>
    </>
  );
}
