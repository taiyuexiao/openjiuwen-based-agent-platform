package com.bosc.agentops.enterprise.connector;

import com.bosc.agentops.enterprise.config.CmdbProperties;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CMDB 连接器 mock 实现：应用表来自配置 agentops.cmdb.mock-apps，启动时装入内存。
 * putApp/removeApp 用于模拟 CMDB 侧数据变更（演示与测试），生产实现替换为行内 CMDB HTTP 连接器后移除。
 */
@Component
public class MockCmdbConnector implements CmdbConnector {

    public static final String SOURCE_SYSTEM = "MOCK_CMDB";

    private final Map<String, CmdbApp> apps = new ConcurrentHashMap<>();

    public MockCmdbConnector(CmdbProperties properties) {
        properties.getMockApps().forEach((appCode, entry) -> apps.put(appCode,
                new CmdbApp(appCode, entry.getAppName(), entry.getOwner(), entry.getBizDomain(),
                        entry.getAppLevel(), entry.getStatus())));
    }

    @Override
    public Optional<CmdbApp> fetchApp(String appCode) {
        return Optional.ofNullable(apps.get(appCode));
    }

    @Override
    public String sourceSystem() {
        return SOURCE_SYSTEM;
    }

    /** mock 专用：模拟 CMDB 侧应用变更（如下线、Owner 变更） */
    public void putApp(CmdbApp app) {
        apps.put(app.getAppCode(), app);
    }

    /** mock 专用：模拟 CMDB 侧应用删除 */
    public void removeApp(String appCode) {
        apps.remove(appCode);
    }
}
