package com.zifang.z.dms.admin.web;

import com.zifang.z.dms.admin.audit.AuditTrailService;
import com.zifang.z.dms.admin.audit.OperatorResolver;
import com.zifang.z.dms.admin.web.dto.ApiResult;
import com.zifang.z.dms.admin.web.dto.DataSourceForm;
import com.zifang.z.dms.admin.web.dto.ProbeView;
import com.zifang.z.dms.admin.web.dto.SourceView;
import com.zifang.z.dms.core.model.DataSourceDef;
import com.zifang.z.dms.core.model.DataSourceHealthReport;
import com.zifang.z.dms.core.model.DataSourceProbeResult;
import com.zifang.z.dms.core.model.DataSourceType;
import com.zifang.z.dms.core.pool.DataSourcePool;
import com.zifang.z.dms.core.pool.DataSourcePoolFactory;
import com.zifang.z.dms.core.health.DataSourceHealthProber;
import com.zifang.z.dms.core.service.DataSourceService;
import com.zifang.z.dms.core.service.RegistrationResult;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 形态 B 的数据源管控面（登记 / 编辑 / 摘除 / 测连接 / 取连接）。
 *
 * <p>底座全部复用形态 A 的 {@link DataSourceService} —— <b>不是两份代码</b>：
 * 建池、探活、换绑关旧池、落库这些动作都走 core 里的那套。
 *
 * <p><b>「测连接」是真发起连接</b>：
 * {@code POST /{id}/test-connection} 走池借连接跑 {@code SELECT 1}；
 * {@code POST /test-connection}（未登记的候选）现场建一个临时池、探完立刻关掉。
 * 两者都不是回固定值，结论里有真实耗时与根因。
 */
@RestController
@RequestMapping("/api/dms/sources")
public class DataSourceAdminController {

    private static final String ACTION_TEST = "TEST_CONNECTION";
    private static final String ACTION_ACQUIRE = "ACQUIRE_CONNECTION";

    private final DataSourceService dataSourceService;
    private final DataSourcePoolFactory poolFactory;
    private final DataSourceHealthProber prober;
    private final AuditTrailService auditTrailService;
    private final OperatorResolver operatorResolver;

    public DataSourceAdminController(DataSourceService dataSourceService,
                                     DataSourcePoolFactory poolFactory,
                                     DataSourceHealthProber prober,
                                     AuditTrailService auditTrailService,
                                     OperatorResolver operatorResolver) {
        this.dataSourceService = dataSourceService;
        this.poolFactory = poolFactory;
        this.prober = prober;
        this.auditTrailService = auditTrailService;
        this.operatorResolver = operatorResolver;
    }

    /**
     * 一屏看到全部登记数据源与各自健康态。
     *
     * <p>健康态是<b>当场探出来的</b>（逐池真借一次连接跑 SELECT 1），不是缓存里的旧结论。
     */
    @GetMapping
    public ApiResult<List<SourceView>> list() {
        DataSourceHealthReport report = dataSourceService.health();
        Map<String, DataSourceProbeResult> probes = new LinkedHashMap<>();
        for (DataSourceProbeResult p : report.getSources()) {
            probes.put(p.getId(), p);
        }
        List<SourceView> out = new ArrayList<>();
        for (DataSourceDef def : dataSourceService.list()) {
            out.add(toView(def, probes.get(def.getId())));
        }
        return ApiResult.ok(out);
    }

    /** 登记一个数据源（注册即探活，探活结论随结果返回）。 */
    @PostMapping
    public ApiResult<SourceView> register(@RequestBody DataSourceForm form, HttpServletRequest request) {
        DataSourceDef def = toDef(form);
        DataSourceDef before = dataSourceService.find(def.getId());
        RegistrationResult result = dataSourceService.register(def);
        String operator = operatorResolver.resolve(request);
        auditTrailService.recordChange(def.getId(), before == null ? "REGISTERED" : "UPDATED",
                before, def, operator,
                before == null ? "登记数据源" : "重新登记数据源（换绑）");
        return ApiResult.ok(toView(def, result.getProbe()));
    }

    /** 更新一个已登记的数据源（换绑：旧池显式关闭，新池重新探活）。 */
    @PutMapping("/{id}")
    public ApiResult<SourceView> update(@PathVariable String id, @RequestBody DataSourceForm form,
                                        HttpServletRequest request) {
        DataSourceDef def = toDef(form);
        def.setId(id);
        DataSourceDef before = dataSourceService.find(id);
        if (before == null) {
            throw new IllegalArgumentException("数据源未登记，无法更新: " + id);
        }
        RegistrationResult result = dataSourceService.update(def);
        String operator = operatorResolver.resolve(request);
        auditTrailService.recordChange(id, "UPDATED", before, def, operator, "修改数据源定义");
        return ApiResult.ok(toView(def, result.getProbe()));
    }

    /** 摘除数据源（显式关闭池）。 */
    @DeleteMapping("/{id}")
    public ApiResult<Map<String, Object>> remove(@PathVariable String id, HttpServletRequest request) {
        DataSourceDef before = dataSourceService.find(id);
        if (before == null) {
            // 摘除一个不存在的源**不能静默成功**：否则界面会以为删掉了，
            // 而变更历史里也留不下一条 REMOVED（前后值都是空的）。
            throw new IllegalArgumentException("数据源未登记，无法摘除: " + id);
        }
        dataSourceService.remove(id);
        String operator = operatorResolver.resolve(request);
        auditTrailService.recordChange(id, "REMOVED", before, null, operator, "摘除数据源");
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("removed", id);
        return ApiResult.ok(out);
    }

