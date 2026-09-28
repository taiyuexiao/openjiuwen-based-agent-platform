package com.bosc.agentops.agentdev.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bosc.agentops.agentdev.entity.ScaffoldTemplate;
import com.bosc.agentops.agentdev.mapper.ScaffoldTemplateMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 脚手架模板静态预置（选择启动初始化而非迁移脚本：模板内容为多行 Python 文件，
 * 放在 Java 文本块中可维护性优于 SQL 转义；按 code 幂等，重复启动不重复插入）。
 * 两个预置模板均引用 openJiuwen agent-core 的真实用法，含 {{MODEL_CODE}} {{PROJECT_CODE}} 占位符。
 */
@Component
public class ScaffoldTemplateInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ScaffoldTemplateInitializer.class);

    private static final String REACT_MAIN_PY = """
            # react-agent-basic：基于 openJiuwen agent-core 的最小可运行 ReAct Agent。
            #
            # 平台占位符（由平台 SDK/脚手架渲染时替换）：
            #   {{MODEL_CODE}}    已授权模型 code（平台模型目录中的 modelCode）
            #   {{PROJECT_CODE}}  所属项目 code（声明登记与配置拉取上下文）
            # 运行前经环境变量提供模型接入：API_BASE / API_KEY / MODEL_PROVIDER。
            import asyncio
            import os

            from openjiuwen.core.foundation.llm import ModelRequestConfig, ModelClientConfig
            from openjiuwen.core.single_agent import AgentCard, ReActAgentConfig, ReActAgent
            from openjiuwen.core.runner import Runner

            MODEL_NAME = "{{MODEL_CODE}}"
            PROJECT_CODE = "{{PROJECT_CODE}}"

            API_BASE = os.getenv("API_BASE", "your api base")
            API_KEY = os.getenv("API_KEY", "your api key")
            MODEL_PROVIDER = os.getenv("MODEL_PROVIDER", "your model provider")


            def build_agent() -> ReActAgent:
                model_config = ModelRequestConfig(model=MODEL_NAME, temperature=0.6, top_p=0.8)
                model_client = ModelClientConfig(
                    client_provider=MODEL_PROVIDER,
                    api_base=API_BASE,
                    api_key=API_KEY,
                    verify_ssl=False,
                )
                card = AgentCard(id="react_agent_" + PROJECT_CODE, description="ReAct 助手")
                config = ReActAgentConfig(
                    model_client_config=model_client,
                    model_config_obj=model_config,
                    prompt_template=[dict(role="system", content="你是一个 AI 助手，简洁回答用户问题。")],
                )
                return ReActAgent(card=card).configure(config)


            async def main():
                agent = build_agent()
                result = await Runner.run_agent(
                    agent=agent, inputs={"query": "你好，请自我介绍", "conversation_id": "demo-001"})
                print(result)


            if __name__ == "__main__":
                asyncio.run(main())
            """;

    private static final String WORKFLOW_MAIN_PY = """
            # workflow-basic：基于 openJiuwen agent-core 的最小可运行 Workflow（start -> llm -> end）。
            #
            # 平台占位符（由平台 SDK/脚手架渲染时替换）：
            #   {{MODEL_CODE}}    已授权模型 code（平台模型目录中的 modelCode）
            #   {{PROJECT_CODE}}  所属项目 code（声明登记与配置拉取上下文）
            # 运行前经环境变量提供模型接入：API_BASE / API_KEY / MODEL_PROVIDER。
            import asyncio
            import os

            from openjiuwen.core.foundation.llm import ModelConfig
            from openjiuwen.core.utils.llm.base import BaseModelInfo
            from openjiuwen.core.workflow import End, Workflow, WorkflowConfig, WorkflowMetadata
            from openjiuwen.core.workflow.components.start_comp import Start
            from openjiuwen.core.workflow import LLMComponent, LLMCompConfig
            from openjiuwen.agent.common.schema import WorkflowSchema
            from openjiuwen.agent.config.workflow_config import WorkflowAgentConfig
            from openjiuwen.agent.workflow_agent.workflow_agent import WorkflowAgent

            MODEL_NAME = "{{MODEL_CODE}}"
            PROJECT_CODE = "{{PROJECT_CODE}}"

            API_BASE = os.getenv("API_BASE", "your api base")
            API_KEY = os.getenv("API_KEY", "your api key")
            MODEL_PROVIDER = os.getenv("MODEL_PROVIDER", "your model provider")


            def create_model_config() -> ModelConfig:
                return ModelConfig(
                    model_provider=MODEL_PROVIDER,
                    model_info=BaseModelInfo(
                        model=MODEL_NAME,
                        api_base=API_BASE,
                        api_key=API_KEY,
                        temperature=0.7,
                        top_p=0.9,
                        timeout=120,
                    ),
                )


            def build_flow() -> Workflow:
                flow = Workflow(workflow_config=WorkflowConfig(
                    metadata=WorkflowMetadata(name="basic_flow", id="wf_" + PROJECT_CODE, version="1.0")))

                start = Start({"inputs": [
                    {"id": "query", "type": "String", "required": "true", "sourceType": "ref"}]})
                llm = LLMComponent(LLMCompConfig(
                    model=create_model_config(),
                    template_content=[{"role": "user", "content": "请用一句话回答：{{query}}"}],
                    response_format={"type": "text"},
                    output_config={"output": {"type": "string", "description": "回答", "required": True}},
                ))
                end = End({"responseTemplate": "{{output}}"})

                flow.set_start_comp("start", start, inputs_schema={"query": "${query}"})
                flow.add_workflow_comp("llm", llm, inputs_schema={"query": "${start.query}"})
                flow.set_end_comp("end", end, inputs_schema={"output": "${llm.output}"})
                flow.add_connection("start", "llm")
                flow.add_connection("llm", "end")
                return flow


            async def main():
                schema = WorkflowSchema(
                    id="wf_" + PROJECT_CODE, name="basic_flow", version="1.0",
                    description="最小工作流 Agent",
                    inputs={"query": {"type": "string"}})
                agent = WorkflowAgent(WorkflowAgentConfig(
                    id="workflow_agent_" + PROJECT_CODE, version="0.1.0",
                    description="workflow-basic 示例", workflows=[schema]))
                agent.bind_workflows([build_flow()])
                result = await agent.invoke({"query": "你好", "conversation_id": "demo-001"})
                print(result)


            if __name__ == "__main__":
                asyncio.run(main())
            """;

    private static final String REQUIREMENTS = "openjiuwen\n";

    private final ScaffoldTemplateMapper scaffoldTemplateMapper;
    private final ObjectMapper objectMapper;

    public ScaffoldTemplateInitializer(ScaffoldTemplateMapper scaffoldTemplateMapper, ObjectMapper objectMapper) {
        this.scaffoldTemplateMapper = scaffoldTemplateMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        seed("react-agent-basic", "ReAct Agent 基础模板",
                "最小可运行 ReAct Agent：ModelRequestConfig/ModelClientConfig 配置模型，"
                        + "ReActAgent.configure 构建，Runner.run_agent 执行。",
                Map.of(
                        "main.py", REACT_MAIN_PY,
                        "requirements.txt", REQUIREMENTS,
                        "README.md", "# react-agent-basic\n\n"
                                + "基于 openJiuwen agent-core 的 ReAct Agent 脚手架。"
                                + "替换 {{MODEL_CODE}} {{PROJECT_CODE}} 占位符，"
                                + "配置 API_BASE/API_KEY/MODEL_PROVIDER 环境变量后运行 `python main.py`。\n"));
        seed("workflow-basic", "Workflow 基础模板",
                "最小可运行 Workflow：Start -> LLMComponent -> End，"
                        + "经 WorkflowAgent.bind_workflows 绑定后 invoke 执行。",
                Map.of(
                        "main.py", WORKFLOW_MAIN_PY,
                        "requirements.txt", REQUIREMENTS,
                        "README.md", "# workflow-basic\n\n"
                                + "基于 openJiuwen agent-core 的 Workflow Agent 脚手架（start -> llm -> end）。"
                                + "替换 {{MODEL_CODE}} {{PROJECT_CODE}} 占位符，"
                                + "配置 API_BASE/API_KEY/MODEL_PROVIDER 环境变量后运行 `python main.py`。\n"));
    }

    private void seed(String code, String name, String description, Map<String, String> files) throws Exception {
        Long count = scaffoldTemplateMapper.selectCount(
                new LambdaQueryWrapper<ScaffoldTemplate>().eq(ScaffoldTemplate::getCode, code));
        if (count > 0) {
            return;
        }
        ScaffoldTemplate template = new ScaffoldTemplate();
        template.setCode(code);
        template.setName(name);
        template.setLanguage("python");
        template.setDescription(description);
        // LinkedHashMap 保序，保证 files JSON 中文件顺序稳定
        template.setFiles(objectMapper.writeValueAsString(new LinkedHashMap<>(files)));
        scaffoldTemplateMapper.insert(template);
        log.info("脚手架模板已预置: {}", code);
    }
}
