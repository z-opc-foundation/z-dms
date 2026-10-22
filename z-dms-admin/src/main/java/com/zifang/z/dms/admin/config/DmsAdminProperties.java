package com.zifang.z.dms.admin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 形态 B 宿主自己的配置面（前缀 {@code z.dms.admin}）。
 *
 * <p><b>配置面里没有任何口令字段</b>：凭据只以 {@code credentialRef}（z-mist 键名）出现。
 * 被管数据源的口令由 z-mist 提供；元数据库自身的口令走 {@code z.base.db.dms.password}
 * （环境变量注入，仓内不落明文）。
 */
@ConfigurationProperties(prefix = "z.dms.admin")
public class DmsAdminProperties {

    /**
     * 可供界面选择的凭据引用清单（{@code <group>/<key>}）。
     *
     * <p>界面上的凭据面<b>只有这一个下拉选择器</b>，没有口令输入框 ——
     * 口令永远不进浏览器。选择器的候选来自这份配置（由运维维护，与 z-mist 里的键一一对应）。
     */
    private List<String> credentialRefs = new ArrayList<>();

    /** 审计流水单次查询的最大条数（防止一次拉爆）。 */
    private int auditMaxLimit = 200;

    /** 操作人取自哪个请求头（z-ctc SSO 网关会把登录用户写进这个头）。 */
    private String operatorHeader = "X-Operator";

    public List<String> getCredentialRefs() {
        return credentialRefs;
    }

    public void setCredentialRefs(List<String> credentialRefs) {
        this.credentialRefs = credentialRefs == null ? new ArrayList<String>() : credentialRefs;
    }

    public int getAuditMaxLimit() {
        return auditMaxLimit;
    }

    public void setAuditMaxLimit(int auditMaxLimit) {
        this.auditMaxLimit = auditMaxLimit;
    }

    public String getOperatorHeader() {
        return operatorHeader;
    }

    public void setOperatorHeader(String operatorHeader) {
        this.operatorHeader = operatorHeader;
    }
}
