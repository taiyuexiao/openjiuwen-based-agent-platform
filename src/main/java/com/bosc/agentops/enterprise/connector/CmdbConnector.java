package com.bosc.agentops.enterprise.connector;

import java.util.Optional;

/**
 * 行内应用资产/CMDB 连接器 SPI。本期实现 MockCmdbConnector（配置内置应用），
 * 生产替换为行内 CMDB HTTP 连接器。fetchApp 找不到返回 empty，由调用方映射 40401。
 */
public interface CmdbConnector {

    /** 按应用唯一编号取权威信息；找不到返回 empty */
    Optional<CmdbApp> fetchApp(String appCode);

    /** 权威来源系统标识，落库到 app_binding.source_system */
    String sourceSystem();
}
