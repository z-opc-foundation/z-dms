package com.zifang.z.dms.core.model;

/**
 * 单个数据源的探活结论。
 */
public final class DataSourceProbeResult {

    private final String id;
    private final String name;
    private final DataSourceHealth health;
    private final String detail;
    private final long latencyMs;
    private final long checkedAt;

    private DataSourceProbeResult(String id, String name, DataSourceHealth health, String detail,
                                  long latencyMs, long checkedAt) {
        this.id = id;
        this.name = name;
        this.health = health;
        this.detail = detail;
        this.latencyMs = latencyMs;
        this.checkedAt = checkedAt;
    }

    public static DataSourceProbeResult up(String id, String name, String detail, long latencyMs) {
        return new DataSourceProbeResult(id, name, DataSourceHealth.UP, detail, latencyMs,
                System.currentTimeMillis());
    }

    public static DataSourceProbeResult down(String id, String name, String detail) {
        return new DataSourceProbeResult(id, name, DataSourceHealth.DOWN, detail, -1L,
                System.currentTimeMillis());
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public DataSourceHealth getHealth() {
        return health;
    }

    public String getDetail() {
        return detail;
    }

    public long getLatencyMs() {
        return latencyMs;
    }

    public long getCheckedAt() {
        return checkedAt;
    }

    public boolean isUp() {
        return health == DataSourceHealth.UP;
    }

    @Override
    public String toString() {
        return "DataSourceProbeResult{id=" + id + ", name=" + name + ", health=" + health
                + ", detail=" + detail + ", latencyMs=" + latencyMs + "}";
    }
}
