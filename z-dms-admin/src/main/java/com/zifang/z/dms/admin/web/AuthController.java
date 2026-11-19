package com.zifang.z.dms.admin.web;

import com.zifang.z.dms.admin.auth.DmsCurrentUser;
import com.zifang.z.dms.admin.auth.DmsPermissionPoint;
import com.zifang.z.dms.admin.auth.DmsPermissionService;
import com.zifang.z.dms.admin.web.dto.ApiResult;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 当前用户的身份与权限点（前端做菜单显隐 / 按钮置灰用）。
 *
 * <p><b>前端只拿结论，不拿 token</b>：token 从头到尾不出后端。
 * 真正的权限闸在服务端每个端点上（前端显隐只是体验，不是安全边界）。
 */
@RestController
@RequestMapping("/api/dms/auth")
public class AuthController {

    private final DmsPermissionService permissionService;

    public AuthController(DmsPermissionService permissionService) {
        this.permissionService = permissionService;
    }

    /** 我是谁、我有什么权限（未登录也返回：前端靠它判断登录态并拿登录跳转地址）。 */
    @GetMapping("/me")
    public ApiResult<Map<String, Object>> me(HttpServletRequest request) {
        DmsCurrentUser user = permissionService.currentUserOrNull(request);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("enabled", permissionService.enabled());
        if (!permissionService.enabled() || user == null || !user.isAuthenticated()) {
            out.put("authenticated", false);
            out.put("display", null);
            out.put("degraded", false);
            out.put("permissions", List.of());
            out.put("loginUrl", permissionService.loginUrl());
            return ApiResult.ok(out);
        }
        out.put("authenticated", true);
        out.put("display", user.display());
        Set<String> codes = permissionService.codesOf(user);
        out.put("degraded", codes == null);
        out.put("permissions", codes == null ? List.of() : new ArrayList<>(codes));
        return ApiResult.ok(out);
    }

    /** z-dms 的权限点清单（4A 中心申请授权时对照用）。 */
    @GetMapping("/points")
    public ApiResult<List<Map<String, Object>>> points() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (DmsPermissionPoint p : DmsPermissionPoint.values()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("code", p.getCode());
            m.put("label", p.getLabel());
            m.put("description", p.getDescription());
            m.put("sortOrder", p.getSortOrder());
            out.add(m);
        }
        return ApiResult.ok(out);
    }
}
