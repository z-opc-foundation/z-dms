package com.zifang.z.dms.demo;

import com.zifang.z.dms.core.model.DataSourceDef;
import com.zifang.z.dms.core.model.DataSourceHealthReport;
import com.zifang.z.dms.core.pool.DataSourcePool;
import com.zifang.z.dms.core.service.DataSourceService;
import com.zifang.z.dms.core.service.RegistrationResult;
import com.zifang.z.dms.starter.health.DmsHealthView;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 宿主侧的数据源操作面（演示用，不是 z-dms 的产品接口 —— 形态 B 的 web 管控是 TASK-012）。
 */
@RestController
public class DemoDataSourceController {

    private final DataSourceService dataSourceService;

    @Autowired
    public DemoDataSourceController(DataSourceService dataSourceService) {
        this.dataSourceService = dataSourceService;
    }

    /** 登记一个数据源（注册即探活，探活结论随结果返回）。 */
    @PostMapping("/demo/datasources")
    public Map<String, Object> register(@RequestBody DataSourceDef def) {
        RegistrationResult result = dataSourceService.register(def);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("definition", result.getDefinition());
        out.put("probe", result.getProbe());
        out.put("replaced", result.isReplaced());
        out.put("up", result.isUp());
        return out;
    }

    @GetMapping("/demo/datasources")
    public List<DataSourceDef> list() {
        return dataSourceService.list();
    }

    @DeleteMapping("/demo/datasources/{id}")
    public Map<String, Object> remove(@PathVariable String id) {
        dataSourceService.remove(id);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("removed", id);
        return out;
    }

    /**
     * <b>宿主内取连接执行查询</b>：从 z-dms 登记的池借连接，直接跑一条 SELECT。
     * 只放行 SELECT（演示面不做写操作闸）。
     */
    @GetMapping("/demo/datasources/{id}/query")
    public Map<String, Object> query(@PathVariable String id, @RequestParam("sql") String sql) {
        if (!sql.trim().toLowerCase().startsWith("select")) {
            throw new IllegalArgumentException("演示接口只放行 SELECT 语句");
        }
        DataSourcePool pool = dataSourceService.require(id);
        List<Map<String, Object>> rows = new ArrayList<>();
        try (Connection conn = pool.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            ResultSetMetaData md = rs.getMetaData();
            int cols = md.getColumnCount();
            int count = 0;
            while (rs.next() && count < 20) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 1; i <= cols; i++) {
                    row.put(md.getColumnLabel(i), rs.getObject(i));
                }
                rows.add(row);
                count++;
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("id", id);
            out.put("sql", sql);
            out.put("rowCount", rows.size());
            out.put("rows", rows);
            return out;
        } catch (Exception e) {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("id", id);
            out.put("sql", sql);
            out.put("error", e.getClass().getSimpleName() + ": " + e.getMessage());
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    /**
     * 宿主自己的健康端点：并上 z-dms 的健康报告。
     * <b>探活失败在这里看得见</b>（status=DOWN + 每个源的 detail）。
     */
    @GetMapping("/health")
    public Map<String, Object> health() {
        DataSourceHealthReport report = dataSourceService.health();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", report.getStatus().name());
        out.put("dms", DmsHealthView.toMap(report));
        return out;
    }
}
