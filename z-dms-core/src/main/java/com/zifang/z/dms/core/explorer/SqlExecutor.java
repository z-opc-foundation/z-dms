package com.zifang.z.dms.core.explorer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * SQL 控制台执行器。
 *
 * <p><b>护栏</b>（全部服务端强制，不信任前端）：
 * <ul>
 *   <li>语句先过 {@link SqlStatementGuard}（只读闸 / 多语句闸 / 长度闸）；</li>
 *   <li>{@code setQueryTimeout} —— 跑飞的长查询到点被驱动取消；</li>
 *   <li>{@code setMaxRows(maxRows + 1)} —— 驱动层截流，结果集读不到上限之外，
 *       多读的那 1 行用来判定「被截断」并如实告知前端。</li>
 * </ul>
 */
public class SqlExecutor {

    private static final Logger log = LoggerFactory.getLogger(SqlExecutor.class);

    private final DmsConnectionProvider connectionProvider;
    private final SqlStatementGuard guard;
    /** 查询超时（秒）。 */
    private int timeoutSeconds = 30;

    public SqlExecutor(DmsConnectionProvider connectionProvider, SqlStatementGuard guard) {
        this.connectionProvider = connectionProvider;
        this.guard = guard;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public SqlStatementGuard guard() {
        return guard;
    }

    /**
     * 执行一条语句（闸按构造时的 readOnly 口径裁决）。
     *
     * @param requestedMaxRows 前端请求的行数上限；null / 非法 / 大于服务端上限时按服务端上限
     */
    public SqlExecutionResult execute(String sourceId, String sql, Integer requestedMaxRows) {
        return execute(sourceId, sql, requestedMaxRows, !guard.isReadOnly());
    }

    /**
     * 执行一条语句（调用方决定写闸：SQL 控制台的写通道在
     * 「服务端写开关 + DMS_SQL_WRITE 权限点」两道都过了才允许 allowWrite=true）。
     */
    public SqlExecutionResult execute(String sourceId, String sql, Integer requestedMaxRows,
                                      boolean allowWrite) {
        String stripped = guard.check(sql, allowWrite);
        int serverMax = Math.max(1, guard.getMaxRows());
        int effectiveMax = requestedMaxRows == null || requestedMaxRows <= 0
                ? serverMax : Math.min(requestedMaxRows, serverMax);

        SqlExecutionResult out = new SqlExecutionResult();
        out.setSourceId(sourceId);
        out.setDigest(SqlStatementGuard.digest(stripped, 300));
        long startNs = System.nanoTime();
        try (Connection conn = connectionProvider.acquire(sourceId);
             Statement st = conn.createStatement()) {
            st.setQueryTimeout(timeoutSeconds);
            st.setMaxRows(effectiveMax + 1);
            // ⚠ 执行的必须是**原始语句**：stripped 只是闸分析用的替身（字面量被换成了 ?），
            //   拿去执行会把 'hello' 变成裸 ? 直接报语法错误。
            boolean hasResultSet = st.execute(sql);
            if (hasResultSet) {
                out.setHasResultSet(true);
                out.setUpdateCount(-1);
                try (ResultSet rs = st.getResultSet()) {
                    ResultSetMetaData meta = rs.getMetaData();
                    int columnCount = meta.getColumnCount();
                    for (int i = 1; i <= columnCount; i++) {
                        String label = meta.getColumnLabel(i);
                        out.getColumns().add(label == null ? meta.getColumnName(i) : label);
                    }
                    int fetched = 0;
                    while (rs.next()) {
                        if (fetched >= effectiveMax) {
                            out.setTruncated(true);
                            break;
                        }
                        List<String> row = new ArrayList<>(columnCount);
                        for (int i = 1; i <= columnCount; i++) {
                            row.add(TableDataReader.displayValue(rs.getObject(i)));
                        }
                        out.getRows().add(row);
                        fetched++;
                    }
                }
            } else {
                out.setHasResultSet(false);
                long update = st.getUpdateCount();
                out.setUpdateCount(update < 0 ? 0 : update);
            }
        } catch (SQLTimeoutException e) {
            long latencyMs = (System.nanoTime() - startNs) / 1_000_000L;
            throw new IllegalStateException("查询超时（上限 " + timeoutSeconds + "s），已由服务端取消。"
                    + "如需更大的超时请联系管理员调整 z.dms.console.timeout-seconds。", e);
        } catch (SQLException e) {
            throw new IllegalStateException("执行失败: " + SchemaExplorer.rootMessage(e), e);
        }
        out.setLatencyMs((System.nanoTime() - startNs) / 1_000_000L);
        log.info("z-dms SQL 控制台执行完成：source={} digest={} rows={} updateCount={} latency={}ms",
                sourceId, out.getDigest(), out.getRows().size(), out.getUpdateCount(), out.getLatencyMs());
        return out;
    }
}
