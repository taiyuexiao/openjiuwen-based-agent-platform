import { useState } from 'react';
import { colors } from '../../theme';
import { Alert, Button, Card, Col, Descriptions, Form, Input, Row, Space, Steps, Typography, message } from 'antd';
import { ArrowLeftOutlined, CheckCircleOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { api } from '../../api/client';
import PageHeader from '../../components/PageHeader';
import ProjectSelect from '../../components/ProjectSelect';
import { useProject } from '../../context/ProjectContext';
import type { AccessMode, Agent } from '../../types';

const MODE_DESC: { value: AccessMode; title: string; desc: string }[] = [
  {
    value: 'NATIVE',
    title: 'NATIVE · 平台原生研发',
    desc: '基于平台脚手架研发，版本登记时需提供完整声明（模型/技能/工具/知识库），可观测与治理能力最全。',
  },
  {
    value: 'ADAPTED',
    title: 'ADAPTED · 适配已有 Agent',
    desc: '已有 Agent 工程适配接入平台声明与治理能力，保留原有运行栈。',
  },
  {
    value: 'HOSTED',
    title: 'HOSTED · 外部托管接入',
    desc: 'Agent 运行在外部环境，平台只做登记与治理；必须提供 Runtime / Health 端点供健康检查。',
  },
];

interface WizardValues {
  code: string;
  name: string;
  description?: string;
  accessMode: AccessMode;
  runtimeEndpoint?: string;
  healthEndpoint?: string;
}

/** Agent 创建向导：基本信息 → 接入方式 → 确认提交 */
export default function AgentWizard() {
  const { projectId } = useProject();
  const navigate = useNavigate();
  const [step, setStep] = useState(0);
  const [submitting, setSubmitting] = useState(false);
  const [form] = Form.useForm<WizardValues>();
  const values = Form.useWatch([], form) ?? ({} as Partial<WizardValues>);

  const next = async () => {
    if (step === 0) {
      await form.validateFields(['code', 'name', 'description']);
    } else if (step === 1) {
      await form.validateFields(
        values.accessMode === 'HOSTED' ? ['accessMode', 'runtimeEndpoint', 'healthEndpoint'] : ['accessMode'],
      );
    }
    setStep(step + 1);
  };

  const submit = async () => {
    if (!projectId) {
      message.warning('请先在顶栏选择项目');
      return;
    }
    setSubmitting(true);
    try {
      const agent = await api<Agent>('/v1/api/agents/create', {
        code: values.code,
        name: values.name,
        projectId,
        accessMode: values.accessMode,
        description: values.description?.trim() || undefined,
        runtimeEndpoint: values.runtimeEndpoint?.trim() || undefined,
        healthEndpoint: values.healthEndpoint?.trim() || undefined,
      });
      message.success('Agent 创建成功');
      navigate(`/agents/${agent.id}?projectId=${projectId}`);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <>
      <PageHeader
        title="新建 Agent"
        subTitle="三步完成创建：基本信息 → 接入方式 → 确认提交"
        extra={
          <Space>
            <ProjectSelect />
            <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/agents')}>
              返回列表
            </Button>
          </Space>
        }
      />
      {!projectId && (
        <Alert style={{ marginBottom: 16 }} type="warning" showIcon message="请先在顶栏选择项目，Agent 将归属到该项目" />
      )}
      <Row justify="center">
        <Col xs={24} lg={16} xl={14}>
          <Card className="soft-card">
            <Steps
              current={step}
              items={[{ title: '基本信息' }, { title: '接入方式' }, { title: '确认提交' }]}
              style={{ marginBottom: 32 }}
            />
            <Form form={form} layout="vertical" initialValues={{ accessMode: 'NATIVE' }}>
              <div style={{ display: step === 0 ? 'block' : 'none' }}>
                <Form.Item
                  name="code"
                  label="Agent 编码"
                  rules={[{ required: true, message: '请输入编码' }, { max: 128 }]}
                  extra="唯一标识，建议小写字母与中划线，如 credit-assistant"
                >
                  <Input placeholder="如 credit-assistant" />
                </Form.Item>
                <Form.Item name="name" label="名称" rules={[{ required: true, message: '请输入名称' }, { max: 128 }]}>
                  <Input placeholder="如 信贷审批助手" />
                </Form.Item>
                <Form.Item name="description" label="描述（可选）" rules={[{ max: 1024 }]}>
                  <Input.TextArea rows={3} placeholder="这个 Agent 做什么、服务哪个业务场景" />
                </Form.Item>
              </div>

              <div style={{ display: step === 1 ? 'block' : 'none' }}>
                <Form.Item name="accessMode" label="接入方式" rules={[{ required: true }]}>
                  <div style={{ width: '100%' }}>
                    <Space direction="vertical" style={{ width: '100%' }}>
                      {MODE_DESC.map((m) => (
                        <Card
                          key={m.value}
                          size="small"
                          hoverable
                          onClick={() => form.setFieldValue('accessMode', m.value)}
                          style={{
                            borderColor: values.accessMode === m.value ? colors.brand : undefined,
                            borderWidth: values.accessMode === m.value ? 2 : 1,
                            background: values.accessMode === m.value ? colors.bgHover : undefined,
                          }}
                        >
                          <Space align="start">
                            <div
                              style={{
                                width: 16,
                                height: 16,
                                borderRadius: '50%',
                                border: `5px solid ${values.accessMode === m.value ? colors.brand : '#d9d9d9'}`,
                                marginTop: 2,
                              }}
                            />
                            <div>
                              <Typography.Text strong>{m.title}</Typography.Text>
                              <div>
                                <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                                  {m.desc}
                                </Typography.Text>
                              </div>
                            </div>
                          </Space>
                        </Card>
                      ))}
                    </Space>
                  </div>
                </Form.Item>
                {values.accessMode === 'HOSTED' && (
                  <>
                    <Alert
                      style={{ marginBottom: 16 }}
                      type="warning"
                      showIcon
                      message="HOSTED 模式必须提供运行与健康检查端点"
                    />
                    <Form.Item
                      name="runtimeEndpoint"
                      label="Runtime Endpoint"
                      rules={[{ required: true, message: 'HOSTED 模式必填' }, { max: 512 }]}
                    >
                      <Input placeholder="如 http://agent-svc:8080/run" />
                    </Form.Item>
                    <Form.Item
                      name="healthEndpoint"
                      label="Health Endpoint"
                      rules={[{ required: true, message: 'HOSTED 模式必填' }, { max: 512 }]}
                    >
                      <Input placeholder="如 http://agent-svc:8080/health" />
                    </Form.Item>
                  </>
                )}
              </div>

              <div style={{ display: step === 2 ? 'block' : 'none' }}>
                <Alert
                  style={{ marginBottom: 16 }}
                  type="info"
                  showIcon
                  icon={<CheckCircleOutlined />}
                  message="请确认以下信息，创建后可在详情页继续登记版本、绑定 CMDB、发布到广场"
                />
                <Descriptions bordered size="small" column={1}>
                  <Descriptions.Item label="归属项目">{projectId ?? '-'}</Descriptions.Item>
                  <Descriptions.Item label="编码">{values.code || '-'}</Descriptions.Item>
                  <Descriptions.Item label="名称">{values.name || '-'}</Descriptions.Item>
                  <Descriptions.Item label="接入方式">{values.accessMode}</Descriptions.Item>
                  {values.accessMode === 'HOSTED' && (
                    <>
                      <Descriptions.Item label="Runtime Endpoint">{values.runtimeEndpoint}</Descriptions.Item>
                      <Descriptions.Item label="Health Endpoint">{values.healthEndpoint}</Descriptions.Item>
                    </>
                  )}
                  <Descriptions.Item label="描述">{values.description || '-'}</Descriptions.Item>
                </Descriptions>
              </div>
            </Form>

            <div style={{ display: 'flex', justifyContent: 'space-between', marginTop: 32 }}>
              <Button disabled={step === 0} onClick={() => setStep(step - 1)}>
                上一步
              </Button>
              {step < 2 ? (
                <Button type="primary" onClick={next}>
                  下一步
                </Button>
              ) : (
                <Button type="primary" loading={submitting} disabled={!projectId} onClick={submit}>
                  确认创建
                </Button>
              )}
            </div>
          </Card>
        </Col>
      </Row>
    </>
  );
}
