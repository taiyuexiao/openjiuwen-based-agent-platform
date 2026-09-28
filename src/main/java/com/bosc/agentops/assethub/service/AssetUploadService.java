package com.bosc.agentops.assethub.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bosc.agentops.assethub.dto.AssetUploadResp;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 资产文件上传：multipart 文件落本地目录，资产/版本均为 DRAFT 草稿（发布走既有 publish 流程）。
 * code 已存在 → 在该资产下新增 DRAFT 版本（版本号必须未被用）；否则创建资产 + DRAFT 版本。
 * definition JSON 记录 {filename,size,sha256,storedPath,contentType}。
 */
@Service
public class AssetUploadService {

    /** 允许的扩展名（小写，含点） */
    static final Set<String> ALLOWED_EXTENSIONS = Set.of(".md", ".zip", ".json", ".yaml", ".txt");
    static final long MAX_FILE_SIZE = 20L * 1024 * 1024;
    private static final Pattern VERSION_PATTERN = Pattern.compile("^\\d+\\.\\d+\\.\\d+$");
    private static final Pattern CODE_PATTERN = Pattern.compile("^[A-Za-z0-9-]+$");

    private final AssetMapper assetMapper;
    private final AssetVersionMapper assetVersionMapper;
    private final AssetService assetService;
    private final ProjectService projectService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final Path uploadDir;

    public AssetUploadService(AssetMapper assetMapper,
                              AssetVersionMapper assetVersionMapper,
                              AssetService assetService,
                              ProjectService projectService,
                              AuditService auditService,
                              ObjectMapper objectMapper,
                              @Value("${agentops.asset-upload.dir:data/uploads}") String uploadDir) {
        this.assetMapper = assetMapper;
        this.assetVersionMapper = assetVersionMapper;
        this.assetService = assetService;
        this.projectService = projectService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.uploadDir = Path.of(uploadDir);
    }

