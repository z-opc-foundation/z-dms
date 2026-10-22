package com.zifang.z.dms.starter.health;

import com.zifang.z.dms.core.model.DataSourceHealth;
import com.zifang.z.dms.core.model.DataSourceHealthReport;
import com.zifang.z.dms.core.model.DataSourceProbeResult;
import com.zifang.z.dms.core.service.DataSourceService;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * actuator 健康贡献：z-dms 有池 DOWN ⇒ 整体 {@code DOWN}（actuator 会回 503）。
 * <p>
 * 这一条是为了让「探活失败」在<b>宿主的标准健康闸</b>上可见 ——
 * 部署闸只看健康检查，它说 UP 而库连不上，闸就变成谎话的放大器（z-lc 源码注释原话）。
 */
public class DmsHealthIndicator implements HealthIndicator {

    private final DataSourceService dataSourceService;

    public DmsHealthIndicator(DataSourceService dataSourceService) {
        this.dataSourceService = dataSourceService;
    }

    @Override
    public Health health() {
        DataSourceHealthReport report = dataSourceService.health();
        Map<String, Object> details = new LinkedHashMap<>();
        Map<String, Object> sources = new LinkedHashMap<>();
        for (DataSourceProbeResult r : report.getSources()) {
            Map<String, Object> one = new LinkedHashMap<>();
            one.put("status", r.getHealth().name());
            one.put("detail", r.getDetail());
            one.put("latencyMs", r.getLatencyMs());
            sources.put(r.getId(), one);
        }
        details.put("sources", sources);
        if (report.getReason() != null) {
            details.put("reason", report.getReason());
        }
        return report.getStatus() == DataSourceHealth.UP
                ? Health.up().withDetails(details).build()
                : Health.down().withDetails(details).build();
    }
}
