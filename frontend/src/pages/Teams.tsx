import { useCallback, useEffect, useState } from 'react';
import { colors } from '../theme';
import {
  Button,
  Card,
  Col,
  Empty,
  Form,
  Input,
  Modal,
  Popconfirm,
  Row,
  Space,
  Table,
  Tag,
  Typography,
  message,
} from 'antd';
import { PlusOutlined, ReloadOutlined, TeamOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import { api } from '../api/client';
import EmptyState from '../components/EmptyState';
import PageHeader from '../components/PageHeader';
import type { Project, ProjectMember, UserGroup, UserGroupMember } from '../types';
import { fmtTime } from '../utils/format';

interface GroupProjectGrant {
  project: Project;
  role: string;
}

/** 团队空间：用户组 CRUD + 组成员管理 + 组被授权到项目的视图 */
export default function Teams() {
  const [groups, setGroups] = useState<UserGroup[]>([]);
  const [loading, setLoading] = useState(false);
  const [selected, setSelected] = useState<UserGroup | null>(null);
  const [members, setMembers] = useState<UserGroupMember[]>([]);
  const [grants, setGrants] = useState<GroupProjectGrant[]>([]);
  const [detailLoading, setDetailLoading] = useState(false);
  const [createOpen, setCreateOpen] = useState(false);
  const [addMemberOpen, setAddMemberOpen] = useState(false);
  const [createForm] = Form.useForm<{ name: string; description?: string }>();
  const [memberForm] = Form.useForm<{ userId: string }>();

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setGroups(await api<UserGroup[]>('/v1/api/user-groups/list'));
    } catch {
      // helper 已提示
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const loadDetail = useCallback(async (g: UserGroup) => {
    setDetailLoading(true);
    try {
      const ms = await api<UserGroupMember[]>('/v1/api/user-groups/members/list', { id: g.id });
      setMembers(ms);
      // 组被授权到哪些项目：遍历我的项目成员表，找 subjectType=GROUP 且 subjectId=组 ID
      const projects = await api<Project[]>('/v1/api/projects/list');
      const found: GroupProjectGrant[] = [];
      await Promise.all(
        projects.map(async (p) => {
          try {
            const pms = await api<ProjectMember[]>(`/v1/api/projects/${p.id}/members/list`);
            pms
              .filter((m) => m.subjectType === 'GROUP' && m.subjectId === String(g.id))
              .forEach((m) => found.push({ project: p, role: m.role }));
          } catch {
            // 无权限项目跳过
          }
        }),
      );
      setGrants(found);
    } catch {
      // helper 已提示
    } finally {
      setDetailLoading(false);
    }
  }, []);

  const select = (g: UserGroup) => {
    setSelected(g);
    loadDetail(g);
  };

  const create = async () => {
    const values = await createForm.validateFields();
    await api<UserGroup>('/v1/api/user-groups/create', values);
    message.success('用户组创建成功');
    setCreateOpen(false);
    createForm.resetFields();
    load();
  };

  const removeGroup = async (id: number) => {
    await api('/v1/api/user-groups/delete', { id });
    message.success('已删除');
    if (selected?.id === id) {
      setSelected(null);
      setMembers([]);
      setGrants([]);
    }
    load();
  };

  const addMember = async () => {
    if (!selected) return;
    const values = await memberForm.validateFields();
    await api('/v1/api/user-groups/members/add', { groupId: selected.id, userId: values.userId });
    message.success('成员已加入');
    setAddMemberOpen(false);
    memberForm.resetFields();
    loadDetail(selected);
  };

  const removeMember = async (userId: string) => {
    if (!selected) return;
    await api('/v1/api/user-groups/members/remove', { groupId: selected.id, userId });
    message.success('已移除');
    loadDetail(selected);
  };

  const memberColumns: ColumnsType<UserGroupMember> = [
    { title: '用户 ID', dataIndex: 'userId' },
    { title: '加入时间', dataIndex: 'createdAt', width: 180, render: fmtTime },
    {
      title: '操作',
      width: 90,
      render: (_, r) => (
        <Popconfirm title={`确认将 ${r.userId} 移出该组？`} onConfirm={() => removeMember(r.userId)}>
          <Typography.Link type="danger">移除</Typography.Link>
        </Popconfirm>
      ),
    },
  ];

  const grantColumns: ColumnsType<GroupProjectGrant> = [
    {
      title: '项目',
      render: (_, r) => (
        <Typography.Link href={`/projects/${r.project.id}`}>
          {r.project.code}（{r.project.name}）
        </Typography.Link>
      ),
    },
    {
      title: '被授予角色',
      dataIndex: 'role',
      width: 140,
      render: (role: string) => <Tag color={role === 'OWNER' ? 'gold' : role === 'ADMIN' ? 'red' : 'blue'}>{role}</Tag>,
    },
  ];

  return (
    <>
      <PageHeader
        title="团队空间"
        subTitle="用户组管理：组可作为整体被授权到项目（项目详情 - 成员管理 - 添加成员选 GROUP）"
        extra={
          <>
            <Button icon={<ReloadOutlined />} onClick={load}>
              刷新
            </Button>
            <Button type="primary" icon={<PlusOutlined />} onClick={() => setCreateOpen(true)}>
              创建用户组
            </Button>
          </>
        }
      />
      <Row gutter={16}>
        <Col xs={24} lg={9}>
          <Card title="用户组" className="soft-card" loading={loading}>
            {groups.length === 0 ? (
              <EmptyState small description="暂无用户组，点击右上角创建" />
            ) : (
              <Space direction="vertical" style={{ width: '100%' }}>
                {groups.map((g) => (
                  <Card
                    key={g.id}
                    size="small"
                    hoverable
                    onClick={() => select(g)}
                    style={{
                      borderColor: selected?.id === g.id ? colors.brand : undefined,
                      background: selected?.id === g.id ? colors.bgHover : undefined,
                    }}
                  >
                    <Space style={{ justifyContent: 'space-between', width: '100%' }}>
                      <Space>
                        <TeamOutlined style={{ color: colors.brand }} />
                        <Typography.Text strong>{g.name}</Typography.Text>
                        <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                          组 ID：{g.id}
                        </Typography.Text>
                      </Space>
                      <Popconfirm title="确认删除该用户组？" onConfirm={() => removeGroup(g.id)}>
                        <Typography.Link type="danger" onClick={(e) => e.stopPropagation()}>
                          删除
                        </Typography.Link>
                      </Popconfirm>
                    </Space>
                    {g.description && (
                      <Typography.Paragraph type="secondary" style={{ fontSize: 12, margin: '8px 0 0' }} ellipsis>
                        {g.description}
                      </Typography.Paragraph>
                    )}
                  </Card>
                ))}
              </Space>
            )}
          </Card>
        </Col>
        <Col xs={24} lg={15}>
          {selected ? (
            <Space direction="vertical" size={16} style={{ width: '100%' }}>
              <Card
                title={`组成员（${selected.name}）`}
                className="soft-card"
                loading={detailLoading}
                extra={
                  <Button type="primary" size="small" icon={<PlusOutlined />} onClick={() => setAddMemberOpen(true)}>
                    添加成员
                  </Button>
                }
              >
                <Table rowKey="id" size="small" columns={memberColumns} dataSource={members} pagination={false} />
              </Card>
              <Card title="该组被授权到的项目" className="soft-card" loading={detailLoading}>
                <Table rowKey={(r) => r.project.id} size="small" columns={grantColumns} dataSource={grants} pagination={false} />
              </Card>
            </Space>
          ) : (
            <Card className="soft-card">
              <EmptyState small description="选择左侧用户组查看成员与项目授权情况" />
            </Card>
          )}
        </Col>
      </Row>

      <Modal title="创建用户组" open={createOpen} onOk={create} onCancel={() => setCreateOpen(false)} destroyOnClose>
        <Form form={createForm} layout="vertical" preserve={false}>
          <Form.Item name="name" label="组名称" rules={[{ required: true }, { max: 128 }]}>
            <Input placeholder="如 信贷研发组" />
          </Form.Item>
          <Form.Item name="description" label="描述" rules={[{ max: 1024 }]}>
            <Input.TextArea rows={3} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title={`添加成员到「${selected?.name ?? ''}」`}
        open={addMemberOpen}
        onOk={addMember}
        onCancel={() => setAddMemberOpen(false)}
        destroyOnClose
      >
        <Form form={memberForm} layout="vertical" preserve={false}>
          <Form.Item name="userId" label="用户 ID" rules={[{ required: true, message: '请输入用户 ID' }]}>
            <Input placeholder="如 u1003" />
          </Form.Item>
        </Form>
      </Modal>
    </>
  );
}
