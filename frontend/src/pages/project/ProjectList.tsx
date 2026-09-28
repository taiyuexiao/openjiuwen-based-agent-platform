import { useCallback, useEffect, useState } from 'react';
import { Button, Card, Col, Form, Input, Modal, Popconfirm, Row, Tag, Typography, message } from 'antd';
import { PlusOutlined, ProjectOutlined, ReloadOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { api, getToken } from '../../api/client';
import EmptyState from '../../components/EmptyState';
import PageHeader from '../../components/PageHeader';
import SquareCard from '../../components/SquareCard';
import StatusBadge from '../../components/StatusBadge';
import type { Project } from '../../types';
import { fmtTime } from '../../utils/format';

/** 项目空间：卡片化项目列表 + 创建 */
export default function ProjectList() {
  const [data, setData] = useState<Project[]>([]);
  const [loading, setLoading] = useState(false);
  const [createOpen, setCreateOpen] = useState(false);
  const [form] = Form.useForm<{ code: string; name: string; description?: string }>();
  const navigate = useNavigate();
  const token = getToken();

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setData(await api<Project[]>('/v1/api/projects/list'));
    } catch {
      // helper 已提示
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const create = async () => {
    const values = await form.validateFields();
    await api<Project>('/v1/api/projects/create', values);
    message.success('项目创建成功');
    setCreateOpen(false);
    form.resetFields();
    load();
  };

  const archive = async (id: number) => {
    await api<Project>('/v1/api/projects/archive', { id });
    message.success('已归档');
    load();
  };

  return (
    <>
      <PageHeader
        title="项目空间"
        subTitle="我参与的全部项目；点击卡片进入项目详情（成员 / 环境配额 / 模型授权 / 知识库授权）"
        extra={
          <>
            <Button icon={<ReloadOutlined />} onClick={load}>
              刷新
            </Button>
            <Button type="primary" icon={<PlusOutlined />} onClick={() => setCreateOpen(true)}>
              创建项目
            </Button>
          </>
        }
      />
      {data.length === 0 && !loading ? (
        <Card className="soft-card">
          <EmptyState description="暂无项目，创建第一个项目开始 Agent 研发" actionText="创建项目" onAction={() => setCreateOpen(true)} />
        </Card>
      ) : (
        <Row gutter={[16, 16]}>
          {data.map((p) => (
            <Col xs={24} sm={12} lg={8} xl={6} key={p.id}>
              <SquareCard
                loading={loading}
                banner="brand"
                icon={<ProjectOutlined />}
                title={p.name}
                code={p.code}
                corner={<StatusBadge value={p.status} />}
                description={p.description}
                meta={
                  <>
                    <Tag color={p.ownerId === token ? 'gold' : 'blue'} style={{ marginRight: 0 }}>
                      {p.ownerId === token ? 'OWNER' : 'MEMBER'}
                    </Tag>
                    <span>{fmtTime(p.createdAt).slice(0, 10)}</span>
                  </>
                }
                actions={
                  p.status === 'ACTIVE' ? (
                    <Popconfirm title="确认归档该项目？" onConfirm={() => archive(p.id)}>
                      <Typography.Link type="danger" style={{ fontSize: 12 }}>
                        归档
                      </Typography.Link>
                    </Popconfirm>
                  ) : (
                    <Button size="small" type="link" onClick={() => navigate(`/projects/${p.id}`)}>
                      进入 →
                    </Button>
                  )
                }
                onClick={() => navigate(`/projects/${p.id}`)}
              />
            </Col>
          ))}
        </Row>
      )}
      <Modal title="创建项目" open={createOpen} onOk={create} onCancel={() => setCreateOpen(false)} destroyOnClose>
        <Form form={form} layout="vertical" preserve={false}>
          <Form.Item
            name="code"
            label="项目编码"
            rules={[
              { required: true, message: '请输入项目编码' },
              { pattern: /^[A-Za-z0-9-]+$/, message: '仅允许字母、数字、中划线' },
              { max: 64 },
            ]}
          >
            <Input placeholder="如 credit-agent" />
          </Form.Item>
          <Form.Item name="name" label="项目名称" rules={[{ required: true }, { max: 128 }]}>
            <Input />
          </Form.Item>
          <Form.Item name="description" label="描述" rules={[{ max: 1024 }]}>
            <Input.TextArea rows={3} />
          </Form.Item>
        </Form>
      </Modal>
    </>
  );
}
