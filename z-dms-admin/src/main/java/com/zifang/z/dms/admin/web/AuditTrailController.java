package com.zifang.z.dms.admin.web;

import com.zifang.z.dms.admin.audit.AuditEntry;
import com.zifang.z.dms.admin.audit.AuditTrailService;
import com.zifang.z.dms.admin.config.DmsAdminProperties;
import com.zifang.z.dms.admin.web.dto.ApiResult;
import com.zifang.z.meta.api.annotation.BizApi;
import com.zifang.z.meta.api.annotation.BizField;
import com.zifang.z.meta.api.annotation.BizParam;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 审计流水：<b>变更与使用都在这条流里</b>。
 *
 * <p>{@code category=CHANGE} 是变更（谁改了什么），{@code category=USAGE} 是使用
 * （谁在什么时候用了哪个数据源、结论如何、耗时多少）。
 */
@RestController
@RequestMapping("/api/dms/audit")
public class AuditTrailController {

    private final AuditTrailService auditTrailService;
    private final DmsAdminProperties props;

    public AuditTrailController(AuditTrailService auditTrailService, DmsAdminProperties props) {
        this.auditTrailService = auditTrailService;
        this.props = props;
    }

    @GetMapping
    @BizApi(summary = "list", description = "", tags = { "z-dms" })
    public ApiResult<List<AuditEntry>> list(@RequestParam(required = false) @BizParam(name = "datasourceId", type = BizField.FieldType.STRING, description = "", position = BizParam.Position.QUERY) String datasourceId,
                                            @RequestParam(required = false)
                                            @BizParam(name = "limit", type = BizField.FieldType.INTEGER, description = "", position = BizParam.Position.QUERY) Integer limit) {
        int max = props.getAuditMaxLimit();
        int n = limit == null || limit <= 0 ? max : Math.min(limit, max);
        return ApiResult.ok(auditTrailService.listAudit(datasourceId, n));
    }
}



