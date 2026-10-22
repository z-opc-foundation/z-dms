package com.zifang.z.dms.admin.web.dto;

/**
 * 测连接结论（真实发起连接后的回执）。
 *
 * <p>{@code up=false} 时 {@code detail} 带根因（Connection refused / Access denied …），
 * 不是一句「失败」。
 */
public class ProbeView {

    private String id;
    private String name;
    private boolean up;
    private String health;
    private String detail;
    private Long latencyMs;
    private Long checkedAt;
    /** 本次测连接的目标（host:port/database 或 jdbc-url 的前段），便于核对「测的是哪个」。 */
    private String target;

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

    public boolean isUp() {
        return up;
    }

    public void setUp(boolean up) {
        this.up = up;
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

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
    }
}
