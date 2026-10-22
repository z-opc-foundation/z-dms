package com.zifang.z.dms.admin.web;

import com.zifang.z.dms.admin.audit.AuditTrailService;
import com.zifang.z.dms.admin.audit.ChangeRecord;
import com.zifang.z.dms.admin.config.DmsAdminProperties;
import com.zifang.z.dms.admin.web.dto.ApiResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 变更历史：<b>每一次修改的前后值</b>。
 *
 * <p>返回的每条记录带 {@code beforeValue} / {@code afterValue} 两个 JSON 快照：
 * 新增时 before 为空、删除时 after 为空。界面据此做「改了什么」的对照。
 */
@RestController
@RequestMapping("/api/dms/changes")
public class ChangeHistoryController {

    private final AuditTrailService auditTrailService;
    private final DmsAdminProperties props;

    public ChangeHistoryController(AuditTrailService auditTrailService, DmsAdminProperties props) {
        this.auditTrailService = auditTrailService;
        this.props = props;
    }

    @GetMapping
    public ApiResult<List<ChangeRecord>> list(@RequestParam(required = false) String datasourceId,
                                               @RequestParam(required = false) Integer limit) {
        int max = props.getAuditMaxLimit();
        int n = limit == null || limit <= 0 ? max : Math.min(limit, max);
        return ApiResult.ok(auditTrailService.listChanges(datasourceId, n));
    }
}
