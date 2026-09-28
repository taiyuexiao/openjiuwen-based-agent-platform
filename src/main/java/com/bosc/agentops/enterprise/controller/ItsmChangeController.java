package com.bosc.agentops.enterprise.controller;

import com.bosc.agentops.common.api.ApiResponse;
import com.bosc.agentops.common.permission.RequirePermission;
import com.bosc.agentops.enterprise.dto.ItsmChangeListReq;
import com.bosc.agentops.enterprise.dto.ItsmChangeRecordReq;
import com.bosc.agentops.enterprise.entity.ItsmChangeRecord;
import com.bosc.agentops.enterprise.service.ItsmChangeService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * ITSM 变更登记（本期仅登记与查询）。
 */
@RestController
@RequestMapping("/v1/api/itsm/changes")
public class ItsmChangeController {

    private final ItsmChangeService itsmChangeService;

    public ItsmChangeController(ItsmChangeService itsmChangeService) {
        this.itsmChangeService = itsmChangeService;
    }

    @PostMapping("/record")
    @RequirePermission("binding:manage")
    public ApiResponse<ItsmChangeRecord> record(@Valid @RequestBody ItsmChangeRecordReq req) {
        return ApiResponse.ok(itsmChangeService.record(req));
    }

    @PostMapping("/list")
    @RequirePermission("binding:read")
    public ApiResponse<List<ItsmChangeRecord>> list(@Valid @RequestBody ItsmChangeListReq req) {
        return ApiResponse.ok(itsmChangeService.list(req));
    }
}
