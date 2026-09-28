package com.bosc.agentops.assethub.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bosc.agentops.assethub.dto.HttpApiConvertReq;
import com.bosc.agentops.assethub.dto.HttpApiRegisterReq;
import com.bosc.agentops.assethub.dto.ProjectScopedReq;
import com.bosc.agentops.assethub.dto.ToolDraft;
import com.bosc.agentops.assethub.entity.Asset;
import com.bosc.agentops.assethub.entity.AssetStatus;
import com.bosc.agentops.assethub.entity.AssetType;
import com.bosc.agentops.assethub.entity.AssetVersion;
import com.bosc.agentops.assethub.entity.AssetVersionStatus;
import com.bosc.agentops.assethub.entity.AssetVisibility;
import com.bosc.agentops.assethub.mapper.AssetMapper;
import com.bosc.agentops.assethub.mapper.AssetVersionMapper;
import com.bosc.agentops.common.api.BizException;
import com.bosc.agentops.common.api.ErrorCode;
import com.bosc.agentops.common.audit.AuditService;
import com.bosc.agentops.common.context.RequestContext;
import com.bosc.agentops.project.entity.Project;
import com.bosc.agentops.project.service.ProjectService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 历史 HTTP API（OpenAPI 3.x）注册与转换。
 * register：校验 OpenAPI 并把原文存为 HTTP_API 资产的 1.0.0 版本 definition（版本不可变，天然存档）；
 * tools/list：按需从 definition 重新解析工具草案；
 * convert：选定 operation 生成 MCP_TOOL 资产（DRAFT，待人工确认后发布）+ 1.0.0 版本（definition 内含
 * source_http_api_id 回链）。
 */
@Service
public class HttpApiService {

    static final String INITIAL_VERSION = "1.0.0";

