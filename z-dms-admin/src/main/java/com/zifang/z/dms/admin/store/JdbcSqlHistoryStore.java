package com.zifang.z.dms.admin.store;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * SQL 控制台执行历史的持久化（表 {@code z_dms_sql_history}，V5 DDL）。
 *
 * <p>sql_text 存的是<b>用户提交的原始语句</b>（凭据本来就不在语句里 ——
 * 走的是已登记数据源的池），digest 是闸分析用的摘要（字面量已抹）。
 */
public class JdbcSqlHistoryStore {

    public static final String TABLE = "z_dms_sql_history";

    private final DataSource dataSource;
    private final int historyLimit;

    public JdbcSqlHistoryStore(DataSource dataSource, int historyLimit) {
        this.dataSource = dataSource;
        this.historyLimit = historyLimit;
    }

    public void save(SqlHistoryRecord record) {
        String sql = "INSERT INTO " + TABLE
                + " (datasource_id, operator, sql_digest, sql_text, statement_kind, success, row_count,"
                + " latency_ms, error_message, executed_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, record.getDatasourceId());
            ps.setString(2, record.getOperator());
            ps.setString(3, record.getSqlDigest());
            ps.setString(4, record.getSqlText());
            ps.setString(5, record.getStatementKind());
            ps.setInt(6, record.isSuccess() ? 1 : 0);
            ps.setLong(7, record.getRowCount());
            if (record.getLatencyMs() == null) {
                ps.setNull(8, java.sql.Types.BIGINT);
            } else {
                ps.setLong(8, record.getLatencyMs());
            }
            ps.setString(9, record.getErrorMessage());
            ps.setTimestamp(10, new Timestamp(record.getExecutedAt() > 0
                    ? record.getExecutedAt() : System.currentTimeMillis()));
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("写入 " + TABLE + " 失败: " + e.getMessage(), e);
        }
        trim();
    }

    /** 执行历史（按时间倒序；datasourceId 为 null 时给全量）。 */
    public List<SqlHistoryRecord> list(String datasourceId, int limit) {
        StringBuilder sql = new StringBuilder("SELECT id, datasource_id, operator, sql_digest, sql_text,"
                + " statement_kind, success, row_count, latency_ms, error_message, executed_at FROM " + TABLE);
        if (datasourceId != null && !datasourceId.trim().isEmpty()) {
            sql.append(" WHERE datasource_id = ?");
        }
        sql.append(" ORDER BY executed_at DESC, id DESC LIMIT ?");
        List<SqlHistoryRecord> out = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            int idx = 1;
            if (datasourceId != null && !datasourceId.trim().isEmpty()) {
                ps.setString(idx++, datasourceId.trim());
            }
            ps.setInt(idx, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    SqlHistoryRecord r = new SqlHistoryRecord();
                    r.setId(rs.getLong("id"));
                    r.setDatasourceId(rs.getString("datasource_id"));
                    r.setOperator(rs.getString("operator"));
                    r.setSqlDigest(rs.getString("sql_digest"));
                    r.setSqlText(rs.getString("sql_text"));
                    r.setStatementKind(rs.getString("statement_kind"));
                    r.setSuccess(rs.getInt("success") == 1);
                    r.setRowCount(rs.getLong("row_count"));
                    long latency = rs.getLong("latency_ms");
                    r.setLatencyMs(rs.wasNull() ? null : Long.valueOf(latency));
                    r.setErrorMessage(rs.getString("error_message"));
                    Timestamp ts = rs.getTimestamp("executed_at");
                    r.setExecutedAt(ts == null ? 0L : ts.getTime());
                    out.add(r);
                }
            }
            return out;
        } catch (SQLException e) {
            throw new IllegalStateException("读取 " + TABLE + " 失败: " + e.getMessage(), e);
        }
    }

    /** 滚动淘汰最旧的历史（表里最多留 historyLimit 条）。 */
    private void trim() {
        String sql = "DELETE FROM " + TABLE + " WHERE id NOT IN ("
                + "SELECT id FROM (SELECT id FROM " + TABLE + " ORDER BY executed_at DESC, id DESC"
                + " LIMIT ?) AS keep)";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, historyLimit);
            ps.executeUpdate();
        } catch (SQLException ignored) {
            // 历史裁剪失败不影响执行主流程
        }
    }

    /** SQL 执行历史的一条记录。 */
    public static class SqlHistoryRecord {
        private Long id;
        private String datasourceId;
        private String operator;
        private String sqlDigest;
        private String sqlText;
        private String statementKind;
        private boolean success;
        private long rowCount;
        private Long latencyMs;
        private String errorMessage;
        private long executedAt;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getDatasourceId() {
            return datasourceId;
        }

        public void setDatasourceId(String datasourceId) {
            this.datasourceId = datasourceId;
        }

        public String getOperator() {
            return operator;
        }

        public void setOperator(String operator) {
            this.operator = operator;
        }

        public String getSqlDigest() {
            return sqlDigest;
        }

        public void setSqlDigest(String sqlDigest) {
            this.sqlDigest = sqlDigest;
        }

        public String getSqlText() {
            return sqlText;
        }

        public void setSqlText(String sqlText) {
            this.sqlText = sqlText;
        }

        public String getStatementKind() {
            return statementKind;
        }

        public void setStatementKind(String statementKind) {
            this.statementKind = statementKind;
        }

        public boolean isSuccess() {
            return success;
        }

        public void setSuccess(boolean success) {
            this.success = success;
        }

        public long getRowCount() {
            return rowCount;
        }

        public void setRowCount(long rowCount) {
            this.rowCount = rowCount;
        }

        public Long getLatencyMs() {
            return latencyMs;
        }

        public void setLatencyMs(Long latencyMs) {
            this.latencyMs = latencyMs;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public void setErrorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
        }

        public long getExecutedAt() {
            return executedAt;
        }

        public void setExecutedAt(long executedAt) {
            this.executedAt = executedAt;
        }
    }
}
