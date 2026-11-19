package com.zifang.z.dms.admin.auth;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

import java.util.ArrayList;
import java.util.List;

/**
 * 登录凭证提取（与 z-ctc SsoInterceptor 同一套通道口径，但只认「安全通道」）。
 *
 * <p>通道优先级：
 * <ol>
 *   <li>{@code Authorization: Bearer <token>}（推荐：不进 URL、不进 access log）；</li>
 *   <li>SSO cookie（{@code sso_token} / {@code zctc_token}，浏览器主通道）。</li>
 * </ol>
 *
 * <p><b>刻意不认 {@code ?token=} 查询参数</b> —— 会落进浏览器历史 / 网关日志 / Referer
 * （z-ctc 仓 TASK-20260924-017 的收窄结论直接沿用）。
 */
public final class DmsTokenExtractor {

    public static final String ATTR_CURRENT_USER = "dmsCurrentUser";

    /** z-ctc-sso 缺席时的 cookie 候选兜底（正常配置下由 SsoProperties 下发）。 */
    static final String[] DEFAULT_COOKIE_NAMES = {"sso_token", "zctc_token"};

    private DmsTokenExtractor() {
    }

    public static String extract(HttpServletRequest request, List<String> cookieNames) {
        if (request == null) {
            return null;
        }
        String auth = request.getHeader("Authorization");
        if (auth != null && !auth.trim().isEmpty()) {
            String t = auth.startsWith("Bearer ") ? auth.substring(7) : auth;
            t = t.trim();
            if (!t.isEmpty()) {
                return t;
            }
        }
        List<String> names = (cookieNames == null || cookieNames.isEmpty())
                ? java.util.Arrays.asList(DEFAULT_COOKIE_NAMES) : cookieNames;
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (String name : names) {
            for (Cookie c : cookies) {
                if (name.equals(c.getName()) && c.getValue() != null && !c.getValue().trim().isEmpty()) {
                    return c.getValue().trim();
                }
            }
        }
        return null;
    }
}
