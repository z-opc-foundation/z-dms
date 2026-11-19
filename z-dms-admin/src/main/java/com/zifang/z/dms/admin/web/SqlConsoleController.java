package com.zifang.z.dms.admin.web;

import com.zifang.z.dms.admin.auth.DmsCurrentUser;
import com.zifang.z.dms.admin.auth.DmsPermissionDeniedException;
import com.zifang.z.dms.admin.auth.DmsPermissionPoint;
import com.zifang.z.dms.admin.auth.DmsPermissionService;
import com.zifang.z.dms.admin.console.DmsConsoleProperties;
import com.zifang.z.dms.admin.store.JdbcSqlHistoryStore;
import com.zifang.z.dms.admin.store.JdbcSqlHistoryStore.SqlHistoryRecord;
import com.zifang.z.dms.admin.web.dto.ApiResult;
import com.zifang.z.dms.core.explorer.SqlExecutionResult;
import com.zifang.z.dms.core.explorer.SqlExecutor;
import com.zifang.z.dms.core.explorer.SqlStatementGuard;
import com.zifang.z.dms.core.service.DataSourceService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * SQL 控制台（真执行，不是回显）。
 *
 * <p><b>权限分离</b>：
 * <ul>
 *   <li>查询语句 → {@code DMS_SQL_EXECUTE}；</li>
 *   <li>写语句 → {@code DMS_SQL_EXECUTE} + {@code DMS_SQL_WRITE}，
 *       且服务端 {@code z.dms.console.write-enabled=true}（默认关）——
 *       两道闸少一道都过不去。</li>
 * </ul>
 *
 * <p><b>审计双落</b>：每次执行都写使用流水（z_dms_datasource_usage_log，digest 摘要）
 * 与执行历史（z_dms_sql_history，原文 + 结论），失败也留痕（根因 + 耗时）。
 */
@RestController
@RequestMapping("/api/dms/console")
@ConditionalOnProperty(prefix = "z.dms", name = "store-enabled", havingValue = "true")
public class SqlConsoleController {

    private final DataSourceService dataSourceService;
    private final SqlExecutor sqlExecutor;
    private final SqlStatementGuard guard;
    private final DmsConsoleProperties consoleProps;
    private final DmsPermissionService permissionService;
    private final JdbcSqlHistoryStore historyStore;

    public SqlConsoleController(DataSourceService dataSourceService,
                                SqlExecutor sqlExecutor,
                                SqlStatementGuard guard,
                                DmsConsoleProperties consoleProps,
                                DmsPermissionService permissionService,
                                JdbcSqlHistoryStore historyStore) {
        this.dataSourceService = dataSourceService;
        this.sqlExecutor = sqlExecutor;
        this.guard = guard;
        this.consoleProps = consoleProps;
        this.permissionService = permissionService;
        this.historyStore = historyStore;
    }

    /** 控制台能力位（前端决定编辑器上方提示与按钮显隐）。 */
    @GetMapping("/capabilities")
    public ApiResult<Map<String, Object>> capabilities(HttpServletRequest request) {
        permissionService.currentUser(request);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("writeEnabled", consoleProps.isWriteEnabled());
        out.put("maxRows", consoleProps.getMaxRows());
        out.put("maxSqlLength", consoleProps.getMaxSqlLength());
        out.put("timeoutSeconds", consoleProps.getTimeoutSeconds());
        return ApiResult.ok(out);
    }

    /**
     * 执行一条 SQL（单语句；查询/写权限分离见类注释）。
     */
    @PostMapping("/execute")
    public ApiResult<Map<String, Object>> execute(@RequestBody ExecuteRequest body,
                                                  HttpServletRequest request) {
        DmsCurrentUser user = permissionService.currentUser(request);
        permissionService.require(user, DmsPermissionPoint.DMS_SQL_EXECUTE);
        if (dataSourceService.find(body.getSourceId()) == null) {
            throw new IllegalArgumentException("数据源未登记: " + body.getSourceId());
        }
        SqlStatementGuard.Kind kind = guard.classify(guard.check(body.getSql(), true));
        boolean allowWrite = kind == SqlStatementGuard.Kind.WRITE;
        if (allowWrite) {
            if (!consoleProps.isWriteEnabled()) {
                throw new DmsPermissionDeniedException(
                        "服务端未开启写执行（z.dms.console.write-enabled=false）");
            }
            permissionService.require(user, DmsPermissionPoint.DMS_SQL_WRITE);
        }
        String operator = permissionService.operatorOf(request);
        SqlExecutionResult result;
        try {
            result = sqlExecutor.execute(body.getSourceId(), body.getSql(), body.getMaxRows(), allowWrite);
            record(body, kind.name(), operator, true, result, null);
        } catch (RuntimeException e) {
            record(body, kind.name(), operator, false, null,
                    e.getClass().getSimpleName() + ": " + e.getMessage());
            throw e;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("sourceId", result.getSourceId());
        out.put("digest", result.getDigest());
        out.put("columns", result.getColumns());
        out.put("rows", result.getRows());
        out.put("hasResultSet", result.isHasResultSet());
        out.put("updateCount", result.getUpdateCount());
        out.put("truncated", result.isTruncated());
        out.put("latencyMs", result.getLatencyMs());
        out.put("statementKind", kind.name());
        return ApiResult.ok(out);
    }

    /** 执行历史（谁的、何时、执行了什么、成败与耗时）。 */
    @GetMapping("/history")
    public ApiResult<?> history(@RequestParam(value = "datasourceId", required = false) String datasourceId,
                                @RequestParam(value = "limit", defaultValue = "50") int limit,
                                HttpServletRequest request) {
        permissionService.require(permissionService.currentUser(request),
                DmsPermissionPoint.DMS_AUDIT_VIEW);
        return ApiResult.ok(historyStore.list(datasourceId, Math.min(Math.max(limit, 1), 200)));
    }

    private void record(ExecuteRequest body, String kindName, String operator, boolean success,
                        SqlExecutionResult result, String error) {
        SqlHistoryRecord r = new SqlHistoryRecord();
        r.setDatasourceId(body.getSourceId());
        r.setOperator(operator);
        r.setSqlDigest(SqlStatementGuard.digest(body.getSql(), 300));
        r.setSqlText(body.getSql());
        r.setStatementKind(kindName);
        r.setSuccess(success);
        if (result != null) {
            r.setRowCount(result.isHasResultSet() ? result.getRows().size() : result.getUpdateCount());
            r.setLatencyMs(result.getLatencyMs());
        } else {
            r.setRowCount(0);
        }
        r.setErrorMessage(error == null ? null : trim(error));
        r.setExecutedAt(System.currentTimeMillis());
        historyStore.save(r);
    }

    private static String trim(String v) {
        if (v == null) {
            return null;
        }
        String flat = v.replace('\n', ' ').replace('\r', ' ').trim();
        return flat.length() <= 1000 ? flat : flat.substring(0, 1000);
    }

    /** 执行请求体。 */
    public static class ExecuteRequest {
        private String sourceId;
        private String sql;
        private Integer maxRows;

        public String getSourceId() {
            return sourceId;
        }

        public void setSourceId(String sourceId) {
            this.sourceId = sourceId;
        }

        public String getSql() {
            return sql;
        }

        public void setSql(String sql) {
            this.sql = sql;
        }

        public Integer getMaxRows() {
            return maxRows;
        }

        public void setMaxRows(Integer maxRows) {
            this.maxRows = maxRows;
        }
    }
}
