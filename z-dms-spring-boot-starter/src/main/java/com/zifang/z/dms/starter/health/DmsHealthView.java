package com.zifang.z.dms.starter.health;

import com.zifang.z.dms.core.model.DataSourceHealthReport;
import com.zifang.z.dms.core.model.DataSourceProbeResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 健康报告 → 视图模型（HTTP 端点与 actuator 贡献共用一份形状）。
 */
public final class DmsHealthView {

    private DmsHealthView() {
    }

    public static Map<String, Object> toMap(DataSourceHealthReport report) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", report.getStatus().name());
        out.put("generatedAt", report.getGeneratedAt());
        List<Map<String, Object>> sources = new ArrayList<>();
        for (DataSourceProbeResult r : report.getSources()) {
            Map<String, Object> one = new LinkedHashMap<>();
            one.put("id", r.getId());
            one.put("name", r.getName());
            one.put("status", r.getHealth().name());
            one.put("detail", r.getDetail());
            one.put("latencyMs", r.getLatencyMs());
            one.put("checkedAt", r.getCheckedAt());
            sources.add(one);
        }
        out.put("sources", sources);
        if (report.getReason() != null) {
            out.put("reason", report.getReason());
        }
        return out;
    }
}
