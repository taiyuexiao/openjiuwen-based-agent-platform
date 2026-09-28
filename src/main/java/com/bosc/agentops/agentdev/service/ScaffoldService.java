package com.bosc.agentops.agentdev.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bosc.agentops.agentdev.entity.ScaffoldTemplate;
import com.bosc.agentops.agentdev.mapper.ScaffoldTemplateMapper;
import com.bosc.agentops.common.api.BizException;
import com.bosc.agentops.common.api.ErrorCode;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 脚手架模板查询。本期静态预置（见 ScaffoldTemplateInitializer），不做远程代码生成服务。
 */
@Service
public class ScaffoldService {

    private final ScaffoldTemplateMapper scaffoldTemplateMapper;

    public ScaffoldService(ScaffoldTemplateMapper scaffoldTemplateMapper) {
        this.scaffoldTemplateMapper = scaffoldTemplateMapper;
    }

    /** 列表只回模板元数据，文件内容走 detail */
    public List<ScaffoldTemplate> list() {
        List<ScaffoldTemplate> templates = scaffoldTemplateMapper.selectList(
                new LambdaQueryWrapper<ScaffoldTemplate>().orderByAsc(ScaffoldTemplate::getId));
        templates.forEach(t -> t.setFiles(null));
        return templates;
    }

    public ScaffoldTemplate detail(String code) {
        ScaffoldTemplate template = scaffoldTemplateMapper.selectOne(
                new LambdaQueryWrapper<ScaffoldTemplate>().eq(ScaffoldTemplate::getCode, code));
        if (template == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "脚手架模板不存在: " + code);
        }
        return template;
    }
}
