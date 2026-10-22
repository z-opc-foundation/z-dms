package com.zifang.z.dms.core.store;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zifang.z.dms.core.model.DataSourceDef;
import com.zifang.z.dms.core.model.DataSourceType;
import com.zifang.z.dms.core.support.DataSourceDefValidator;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * MySQL 实现的定义存储（表 {@code z_dms_datasource_def}）。
 * <p>
 * 纯 JDBC，不引 ORM：这张表只有 5 个字段，形态 B（web 管控）还没开工，
 * 现在上 MyBatis-Plus 属于提前预付复杂度。
 * <p>
 * <b>落库内容</b>：id / name / type / credential_ref / properties(JSON) / created_at / updated_at。
 * <b>没有明文口令列</b> —— 也没有「以后再说」的口子。
 */
public class JdbcDataSourceDefStore implements DataSourceDefStore {

    public static final String TABLE = "z_dms_datasource_def";

    private final DataSource dataSource;
    private final ObjectMapper mapper = new ObjectMapper();

    public JdbcDataSourceDefStore(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public List<DataSourceDef> list() {
        String sql = "SELECT id, name, type, credential_ref, properties FROM " + TABLE + " ORDER BY id";
        List<DataSourceDef> out = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                out.add(read(rs));
            }
            return out;
        } catch (SQLException e) {
            throw new IllegalStateException("读取 " + TABLE + " 失败: " + e.getMessage(), e);
        }
    }

    @Override
    public Optional<DataSourceDef> find(String id) {
        String sql = "SELECT id, name, type, credential_ref, properties FROM " + TABLE + " WHERE id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(read(rs));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("读取 " + TABLE + "[" + id + "] 失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void save(DataSourceDef def) {
        // 落库前再过一次写入口闸：明文一律不入库（纵深防御：调用方可能绕过 service 直接调 store）。
        DataSourceDefValidator.validate(def);
        String sql = "INSERT INTO " + TABLE
                + " (id, name, type, credential_ref, properties, created_at, updated_at)"
                + " VALUES (?, ?, ?, ?, ?, ?, ?)"
                + " ON DUPLICATE KEY UPDATE name = VALUES(name), type = VALUES(type),"
                + " credential_ref = VALUES(credential_ref), properties = VALUES(properties),"
                + " updated_at = VALUES(updated_at)";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            Timestamp now = new Timestamp(System.currentTimeMillis());
            ps.setString(1, def.getId());
            ps.setString(2, def.getName());
            ps.setString(3, def.getType().name());
            ps.setString(4, def.getCredentialRef());
            ps.setString(5, writeProperties(def.getProperties()));
            ps.setTimestamp(6, now);
            ps.setTimestamp(7, now);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("写入 " + TABLE + "[" + def.getId() + "] 失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void delete(String id) {
        String sql = "DELETE FROM " + TABLE + " WHERE id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("删除 " + TABLE + "[" + id + "] 失败: " + e.getMessage(), e);
        }
    }

    private DataSourceDef read(ResultSet rs) throws SQLException {
        String propsJson = rs.getString("properties");
        Map<String, String> props = new LinkedHashMap<>();
        if (propsJson != null && !propsJson.trim().isEmpty()) {
            try {
                props.putAll(mapper.readValue(propsJson, new TypeReference<LinkedHashMap<String, String>>() {
                }));
            } catch (Exception e) {
                throw new IllegalStateException("解析 " + TABLE + " 的 properties JSON 失败: " + e.getMessage(), e);
            }
        }
        return new DataSourceDef(rs.getString("id"), rs.getString("name"),
                DataSourceType.valueOf(rs.getString("type")), rs.getString("credential_ref"), props);
    }

    private String writeProperties(Map<String, String> props) {
        try {
            return mapper.writeValueAsString(props == null ? new LinkedHashMap<String, String>() : props);
        } catch (Exception e) {
            throw new IllegalStateException("序列化 properties 失败: " + e.getMessage(), e);
        }
    }
}
