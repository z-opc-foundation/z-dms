package com.zifang.z.dms.core.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 一次整体健康报告（宿主 /health 与 actuator 消费它）。
 * <p>
 * <b>口径沿用 z-lc {@code DataSourceHealthProber#report}</b>：
 * 只有在「至少有一个池且全部池都 UP」时整体才是 UP；否则 DOWN 并给出第一个失败原因。
 * 这条口径的由来（z-lc 源码注释原文）：部署件在没有任何可用连接时照样回 200 "UP"，
 * 健康检查是部署闸唯一的判据，它说 UP 而库连不上，闸就变成谎话的放大器。
 */
public final class DataSourceHealthReport {

    private final DataSourceHealth status;
    private final List<DataSourceProbeResult> sources;
    private final String reason;
    private final long generatedAt;

    private DataSourceHealthReport(DataSourceHealth status, List<DataSourceProbeResult> sources,
                                   String reason) {
        this.status = status;
        this.sources = Collections.unmodifiableList(new ArrayList<>(sources));
        this.reason = reason;
        this.generatedAt = System.currentTimeMillis();
    }

    public static DataSourceHealthReport of(List<DataSourceProbeResult> sources) {
        boolean allUp = sources != null && !sources.isEmpty();
        String reason = null;
        if (sources != null) {
            for (DataSourceProbeResult r : sources) {
                if (!r.isUp()) {
                    allUp = false;
                    if (reason == null) {
                        reason = r.getId() + ": " + r.getDetail();
                    }
                }
            }
        }
        if (allUp && (sources == null || sources.isEmpty())) {
            reason = "上下文里没有任何 DataSource";
        }
        return new DataSourceHealthReport(allUp ? DataSourceHealth.UP : DataSourceHealth.DOWN,
                sources == null ? Collections.<DataSourceProbeResult>emptyList() : sources, reason);
    }

    public DataSourceHealth getStatus() {
        return status;
    }

    public List<DataSourceProbeResult> getSources() {
        return sources;
    }

    public String getReason() {
        return reason;
    }

    public long getGeneratedAt() {
        return generatedAt;
    }

    public boolean isUp() {
        return status == DataSourceHealth.UP;
    }

    @Override
    public String toString() {
        return "DataSourceHealthReport{status=" + status + ", sources=" + sources
                + (reason == null ? "" : ", reason=" + reason) + "}";
    }
}
