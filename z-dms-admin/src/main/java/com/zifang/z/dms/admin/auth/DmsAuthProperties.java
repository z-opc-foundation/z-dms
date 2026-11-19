package com.zifang.z.dms.admin.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * z-dms 权限管控配置面（前缀 {@code z.dms.auth}）。
 *
 * <p><b>默认开启</b>（{@link #enabled} 缺省 true）：管控台不是谁都能进的 ——
 * 登录态走同集群 z-ctc（4A 中心）的 JWT 验签，权限点走 z-ctc 的授权数据
 * （{@code app_code = z-dms} 的资源码）。
 *
 * <p><b>可关闭</b>：{@code z.dms.auth.enabled=false} ⇒ 整个 auth 包不装配
 * （闸门、权限判定全部消失，退回「X-Operator 头记操作人」的原始口径）。
 * 关闭只该出现在本地自检 / 无 4A 的孤立环境 —— 审计流水里会因此看不到登录身份。
 */
@ConfigurationProperties(prefix = "z.dms.auth")
public class DmsAuthProperties {

    /** 权限管控总开关（默认开）。 */
    private boolean enabled = true;

    /**
     * z-ctc（4A 中心）地址。开启权限管控时必填；指向同集群的
     * {@code http://z-ctc-admin.z-ctc.svc.cluster.local:8888} 或网关地址。
     */
    private String ctcBaseUrl = "";

    /** 本应用在 z-ctc 资源表里的 appCode（权限点按它隔离）。 */
    private String appCode = "z-dms";

    /** 权限码本地缓存 TTL（毫秒）。授权变更最迟这么久生效。 */
    private long cacheTtlMs = 30000L;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getCtcBaseUrl() {
        return ctcBaseUrl;
    }

    public void setCtcBaseUrl(String ctcBaseUrl) {
        this.ctcBaseUrl = ctcBaseUrl;
    }

    public String getAppCode() {
        return appCode;
    }

    public void setAppCode(String appCode) {
        this.appCode = appCode;
    }

    public long getCacheTtlMs() {
        return cacheTtlMs;
    }

    public void setCacheTtlMs(long cacheTtlMs) {
        this.cacheTtlMs = cacheTtlMs;
    }
}
