package com.bosc.agentops.common.audit;

import com.bosc.agentops.common.audit.entity.AuditEvent;
import com.bosc.agentops.common.audit.mapper.AuditEventMapper;
import com.bosc.agentops.common.context.RequestContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 审计记录。写操作在业务事务内调用，与业务操作同事务提交/回滚。
 */
@Service
public class AuditService {

    public static final String RESULT_SUCCESS = "SUCCESS";
    public static final String RESULT_FAIL = "FAIL";

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditEventMapper auditEventMapper;
    private final ObjectMapper objectMapper;

    public AuditService(AuditEventMapper auditEventMapper, ObjectMapper objectMapper) {
        this.auditEventMapper = auditEventMapper;
        this.objectMapper = objectMapper;
    }

    public void record(String module, String action, String resourceType, Object resourceId, Object detail) {
        record(module, action, resourceType, resourceId, detail, RESULT_SUCCESS);
    }

    public void record(String module, String action, String resourceType, Object resourceId, Object detail,
                       String result) {
        AuditEvent event = new AuditEvent();
        event.setRequestId(RequestContext.currentRequestId());
        event.setUserId(RequestContext.currentUserId());
        event.setModule(module);
        event.setAction(action);
        event.setResourceType(resourceType);
        event.setResourceId(resourceId == null ? null : resourceId.toString());
        event.setDetail(toJson(detail));
        event.setResult(result);
        auditEventMapper.insert(event);
    }

    private String toJson(Object detail) {
        if (detail == null) {
            return null;
        }
        if (detail instanceof String s) {
            return s;
        }
        try {
            return objectMapper.writeValueAsString(detail);
        } catch (JsonProcessingException e) {
            log.warn("审计 detail 序列化失败: {}", e.getMessage());
            return String.valueOf(detail);
        }
    }
}
