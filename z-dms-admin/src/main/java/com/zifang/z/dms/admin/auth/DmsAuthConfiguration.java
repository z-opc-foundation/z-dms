package com.zifang.z.dms.admin.auth;

import com.zifang.ctc.client.ZCtcClient;
import com.zifang.ctc.client.ZCtcClientFactory;
import com.zifang.ctc.sso.config.SsoProperties;
import com.zifang.ctc.sso.config.TokenService;
import com.zifang.z.dms.admin.audit.OperatorResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * z-dms 权限管控装配（<b>默认开启</b>：{@code matchIfMissing = true}）。
 *
 * <p><b>关闭方式</b>：{@code z.dms.auth.enabled=false} ⇒ 本配置类整体消失，
 * 闸门、权限判定全部退场（退回「X-Operator 头记操作人」的原始口径）。
 *
 * <p><b>开启时的两条 z-ctc 链路</b>（故障域分开，都是官方 SDK）：
 * <ul>
 *   <li>登录态：z-ctc-sso 的 {@link TokenService}（{@code sso.ctc-base-url} 指向 4A 中心 ⇒
 *       远端验签，不查 4A 的库）。官方 {@code SsoInterceptor} 通过
 *       {@code sso.intercept-paths: []} 置空 —— 本闸门（{@code /api/dms/**}）是唯一真源，
 *       两把刀收成一把，{@code z.dms.auth.enabled} 才能真正一把关掉；</li>
 *   <li>授权数据：z-ctc-client 的 {@link ZCtcClient}（按 {@code app_code=z-dms} 拉权限点）。</li>
 * </ul>
 *
 * <p><b>fail-closed</b>：开启但 z-ctc 地址缺失 / 验签通道不可用 ⇒ 拒绝启动或按无权限处理，
 * 绝不静默放行 —— 权限管控「看起来开着实际没开」比明确拒绝危险得多。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "z.dms.auth", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(DmsAuthProperties.class)
public class DmsAuthConfiguration implements WebMvcConfigurer {

    private static final Logger log = LoggerFactory.getLogger(DmsAuthConfiguration.class);

    /** 延迟取闸门 bean（addInterceptors 的调用时机早于普通 @Bean 方法，不能存字段）。 */
    private final ObjectProvider<DmsPermissionService> permissionServiceProvider;

    public DmsAuthConfiguration(ObjectProvider<DmsPermissionService> permissionServiceProvider) {
        this.permissionServiceProvider = permissionServiceProvider;
    }

    @Bean
    @ConditionalOnMissingBean
    public ZCtcClient dmsCtcClient(DmsAuthProperties props) {
        if (props.getCtcBaseUrl() == null || props.getCtcBaseUrl().trim().isEmpty()) {
            throw new IllegalStateException(
                    "z.dms.auth.enabled=true（默认开）但 z.dms.auth.ctc-base-url 为空 —— "
                            + "请把它指向同集群的 z-ctc（4A 中心，如 "
                            + "http://z-ctc-admin.z-ctc.svc.cluster.local:8888）；"
                            + "若确要无 4A 孤立运行，把 z.dms.auth.enabled 设为 false 关闭权限管控。");
        }
        log.info("z-dms 权限管控开启：appCode={} ctcBaseUrl={}", props.getAppCode(),
                props.getCtcBaseUrl());
        return ZCtcClientFactory.remote(props.getCtcBaseUrl().trim());
    }

    @Bean
    public DmsPermissionService dmsPermissionService(DmsAuthProperties props,
                                                     ZCtcClient dmsCtcClient,
                                                     ObjectProvider<TokenService> tokenServiceProvider,
                                                     ObjectProvider<SsoProperties> ssoPropertiesProvider,
                                                     OperatorResolver operatorResolver) {
        TokenService tokenService = tokenServiceProvider.getIfAvailable();
        if (tokenService == null) {
            throw new IllegalStateException(
                    "z.dms.auth.enabled=true 但 classpath 里没有 z-ctc-sso（登录态链路缺失）—— "
                            + "请引入 io.github.yuku123:z-ctc-sso 并配置 sso.ctc-base-url。");
        }
        SsoProperties ssoProperties = ssoPropertiesProvider.getIfAvailable();
        if (ssoProperties == null || !ssoProperties.isRemoteCtc()) {
            log.warn("z-dms 登录态建议走远端 4A 中心验签（sso.ctc-base-url）；"
                    + "当前未配置远端 ⇒ 本进程本地验签（需要 sso.jwt-secret，缺失会在首个请求上失败）。");
        }
        return new DmsPermissionService(props, tokenService, ssoProperties, dmsCtcClient, operatorResolver);
    }

    /**
     * 登录态闸门（401）：只判「你是谁」，不判权限点 —— 权限点在 Controller 里按端点判
     * （消费方的 URL↔权限点映射只有业务自己知道，z-ctc 把资源级授权刻意留给业务侧）。
     *
     * <p>拒绝必须留痕（沿用 z-ctc SSO-GATE 的纪律）：URL + 客户端 IP + 拒绝原因，
     * <b>不记 token 值</b>。
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                                     Object handler) throws Exception {
                try {
                    DmsPermissionService service = permissionServiceProvider.getObject();
                    service.currentUser(request); // 结论进 request attribute，Controller 复用
                    return true;
                } catch (DmsUnauthenticatedException e) {
                    log.warn("[DMS-GATE] 拒绝: url={}, method={}, clientIp={}, reason={}",
                            request.getRequestURI(), request.getMethod(),
                            clientIp(request), e.getMessage());
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json;charset=UTF-8");
                    String msg = e.getMessage() == null ? "未登录或Token无效" : e.getMessage();
                    response.getWriter().write("{\"code\":401,\"message\":\""
                            + jsonEscape(msg) + "\",\"data\":null}");
                    return false;
                }
            }
        }).addPathPatterns("/api/dms/**").excludePathPatterns("/api/dms/auth/**");
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.trim().isEmpty()) {
            int comma = forwarded.indexOf(',');
            return (comma > 0 ? forwarded.substring(0, comma) : forwarded).trim();
        }
        return request.getRemoteAddr();
    }

    private static String jsonEscape(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }
}
