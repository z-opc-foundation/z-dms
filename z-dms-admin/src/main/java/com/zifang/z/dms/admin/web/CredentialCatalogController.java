package com.zifang.z.dms.admin.web;

import com.zifang.z.dms.admin.config.DmsAdminProperties;
import com.zifang.z.dms.admin.web.dto.ApiResult;
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

    public CredentialCatalogController(DmsAdminProperties props) {
        this.props = props;
    }

    @GetMapping
    public ApiResult<List<String>> list() {
        return ApiResult.ok(new ArrayList<>(props.getCredentialRefs()));
    }
}
