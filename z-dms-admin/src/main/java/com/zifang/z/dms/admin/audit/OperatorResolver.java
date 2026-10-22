package com.zifang.z.dms.admin.audit;

import com.zifang.z.dms.admin.config.DmsAdminProperties;

import javax.servlet.http.HttpServletRequest;

/**
 * 操作人解析（审计面要能回答「谁」）。
 *
 * <p>取值顺序：
 * <ol>
 *   <li>请求头 {@code z.dms.admin.operator-header}（默认 {@code X-Operator}）——
 *       组织口径是网关 / SSO 把登录用户写进这个头；</li>
 *   <li>备用头 {@code X-User-Name} / {@code X-Forwarded-User}；</li>
 *   <li>都没有 ⇒ 记成 {@code anonymous}（<b>不猜、不编</b>：宁可写 anonymous，
 *       也不要写一个看起来像真人的名字）。</li>
 * </ol>
 */
public class OperatorResolver {

    /** 没有任何身份信息时的占位（明确表示「不知道」，不是「某个人」）。 */
    public static final String ANONYMOUS = "anonymous";

    private static final String[] FALLBACK_HEADERS = {"X-User-Name", "X-Forwarded-User"};

    private final DmsAdminProperties props;

    public OperatorResolver(DmsAdminProperties props) {
        this.props = props;
    }

    public String resolve(HttpServletRequest request) {
        if (request == null) {
            return ANONYMOUS;
        }
        String primary = header(request, props.getOperatorHeader());
        if (primary != null) {
            return primary;
        }
        for (String h : FALLBACK_HEADERS) {
            String v = header(request, h);
            if (v != null) {
                return v;
            }
        }
        return ANONYMOUS;
    }

    private String header(HttpServletRequest request, String name) {
        if (name == null || name.trim().isEmpty()) {
            return null;
        }
        String v = request.getHeader(name);
        if (v == null) {
            return null;
        }
        String t = v.trim();
        if (t.isEmpty()) {
            return null;
        }
        // 头里的内容会进库，截断 + 去控制字符（防日志注入）。
        String flat = t.replaceAll("[\\r\\n\\t]", " ");
        return flat.length() > 64 ? flat.substring(0, 64) : flat;
    }
}
