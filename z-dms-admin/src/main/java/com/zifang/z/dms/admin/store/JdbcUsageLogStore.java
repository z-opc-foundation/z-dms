package com.zifang.z.dms.admin.store;

import com.zifang.z.dms.admin.audit.UsageRecord;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * 使用流水的持久化（表 {@code z_dms_datasource_usage_log}）。
 *
 * <p><b>落库内容里没有明文凭据</b>：detail 只放结论与耗时。
 */
public class JdbcUsageLogStore {

    public static final String TABLE = "z_dms_datasource_usage_log";

    private final DataSource dataSource;

    public JdbcUsageLogStore(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** 写一条使用流水。 */
    public void save(UsageRecord record) {
        String sql = "INSERT INTO " + TABLE
                + " (datasource_id, action, operator, success, detail, latency_ms, used_at)"
                + " VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, record.getDatasourceId());
            ps.setString(2, record.getAction());
            ps.setString(3, record.getOperator());
            ps.setInt(4, record.isSuccess() ? 1 : 0);
            ps.setString(5, brief(record.getDetail()));
            if (record.getLatencyMs() == null) {
                ps.setNull(6, java.sql.Types.BIGINT);
            } else {
                ps.setLong(6, record.getLatencyMs());
            }
            ps.setTimestamp(7, new Timestamp(record.getUsedAt() > 0
                    ? record.getUsedAt() : System.currentTimeMillis()));
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("写入 " + TABLE + " 失败: " + e.getMessage(), e);
        }
    }

    /** 使用流水（按时间倒序；datasourceId 为 null 时给全量）。 */
    public List<UsageRecord> list(String datasourceId, int limit) {
        StringBuilder sql = new StringBuilder("SELECT id, datasource_id, action, operator, success, "
                + "detail, latency_ms, used_at FROM " + TABLE);
        if (datasourceId != null && !datasourceId.trim().isEmpty()) {
            sql.append(" WHERE datasource_id = ?");
        }
        sql.append(" ORDER BY used_at DESC, id DESC LIMIT ?");
        List<UsageRecord> out = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            int idx = 1;
            if (datasourceId != null && !datasourceId.trim().isEmpty()) {
                ps.setString(idx++, datasourceId.trim());
            }
            ps.setInt(idx, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    UsageRecord r = new UsageRecord();
                    r.setId(rs.getLong("id"));
                    r.setDatasourceId(rs.getString("datasource_id"));
                    r.setAction(rs.getString("action"));
                    r.setOperator(rs.getString("operator"));
                    r.setSuccess(rs.getInt("success") == 1);
                    r.setDetail(rs.getString("detail"));
                    long latency = rs.getLong("latency_ms");
                    r.setLatencyMs(rs.wasNull() ? null : Long.valueOf(latency));
                    Timestamp ts = rs.getTimestamp("used_at");
                    r.setUsedAt(ts == null ? 0L : ts.getTime());
                    out.add(r);
                }
            }
            return out;
        } catch (SQLException e) {
            throw new IllegalStateException("读取 " + TABLE + " 失败: " + e.getMessage(), e);
        }
    }

    private static String brief(String v) {
        if (v == null) {
            return null;
        }
        String flat = v.replace('\n', ' ').replace('\r', ' ').trim();
        return flat.length() <= 512 ? flat : flat.substring(0, 512);
    }
}