    private final AssetMapper assetMapper;
    private final AssetVersionMapper assetVersionMapper;
    private final AssetService assetService;
    private final ProjectService projectService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public HttpApiService(AssetMapper assetMapper,
                          AssetVersionMapper assetVersionMapper,
                          AssetService assetService,
                          ProjectService projectService,
                          AuditService auditService,
                          ObjectMapper objectMapper) {
        this.assetMapper = assetMapper;
        this.assetVersionMapper = assetVersionMapper;
        this.assetService = assetService;
        this.projectService = projectService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Asset register(HttpApiRegisterReq req) {
        String userId = RequestContext.currentUserId();
        Project project = projectService.getOrThrow(req.getProjectId());
        projectService.requireActive(project);
        // 注册时即解析校验，不合法文档直接拒绝
        List<ToolDraft> tools = OpenApiParser.parseTools(objectMapper, req.getOpenApiJson());

        Asset asset = new Asset();
        asset.setCode(req.getCode());
        asset.setName(req.getName());
        asset.setAssetType(AssetType.HTTP_API);
        asset.setOwnerProjectId(req.getProjectId());
        asset.setOwnerUserId(userId);
        asset.setVisibility(AssetVisibility.PROJECT);
        asset.setStatus(AssetStatus.DRAFT);
        asset.setDescription(req.getDescription());
        asset.setCreatedBy(userId);
        try {
            assetMapper.insert(asset);
        } catch (DuplicateKeyException e) {
            throw new BizException(ErrorCode.DUPLICATE, "资产 code 已存在: " + req.getCode());
        }
        insertVersion(asset.getId(), INITIAL_VERSION, req.getOpenApiJson(), userId);
        auditService.record(AssetService.MODULE, "httpapi.register", "asset", asset.getId(),
                Map.of("code", asset.getCode(), "toolCount", tools.size()));
        return asset;
    }

    /** 从 HTTP_API 资产的 OpenAPI 原文解析工具定义草案 */
    public List<ToolDraft> listTools(Long id, ProjectScopedReq req) {
        Asset asset = requireHttpApi(id);
        assetService.requireVisible(asset, req.getProjectId());
        return OpenApiParser.parseTools(objectMapper, requireOpenApiJson(asset));
    }

    @Transactional
    public List<Asset> convert(Long id, HttpApiConvertReq req) {
        String userId = RequestContext.currentUserId();
        Asset httpApi = requireHttpApi(id);
        assetService.requireOwnerProject(httpApi, req.getProjectId());
        if (req.getMcpServiceId() != null) {
            Asset parent = assetService.getOrThrow(req.getMcpServiceId());
            if (parent.getAssetType() != AssetType.MCP_SERVICE) {
                throw new BizException(ErrorCode.PARAM_INVALID,
                        "mcpServiceId 必须指向 MCP_SERVICE 资产: " + req.getMcpServiceId());
            }
        }
        Map<String, ToolDraft> drafts = OpenApiParser.parseTools(objectMapper, requireOpenApiJson(httpApi))
                .stream().collect(Collectors.toMap(ToolDraft::getName, Function.identity(), (a, b) -> a));

        List<Asset> created = new ArrayList<>();
        for (String operation : req.getOperations()) {
            ToolDraft draft = drafts.get(operation);
            if (draft == null) {
                throw new BizException(ErrorCode.PARAM_INVALID,
                        "operation 不存在于 OpenAPI 工具草案: " + operation);
            }
            created.add(createToolAsset(httpApi, draft, req.getMcpServiceId(), userId));
        }
        auditService.record(AssetService.MODULE, "httpapi.convert", "asset", httpApi.getId(),
                Map.of("operations", req.getOperations(), "createdCount", created.size()));
        return created;
    }

    /** 生成 MCP_TOOL 资产（DRAFT，待人工确认后发布）+ 1.0.0 版本（definition 内含 source_http_api_id 回链） */
    private Asset createToolAsset(Asset httpApi, ToolDraft draft, Long mcpServiceId, String userId) {
        ObjectNode definition = objectMapper.createObjectNode();
        definition.put("name", draft.getName());
        if (draft.getDescription() != null) {
            definition.put("description", draft.getDescription());
        }
        definition.put("method", draft.getMethod());
        definition.put("path", draft.getPath());
        if (draft.getParameters() != null) {
            definition.set("parameters", draft.getParameters());
        }
        if (draft.getRequestBody() != null) {
            definition.set("requestBody", draft.getRequestBody());
        }
        definition.put("source_http_api_id", httpApi.getId());

        Asset tool = new Asset();
        tool.setCode(nextToolCode(httpApi.getCode(), draft.getName()));
        tool.setName(draft.getName());
        tool.setAssetType(AssetType.MCP_TOOL);
        tool.setParentAssetId(mcpServiceId);
        tool.setOwnerProjectId(httpApi.getOwnerProjectId());
        tool.setOwnerUserId(userId);
        tool.setVisibility(httpApi.getVisibility());
        tool.setStatus(AssetStatus.DRAFT);
        tool.setDescription(draft.getDescription());
        tool.setCreatedBy(userId);
        assetMapper.insert(tool);
        insertVersion(tool.getId(), INITIAL_VERSION,
                JsonSupport.toJson(objectMapper, definition), userId);
        return tool;
    }

    private AssetVersion insertVersion(Long assetId, String version, String definition, String userId) {
        AssetVersion assetVersion = new AssetVersion();
        assetVersion.setAssetId(assetId);
        assetVersion.setVersion(version);
        assetVersion.setDefinition(definition);
        assetVersion.setStatus(AssetVersionStatus.PUBLISHED);
        assetVersion.setPublishedBy(userId);
        assetVersion.setPublishedAt(LocalDateTime.now());
        assetVersion.setCreatedBy(userId);
        assetVersionMapper.insert(assetVersion);
        return assetVersion;
    }

    private Asset requireHttpApi(Long id) {
        Asset asset = assetService.getOrThrow(id);
        if (asset.getAssetType() != AssetType.HTTP_API) {
            throw new BizException(ErrorCode.PARAM_INVALID, "资产不是 HTTP_API 类型: " + id);
        }
        return asset;
    }

    private String requireOpenApiJson(Asset httpApi) {
        AssetVersion latest = assetVersionMapper.selectOne(new LambdaQueryWrapper<AssetVersion>()
                .eq(AssetVersion::getAssetId, httpApi.getId())
                .orderByDesc(AssetVersion::getId)
                .last("LIMIT 1"));
        if (latest == null || latest.getDefinition() == null) {
            throw new BizException(ErrorCode.STATE_CONFLICT,
                    "HTTP_API 资产缺少 OpenAPI 定义: " + httpApi.getId());
        }
        return latest.getDefinition();
    }

    /** 工具资产 code：{httpApiCode}-{toolName} 清洗为合法字符，冲突时追加序号 */
    private String nextToolCode(String httpApiCode, String toolName) {
        String base = (httpApiCode + "-" + toolName).replaceAll("[^A-Za-z0-9-]", "-");
        if (base.length() > 120) {
            base = base.substring(0, 120);
        }
        String code = base;
        int seq = 2;
        while (assetMapper.selectCount(new LambdaQueryWrapper<Asset>()
                .eq(Asset::getCode, code)) > 0) {
            code = base + "-" + seq++;
        }
        return code;
    }
}
