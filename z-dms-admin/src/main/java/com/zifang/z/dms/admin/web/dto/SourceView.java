package com.zifang.z.dms.admin.web.dto;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 数据源列表的一行（一屏看全登记数据源与各自健康态）。
 *
 * <p>{@code health} 取值：{@code UP} / {@code DOWN} / {@code UNKNOWN}（尚未探活过）。
 * {@code detail} 是探活结论（失败时带根因，如 Connection refused）——
 * <b>失败不静默</b>：DOWN 的源在列表上直接看得见。
 */
public class SourceView {

    private String id;
    private String name;
    private String type;
    private String credentialRef;
    private Map<String, String> properties = new LinkedHashMap<>();
    private String health = "UNKNOWN";
    private String detail;
    private Long latencyMs;
    private Long checkedAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getCredentialRef() {
        return credentialRef;
    }

    public void setCredentialRef(String credentialRef) {
        this.credentialRef = credentialRef;
    }

    public Map<String, String> getProperties() {
        return properties;
    }

    public void setProperties(Map<String, String> properties) {
        this.properties = properties == null ? new LinkedHashMap<String, String>() : properties;
    }

    public String getHealth() {
        return health;
    }

    public void setHealth(String health) {
        this.health = health;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    public Long getLatencyMs() {
        return latencyMs;
    }

    public void setLatencyMs(Long latencyMs) {
        this.latencyMs = latencyMs;
    }

    public Long getCheckedAt() {
        return checkedAt;
    }

    public void setCheckedAt(Long checkedAt) {
        this.checkedAt = checkedAt;
    }
}