    @Transactional
    public AssetUploadResp upload(MultipartFile file, Long projectId, String assetType,
                                  String name, String code, String version) {
        String userId = RequestContext.currentUserId();
        Project project = projectService.getOrThrow(projectId);
        projectService.requireActive(project);

        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "上传文件不能为空");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "文件大小超过上限 20MB: " + file.getSize());
        }
        String filename = sanitizeFilename(file.getOriginalFilename());
        String extension = extensionOf(filename);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "不支持的文件类型: " + extension + "，仅支持 " + ALLOWED_EXTENSIONS);
        }
        AssetType type = parseAssetType(assetType);
        if (name == null || name.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "name 不能为空");
        }
        if (version == null || version.isBlank()) {
            version = "1.0.0";
        }
        if (!VERSION_PATTERN.matcher(version).matches()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "版本号必须为 x.y.z 格式: " + version);
        }

        byte[] content = readBytes(file);
        String sha256 = sha256Hex(content);

        Asset asset = resolveOrCreateAsset(project, userId, type, name, code);
        if (assetVersionMapper.selectCount(new LambdaQueryWrapper<AssetVersion>()
                .eq(AssetVersion::getAssetId, asset.getId())
                .eq(AssetVersion::getVersion, version)) > 0) {
            throw new BizException(ErrorCode.DUPLICATE,
                    "版本已存在，请使用新版本号: " + version);
        }

        String storedPath = storeFile(asset.getId(), version, filename, content);
        String definition = buildDefinition(filename, content.length, sha256, storedPath, file.getContentType());

        AssetVersion assetVersion = new AssetVersion();
        assetVersion.setAssetId(asset.getId());
        assetVersion.setVersion(version);
        assetVersion.setDefinition(definition);
        assetVersion.setStatus(AssetVersionStatus.DRAFT);
        assetVersion.setCreatedBy(userId);
        try {
            assetVersionMapper.insert(assetVersion);
        } catch (DuplicateKeyException e) {
            throw new BizException(ErrorCode.DUPLICATE, "版本已存在，请使用新版本号: " + version);
        }

        auditService.record(AssetService.MODULE, "asset.upload", "asset", asset.getId(),
                Map.of("code", asset.getCode(), "version", version,
                        "filename", filename, "size", content.length, "sha256", sha256));

        AssetUploadResp resp = new AssetUploadResp();
        resp.setAssetId(asset.getId());
        resp.setAssetCode(asset.getCode());
        resp.setAssetStatus(asset.getStatus().name());
        resp.setVersionId(assetVersion.getId());
        resp.setVersion(version);
        resp.setVersionStatus(AssetVersionStatus.DRAFT.name());
        resp.setFilename(filename);
        resp.setSize(content.length);
        resp.setSha256(sha256);
        resp.setContentType(file.getContentType());
        return resp;
    }

    /** code 已存在 → 复用该资产（必须归属当前项目且未下线）；否则新建 DRAFT 资产 */
    private Asset resolveOrCreateAsset(Project project, String userId, AssetType type, String name, String code) {
        if (code != null && !code.isBlank()) {
            Asset existing = assetMapper.selectOne(
                    new LambdaQueryWrapper<Asset>().eq(Asset::getCode, code));
            if (existing != null) {
                assetService.requireOwnerProject(existing, project.getId());
                if (existing.getStatus() == AssetStatus.OFFLINE) {
                    throw new BizException(ErrorCode.STATE_CONFLICT,
                            "资产已下线，禁止上传新版本: " + existing.getId());
                }
                return existing;
            }
        }
        Asset asset = new Asset();
        asset.setCode(code == null || code.isBlank() ? "upload-" + UUID.randomUUID() : code);
        if (!CODE_PATTERN.matcher(asset.getCode()).matches() || asset.getCode().length() > 128) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "code 仅允许字母、数字、中划线且不超过 128 字符: " + code);
        }
        asset.setName(name);
        asset.setAssetType(type);
        asset.setOwnerProjectId(project.getId());
        asset.setOwnerUserId(userId);
        asset.setVisibility(AssetVisibility.PROJECT);
        asset.setStatus(AssetStatus.DRAFT);
        asset.setCreatedBy(userId);
        try {
            assetMapper.insert(asset);
        } catch (DuplicateKeyException e) {
            throw new BizException(ErrorCode.DUPLICATE, "资产 code 已存在: " + asset.getCode());
        }
        return asset;
    }

    private String storeFile(Long assetId, String version, String filename, byte[] content) {
        Path target = uploadDir.resolve(String.valueOf(assetId)).resolve(version).resolve(filename);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, content);
        } catch (IOException e) {
            throw new UncheckedIOException("文件写入失败: " + target, e);
        }
        return uploadDir.relativize(target).toString();
    }

    private String buildDefinition(String filename, long size, String sha256,
                                   String storedPath, String contentType) {
        Map<String, Object> definition = new LinkedHashMap<>();
        definition.put("filename", filename);
        definition.put("size", size);
        definition.put("sha256", sha256);
        definition.put("storedPath", storedPath);
        definition.put("contentType", contentType == null ? "" : contentType);
        try {
            return objectMapper.writeValueAsString(definition);
        } catch (IOException e) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "definition 序列化失败: " + e.getMessage());
        }
    }

    /** 仅取文件名部分，拒绝路径穿越 */
    private String sanitizeFilename(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "文件名不能为空");
        }
        String filename = originalFilename.replace('\\', '/');
        filename = filename.substring(filename.lastIndexOf('/') + 1);
        if (filename.isBlank() || filename.contains("..")) {
            throw new BizException(ErrorCode.PARAM_INVALID, "非法文件名: " + originalFilename);
        }
        return filename;
    }

    private String extensionOf(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot).toLowerCase();
    }

    private AssetType parseAssetType(String assetType) {
        if (assetType == null || assetType.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "assetType 不能为空");
        }
        try {
            return AssetType.valueOf(assetType.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BizException(ErrorCode.PARAM_INVALID, "不支持的 assetType: " + assetType);
        }
    }

    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("文件读取失败: " + file.getOriginalFilename(), e);
        }
    }

    private String sha256Hex(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
