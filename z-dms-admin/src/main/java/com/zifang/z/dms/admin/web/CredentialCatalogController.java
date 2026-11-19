package com.zifang.z.dms.admin.web;

import com.zifang.z.dms.admin.auth.DmsPermissionPoint;
import com.zifang.z.dms.admin.auth.DmsPermissionService;
import com.zifang.z.dms.admin.config.DmsAdminProperties;
import com.zifang.z.dms.admin.web.dto.ApiResult;
import com.zifang.z.meta.api.annotation.BizApi;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * 凭据引用目录（界面上的凭据面只有这一个下拉选择器）。
 *
 * <p>返回的是<b>引用清单</b>（{@code <group>/<key>}），<b>不是口令</b> ——
 * 口令永远不进浏览器、也不进这个接口。
 */
@RestController
@RequestMapping("/api/dms/credential-refs")
public class CredentialCatalogController {

    private final DmsAdminProperties props;
    private final DmsPermissionService permissionService;

    public CredentialCatalogController(DmsAdminProperties props, DmsPermissionService permissionService) {
        this.props = props;
        this.permissionService = permissionService;
    }

    @GetMapping
    @BizApi(summary = "list", description = "", tags = { "z-dms" })
    public ApiResult<List<String>> list(HttpServletRequest request) {
        permissionService.require(permissionService.currentUser(request),
                DmsPermissionPoint.DMS_SOURCE_MANAGE);
        return ApiResult.ok(new ArrayList<>(props.getCredentialRefs()));
    }
}