    /**
     * 测连接（已登记的数据源）：<b>真借一次连接跑 SELECT 1</b>。
     */
    @PostMapping("/{id}/test-connection")
    public ApiResult<ProbeView> testConnection(@PathVariable String id, HttpServletRequest request) {
        DataSourceDef def = dataSourceService.find(id);
        if (def == null) {
            throw new IllegalArgumentException("数据源未登记: " + id);
        }
        DataSourceProbeResult probe = dataSourceService.probe(id);
        String operator = operatorResolver.resolve(request);
        auditTrailService.recordUsage(id, ACTION_TEST, operator, probe.isUp(), probe.getDetail(),
                probe.isUp() ? Long.valueOf(probe.getLatencyMs()) : null);
        return ApiResult.ok(toProbeView(def, probe));
    }

    /**
     * 测连接（<b>未登记的候选</b>，登记表单里的「自测」按钮）：
     * 现场建一个临时池 → 真探一次 → 立刻关掉。
     *
     * <p>候选 id 只是流水里的标签，不会被登记进注册中心。
     */
    @PostMapping("/test-connection")
    public ApiResult<ProbeView> testCandidate(@RequestBody DataSourceForm form, HttpServletRequest request) {
        DataSourceDef def = toDef(form);
        String operator = operatorResolver.resolve(request);
        DataSourcePool pool = null;
        try {
            pool = poolFactory.create(def);
            DataSourceProbeResult probe = prober.probe(pool);
            auditTrailService.recordUsage(def.getId(), ACTION_TEST, operator, probe.isUp(), probe.getDetail(),
                    probe.isUp() ? Long.valueOf(probe.getLatencyMs()) : null);
            return ApiResult.ok(toProbeView(def, probe));
        } finally {
            if (pool != null) {
                try {
                    pool.close();
                } catch (RuntimeException ignored) {
                    // 临时池关不掉不该影响探测结论
                }
            }
        }
    }

    /**
     * 取连接（真取一次连接并跑一条 SELECT）：<b>审计流水里的「使用记录」就来自这里</b>。
     */
    @PostMapping("/{id}/acquire")
    public ApiResult<Map<String, Object>> acquire(@PathVariable String id, HttpServletRequest request) {
        DataSourceDef def = dataSourceService.find(id);
        if (def == null) {
            throw new IllegalArgumentException("数据源未登记: " + id);
        }
        String operator = operatorResolver.resolve(request);
        long startNs = System.nanoTime();
        try (Connection conn = dataSourceService.require(id).getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT 1")) {
            boolean hasRow = rs.next();
            long latencyMs = (System.nanoTime() - startNs) / 1_000_000L;
            auditTrailService.recordUsage(id, ACTION_ACQUIRE, operator, hasRow, "SELECT 1 ok",
                    Long.valueOf(latencyMs));
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("id", id);
            out.put("target", targetOf(def));
            out.put("latencyMs", latencyMs);
            out.put("rows", hasRow ? 1 : 0);
            return ApiResult.ok(out);
        } catch (Exception e) {
            long latencyMs = (System.nanoTime() - startNs) / 1_000_000L;
            String detail = e.getClass().getSimpleName() + ": " + e.getMessage();
            auditTrailService.recordUsage(id, ACTION_ACQUIRE, operator, false, detail, Long.valueOf(latencyMs));
            throw new IllegalStateException("取连接失败: " + detail, e);
        }
    }

    // ------------------------------------------------------------------ 内部工具

    private SourceView toView(DataSourceDef def, DataSourceProbeResult probe) {
        SourceView v = new SourceView();
        v.setId(def.getId());
        v.setName(def.getName());
        v.setType(def.getType() == null ? null : def.getType().name());
        v.setCredentialRef(def.getCredentialRef());
        v.setProperties(new LinkedHashMap<>(def.getProperties()));
        if (probe == null) {
            v.setHealth("UNKNOWN");
        } else {
            v.setHealth(probe.getHealth().name());
            v.setDetail(probe.getDetail());
            v.setLatencyMs(probe.isUp() ? Long.valueOf(probe.getLatencyMs()) : null);
            v.setCheckedAt(Long.valueOf(probe.getCheckedAt()));
        }
        return v;
    }

    private ProbeView toProbeView(DataSourceDef def, DataSourceProbeResult probe) {
        ProbeView v = new ProbeView();
        v.setId(def.getId());
        v.setName(def.getName());
        v.setUp(probe.isUp());
        v.setHealth(probe.getHealth().name());
        v.setDetail(probe.getDetail());
        v.setLatencyMs(probe.isUp() ? Long.valueOf(probe.getLatencyMs()) : null);
        v.setCheckedAt(Long.valueOf(probe.getCheckedAt()));
        v.setTarget(targetOf(def));
        return v;
    }

    /** 测连接的目标描述（不含任何凭据）。 */
    private String targetOf(DataSourceDef def) {
        Map<String, String> props = def.getProperties();
        if (props == null) {
            return null;
        }
        String url = props.get("jdbc-url");
        if (url == null) {
            url = props.get("url");
        }
        if (url != null && !url.trim().isEmpty()) {
            return url.trim();
        }
        String host = props.get("host");
        String database = props.get("database");
        if (host == null || host.trim().isEmpty() || database == null || database.trim().isEmpty()) {
            return null;
        }
        String port = props.get("port");
        return host.trim() + ":" + (port == null || port.trim().isEmpty() ? "3306" : port.trim())
                + "/" + database.trim();
    }

    /** 表单 → 定义。敏感键由 core 的 DataSourceDefValidator 在写入口拒掉。 */
    private DataSourceDef toDef(DataSourceForm form) {
        DataSourceType type = DataSourceType.valueOf(form.getType() == null
                ? DataSourceType.JDBC.name() : form.getType().trim().toUpperCase());
        return new DataSourceDef(form.getId(), form.getName(), type, form.getCredentialRef(),
                form.getProperties());
    }
}
