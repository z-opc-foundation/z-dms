package com.zifang.z.dms.admin.store;

import com.zifang.z.dms.admin.audit.ChangeRecord;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * 变更流水的持久化（表 {@code z_dms_datasource_change_log}）。
 *
 * <p>纯 JDBC，与 core 里的 {@code JdbcDataSourceDefStore} 同款形状（不引 ORM）：
 * 这张表字段少，形态 B 的读法就两种（按源列 / 全量倒序）。
 *
 * <p><b>落库内容里没有明文凭据</b>：只有 credential_ref 与<b>打码过</b>的前后值快照。
 */
public class JdbcChangeLogStore {

    public static final String TABLE = "z_dms_datasource_change_log";

    private final DataSource dataSource;

    public JdbcChangeLogStore(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** 写一条变更流水。 */
    public void save(ChangeRecord record) {
        String sql = "INSERT INTO " + TABLE
                + " (datasource_id, change_type, credential_ref, operator, detail, before_value, after_value, changed_at)"
                + " VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, record.getDatasourceId());
            ps.setString(2, record.getChangeType());
            ps.setString(3, record.getCredentialRef());
            ps.setString(4, record.getOperator());
            ps.setString(5, brief(record.getDetail()));
            ps.setString(6, record.getBeforeValue());
            ps.setString(7, record.getAfterValue());
            ps.setTimestamp(8, new Timestamp(record.getChangedAt() > 0
                    ? record.getChangedAt() : System.currentTimeMillis()));
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("写入 " + TABLE + " 失败: " + e.getMessage(), e);
        }
    }

    /** 变更历史（按时间倒序；datasourceId 为 null 时给全量）。 */
    public List<ChangeRecord> list(String datasourceId, int limit) {
        StringBuilder sql = new StringBuilder("SELECT id, datasource_id, change_type, credential_ref, operator, "
                + "detail, before_value, after_value, changed_at FROM " + TABLE);
        if (datasourceId != null && !datasourceId.trim().isEmpty()) {
            sql.append(" WHERE datasource_id = ?");
        }
        sql.append(" ORDER BY changed_at DESC, id DESC LIMIT ?");
        List<ChangeRecord> out = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            int idx = 1;
            if (datasourceId != null && !datasourceId.trim().isEmpty()) {
                ps.setString(idx++, datasourceId.trim());
            }
            ps.setInt(idx, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ChangeRecord r = new ChangeRecord();
                    r.setId(rs.getLong("id"));
                    r.setDatasourceId(rs.getString("datasource_id"));
                    r.setChangeType(rs.getString("change_type"));
                    r.setCredentialRef(rs.getString("credential_ref"));
                    r.setOperator(rs.getString("operator"));
                    r.setDetail(rs.getString("detail"));
                    r.setBeforeValue(rs.getString("before_value"));
                    r.setAfterValue(rs.getString("after_value"));
                    Timestamp ts = rs.getTimestamp("changed_at");
                    r.setChangedAt(ts == null ? 0L : ts.getTime());
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
