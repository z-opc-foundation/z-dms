package com.zifang.z.dms.admin.auth;

import com.zifang.ctc.client.ZCtcClient;
import com.zifang.ctc.client.model.ZCtcResource;
import com.zifang.ctc.sso.config.SsoProperties;
import com.zifang.ctc.sso.config.TokenService;
import com.zifang.ctc.sso.model.UserInfo;
import com.zifang.z.dms.admin.audit.OperatorResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * z-dms 权限管控门面（登录态 + 授权数据两个 z-ctc 链路的收口）。
 *
 * <p>两条链路各自独立（z-ctc 的核心设计，故障域分开）：
 * <ul>
 *   <li><b>登录态</b>（你是谁）：{@link TokenService} 验签 —— 纯验签，不查 4A 的库；</li>
 *   <li><b>授权数据</b>（你能干什么）：{@link ZCtcClient} 拉权限点 —— 查 4A 的库，
 *       <b>必须转发终端用户的 token</b>（authz 端点被 4A 自己的 SSO 闸门保护）。</li>
 * </ul>
 *
 * <p><b>可关闭</b>：{@code z.dms.auth.enabled=false} ⇒ {@link #enabled()} 为 false，
 * 所有 {@link #require} 直接放行、身份退回 {@code X-Operator} 头。
 *
 * <p><b>三态降级</b>（照 z-team 的教训）：「判定为无权限」与「判定不出来」不是一回事 ——
 * z-ctc 不可达 ⇒ 按<b>无权限</b>处理（fail-closed），但<b>降级不写缓存</b>（不把一次网络抖动
 * 缓存成 30 秒的无权限），并向用户如实说明是降级而不是越权拒绝。
 */
public class DmsPermissionService {

    private static final Logger log = LoggerFactory.getLogger(DmsPermissionService.class);

    private final DmsAuthProperties props;
    private final TokenService tokenService;
    private final SsoProperties ssoProperties;
    private final ZCtcClient ctcClient;
    private final OperatorResolver operatorResolver;

    private final ConcurrentHashMap<Long, CacheEntry> cache = new ConcurrentHashMap<>();

    public DmsPermissionService(DmsAuthProperties props,
                                TokenService tokenService,
                                SsoProperties ssoProperties,
                                ZCtcClient ctcClient,
                                OperatorResolver operatorResolver) {
        this.props = props;
        this.tokenService = tokenService;
        this.ssoProperties = ssoProperties;
        this.ctcClient = ctcClient;
        this.operatorResolver = operatorResolver;
    }

    /** 权限管控是否生效（配置开关 && z-ctc 客户端就位）。 */
    public boolean enabled() {
        return props.isEnabled() && ctcClient != null && tokenService != null;
    }

    public String appCode() {
        return props.getAppCode();
    }

    // ---------------------------------------------------------- 登录态（401）

    /**
     * 当前用户。管控开启时 401 由 {@link DmsUnauthenticatedException} 表达；
     * 管控关闭时返回匿名占位（审计里的操作人退回 X-Operator 头）。
     */
    public DmsCurrentUser currentUser(HttpServletRequest request) {
        if (!enabled()) {
            return DmsCurrentUser.anonymous();
        }
        Object cached = request == null ? null : request.getAttribute(DmsTokenExtractor.ATTR_CURRENT_USER);
        if (cached instanceof DmsCurrentUser) {
            return (DmsCurrentUser) cached;
        }
        String token = DmsTokenExtractor.extract(request, effectiveCookieNames());
        if (token == null || token.isEmpty()) {
            throw new DmsUnauthenticatedException(
                    "未携带登录凭证（请通过 z-ctc SSO 登录；接口走 Authorization: Bearer）");
        }
        UserInfo info;
        try {
            info = tokenService.verifyToken(token);
        } catch (RuntimeException e) {
            // 4A 中心验签通道挂了 ≠ 未登录：降级为不可判定，fail-closed。
            log.warn("[DMS-GATE] 验签通道异常（按未登录处理，fail-closed）: {}", e.toString());
            throw new DmsUnauthenticatedException("登录态无法确认（4A 中心不可达），已按未登录处理");
        }
        if (info == null || info.getUserId() == null || info.getUserId().trim().isEmpty()) {
            throw new DmsUnauthenticatedException("登录态无效或已过期，请重新登录");
        }
        DmsCurrentUser user = new DmsCurrentUser();
        try {
            user.setUserId(Long.parseLong(info.getUserId().trim()));
        } catch (NumberFormatException e) {
            throw new DmsUnauthenticatedException("登录凭证里的 userId 不是合法数字（" + "拒绝继续）");
        }
        user.setUsername(info.getUsername());
        user.setNickname(info.getNickname());
        user.setAuthenticated(true);
        // token 存进请求域内存对象：授权链（codesOf）要转发终端用户 token；
        // 它不进审计流水 / 日志 / 响应体（DmsCurrentUser 只有 display() 对外）。
        user.setToken(token);
        request.setAttribute(DmsTokenExtractor.ATTR_CURRENT_USER, user);
        return user;
    }

    /**
     * 当前用户，不抛版本：未登录 / 验签不可用 ⇒ 返回 null（给 {@code /auth/me} 这类
     * 「闸门外」端点用 —— 前端要靠它判断是否已登录、拿登录跳转地址）。
     */
    public DmsCurrentUser currentUserOrNull(HttpServletRequest request) {
        try {
            return currentUser(request);
        } catch (DmsUnauthenticatedException e) {
            return null;
        }
    }

    /** SSO 登录页地址（配置了才有；前端 401 时跳这里）。 */
    public String loginUrl() {
        return ssoProperties == null ? null : ssoProperties.getLoginUrl();
    }

    /** 审计流水里的操作人：登录身份优先，回退 X-Operator 头（再回退 anonymous）。 */
    public String operatorOf(HttpServletRequest request) {
        if (!enabled()) {
            return operatorResolver.resolve(request);
        }
        try {
            DmsCurrentUser user = currentUser(request);
            if (user.isAuthenticated()) {
                return truncate(user.display());
            }
        } catch (DmsUnauthenticatedException e) {
            log.warn("[DMS-GATE] 操作人取登录身份失败（记 {}）: {}", OperatorResolver.ANONYMOUS,
                    e.getMessage());
        }
        return operatorResolver.resolve(request);
    }

    // ---------------------------------------------------------- 授权数据（403）

    /**
     * 权限闸：管控关闭 ⇒ 放行；开启 ⇒ 用户必须持有任一权限点（或全局管理员）。
     *
     * @throws DmsPermissionDeniedException 缺权限 / 判定不出来（降级）
     */
    public void require(DmsCurrentUser user, DmsPermissionPoint... points) {
        if (!enabled()) {
            return;
        }
        if (user == null || !user.isAuthenticated() || user.getUserId() == null) {
            throw new DmsPermissionDeniedException("未登录，无法判定权限");
        }
        Set<String> codes = codesOf(user);
        if (codes == null) {
            throw new DmsPermissionDeniedException(
                    "权限服务暂时不可用，已按无权限处理（fail-closed；稍后重试）", true);
        }
        if (codes.contains(DmsPermissionPoint.DMS_GLOBAL_ADMIN.getCode())) {
            return;
        }
        if (points != null) {
            for (DmsPermissionPoint p : points) {
                if (p != null && codes.contains(p.getCode())) {
                    return;
                }
            }
        }
        List<String> wanted = new ArrayList<>();
        if (points != null) {
            for (DmsPermissionPoint p : points) {
                if (p != null) {
                    wanted.add(p.getCode());
                }
            }
        }
        throw new DmsPermissionDeniedException(
                "无权限操作，需要权限点: " + String.join(" / ", wanted.isEmpty() ? List.of("(未指定)") : wanted)
                        + "（请在 4A 中心申请，app_code=" + props.getAppCode() + "）");
    }

    /**
     * 用户的有效权限码（已按 appCode 过滤）。
     *
     * @return 权限码集合；<b>z-ctc 不可达时返回 null（判定不出来）</b>，且不写缓存
     */
    public Set<String> codesOf(DmsCurrentUser user) {
        if (!enabled() || user == null || user.getUserId() == null) {
            return null;
        }
        CacheEntry entry = cache.get(user.getUserId());
        if (entry != null && System.currentTimeMillis() < entry.expireAt) {
            return entry.codes;
        }
        List<ZCtcResource> permissions;
        try {
            permissions = ctcClient.listUserPermissions(user.getToken(), user.getUserId());
        } catch (RuntimeException e) {
            // 游离态用户（0 membership）/ 4A 不可达 / 401 都会走到这：按「判定不出来」处理。
            log.warn("[DMS-AUTHZ] 权限服务不可用 ⇒ 按无权限处理（不缓存降级结果）: userId={}, ex={}",
                    user.getUserId(), summarize(e));
            return null;
        }
        Set<String> codes = new LinkedHashSet<>();
        for (ZCtcResource r : permissions == null ? Collections.<ZCtcResource>emptyList() : permissions) {
            if (r == null || r.getResourceCode() == null) {
                continue;
            }
            if (props.getAppCode().equals(r.getAppCode())) {
                codes.add(r.getResourceCode());
            }
        }
        cache.put(user.getUserId(), new CacheEntry(codes, System.currentTimeMillis() + props.getCacheTtlMs()));
        return codes;
    }

    /** 权限码缓存作废（管理动作之后可主动清）。 */
    public void evict(Long userId) {
        if (userId != null) {
            cache.remove(userId);
        }
    }

    public void evictAll() {
        cache.clear();
    }

    public int cacheSize() {
        return cache.size();
    }

    // ---------------------------------------------------------- 内部工具

    private List<String> effectiveCookieNames() {
        if (ssoProperties != null) {
            List<String> names = ssoProperties.getEffectiveTokenCookieNames();
            if (names != null && !names.isEmpty()) {
                return names;
            }
        }
        return java.util.Arrays.asList(DmsTokenExtractor.DEFAULT_COOKIE_NAMES);
    }

    private static String truncate(String v) {
        if (v == null) {
            return OperatorResolver.ANONYMOUS;
        }
        return v.length() > 64 ? v.substring(0, 64) : v;
    }

    private static String summarize(Throwable t) {
        String msg = t.getMessage();
        return t.getClass().getSimpleName() + (msg == null ? "" : (": " + msg));
    }

    private static final class CacheEntry {
        final Set<String> codes;
        final long expireAt;

        CacheEntry(Set<String> codes, long expireAt) {
            this.codes = codes;
            this.expireAt = expireAt;
        }
    }
}
