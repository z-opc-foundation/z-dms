package com.zifang.z.dms.core.explorer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 库表结构浏览（phpMyAdmin / Adminer 的「结构」那一屏的后端）。
 *
 * <p>全部走 JDBC {@link DatabaseMetaData}，<b>不拼任何用户输入进 SQL</b>：
 * schema / 表名只作为 metadata 方法的模式参数（驱动自己处理转义），
 * 唯一拼 SQL 的地方是 {@link #showDdl}，且表名经过 {@link #requireTable} 白名单校验
 * （先在元数据里查到这张表才允许拼 SHOW CREATE TABLE）。
 *
 * <p><b>MySQL 8 驱动的库名坑</b>：Connector/J 默认 {@code databaseTerm=CATALOG}，
 * 库名在 <b>catalog</b> 里，{@code getSchemas()} 返回空。所以列表先试 schemas、空了再试
 * catalogs；查表 / 查字段先按 catalog 试、空了再按 schemaPattern 试 ——
 * 两套摆法都兼容（H2 测试环境与 MySQL 生产各走各的那条）。
 */
public class SchemaExplorer {

    private static final Logger log = LoggerFactory.getLogger(SchemaExplorer.class);

    /** 结构视图里不做全表扫描：索引元数据最多取这么多行。 */
    private static final int MAX_INDEX_ROWS = 500;

    private final DmsConnectionProvider connectionProvider;

    public SchemaExplorer(DmsConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
    }

    /** 库（schema）清单。MySQL 下是 database 名单，只显示当前账号可见的。 */
    public List<SchemaObject> listSchemas(String sourceId) {
        try (Connection conn = connectionProvider.acquire(sourceId)) {
            DatabaseMetaData meta = conn.getMetaData();
            List<SchemaObject> out = new ArrayList<>();
            try (ResultSet rs = meta.getSchemas()) {
                while (rs.next()) {
                    String name = rs.getString(1);
                    if (name != null && !name.trim().isEmpty()) {
                        out.add(new SchemaObject(name, SchemaObject.KIND_SCHEMA, null));
                    }
                }
            }
            if (out.isEmpty()) {
                // MySQL（databaseTerm=CATALOG）走这条路：库名在 catalogs 里。
                try (ResultSet rs = meta.getCatalogs()) {
                    while (rs.next()) {
                        String name = rs.getString(1);
                        if (name != null && !name.trim().isEmpty()) {
                            out.add(new SchemaObject(name, SchemaObject.KIND_SCHEMA, null));
                        }
                    }
                }
            }
            // 连接默认库排最前（phpMyAdmin 口径：进来就落在当前库）。
            String preferred = defaultSchema(conn);
            if (preferred != null) {
                out.sort((a, b) -> {
                    if (a.getName().equals(preferred)) return -1;
                    if (b.getName().equals(preferred)) return 1;
                    return 0;
                });
            }
            return out;
        } catch (SQLException e) {
            throw new IllegalStateException("浏览库清单失败: " + rootMessage(e), e);
        }
    }

    /**
     * 某个库下的表 / 视图清单。
     *
     * @param schema 库名；空 / null = 当前连接的默认库
     * @param kind {@code TABLE} / {@code VIEW} / null（两者都要）
     */
    public List<SchemaObject> listTables(String sourceId, String schema, String kind) {
        String[] types = toJdbcTypes(kind);
        try (Connection conn = connectionProvider.acquire(sourceId)) {
            DatabaseMetaData meta = conn.getMetaData();
            String effectiveSchema = schema == null || schema.trim().isEmpty()
                    ? defaultSchema(conn) : schema.trim();
            ResultSet rs = null;
            List<SchemaObject> out;
            try {
                rs = meta.getTables(effectiveSchema, null, "%", types);
                out = readTableRows(rs);
                // schemaPattern 兜底只给真·schema 驱动（H2 / PostgreSQL）。MySQL 的
                // databaseTerm=CATALOG 会忽略 schemaPattern：null catalog 摆法会返回
                // 全部库的表，把别的库漏进来（实测 Connector/J 8.2）。
                if (out.isEmpty() && meta.supportsSchemasInTableDefinitions()) {
                    closeQuietly(rs);
                    rs = meta.getTables(null, effectiveSchema, "%", types);
                    out = readTableRows(rs);
                }
                log.debug("z-dms 浏览表清单：source={} schema={} kind={} 共 {} 个",
                        sourceId, effectiveSchema, kind, out.size());
                return out;
            } finally {
                closeQuietly(rs);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("浏览表清单失败: " + rootMessage(e), e);
        }
    }

    /** 表结构：字段 + 索引 + 建表语句（尽力而为）。 */
    public TableDetail describeTable(String sourceId, String schema, String table) {
        try (Connection conn = connectionProvider.acquire(sourceId)) {
            ResolvedTable rt = requireTable(conn, schema, table);
            TableDetail detail = new TableDetail();
            detail.setSchema(rt.schema);
            detail.setTable(rt.table);
            detail.setKind(rt.kind);
            detail.setRemark(rt.remark);
            detail.setColumns(readColumns(conn.getMetaData(), rt));
            detail.setIndexes(readIndexes(conn.getMetaData(), rt));
            detail.setDdl(showDdl(conn, rt));
            return detail;
        } catch (SQLException e) {
            throw new IllegalStateException("读表结构失败: " + rootMessage(e), e);
        }
    }

    // ------------------------------------------------------------ 内部工具

    private String[] toJdbcTypes(String kind) {
        if (kind == null || kind.trim().isEmpty()
                || SchemaObject.KIND_TABLE.equalsIgnoreCase(kind.trim())) {
            return new String[]{"TABLE"};
        }
        if (SchemaObject.KIND_VIEW.equalsIgnoreCase(kind.trim())) {
            return new String[]{"VIEW"};
        }
        return new String[]{"TABLE", "VIEW"};
    }

    /** 当前连接的默认库：MySQL 是 catalog，其它驱动可能是 schema。 */
    private String defaultSchema(Connection conn) throws SQLException {
        String catalog = conn.getCatalog();
        if (catalog != null && !catalog.trim().isEmpty()) {
            return catalog.trim();
        }
        String schema = conn.getSchema();
        return schema == null ? null : schema.trim();
    }

    /**
     * 把 (schema, table) 解析成元数据里真实存在的表。
     * <p>这是防注入的根：后面所有拼 SQL 的地方（SHOW CREATE TABLE / 数据查询）都以
     * 这里返回的<b>真实拼写</b>为准，用户传入的名字只在元数据里当查找键，永不直接进 SQL。
     *
     * @throws IllegalArgumentException 表不存在（明确报错，不静默）
     */
    ResolvedTable requireTable(Connection conn, String schema, String table) throws SQLException {
        if (table == null || table.trim().isEmpty()) {
            throw new IllegalArgumentException("表名不能为空");
        }
        String effectiveSchema = schema == null || schema.trim().isEmpty()
                ? defaultSchema(conn) : schema.trim();
        String wanted = table.trim();
        DatabaseMetaData meta = conn.getMetaData();
        // 先按 catalog 摆法（MySQL 默认），再按 schemaPattern 摆法（H2 / PostgreSQL 等）。
        ResultSet rs = meta.getTables(effectiveSchema, null, wanted, new String[]{"TABLE", "VIEW"});
        ResolvedTable rt = tryMatchTable(rs, wanted, effectiveSchema, true);
        // 兜底摆法同样只给真·schema 驱动；MySQL 上 schemaPattern 被忽略，兜底会跨库
        // 误匹配同名表（把 oc.table 查成 sys.table），必须挡住。
        if (rt == null && meta.supportsSchemasInTableDefinitions()) {
            closeQuietly(rs);
            rs = meta.getTables(null, effectiveSchema, wanted, new String[]{"TABLE", "VIEW"});
            rt = tryMatchTable(rs, wanted, effectiveSchema, false);
        }
        if (rt == null) {
            throw new IllegalArgumentException(
                    "表不存在（或当前账号无权查看）: " + effectiveSchema + "." + wanted);
        }
        return rt;
    }

    private ResolvedTable tryMatchTable(ResultSet rs, String wanted, String effectiveSchema,
                                        boolean asCatalog) throws SQLException {
        try {
            while (rs.next()) {
                String name = rs.getString("TABLE_NAME");
                if (wanted.equalsIgnoreCase(name)) {
                    ResolvedTable rt = new ResolvedTable();
                    rt.table = name;
                    rt.schema = asCatalog ? rs.getString("TABLE_CAT") : rs.getString("TABLE_SCHEM");
                    if (rt.schema == null || rt.schema.trim().isEmpty()) {
                        rt.schema = effectiveSchema;
                    }
                    String type = rs.getString("TABLE_TYPE");
                    rt.kind = type != null && type.contains("VIEW")
                            ? SchemaObject.KIND_VIEW : SchemaObject.KIND_TABLE;
                    rt.remark = rs.getString("REMARKS");
                    rt.asCatalog = asCatalog;
                    return rt;
                }
            }
            return null;
        } finally {
            closeQuietly(rs);
        }
    }

    private List<SchemaObject> readTableRows(ResultSet rs) throws SQLException {
        List<SchemaObject> out = new ArrayList<>();
        while (rs.next()) {
            String name = rs.getString("TABLE_NAME");
            String type = rs.getString("TABLE_TYPE");
            String remark = rs.getString("REMARKS");
            if (name == null || name.trim().isEmpty()) {
                continue;
            }
            String k = type != null && type.contains("VIEW")
                    ? SchemaObject.KIND_VIEW : SchemaObject.KIND_TABLE;
            out.add(new SchemaObject(name, k, remark));
        }
        return out;
    }

    private List<ColumnMeta> readColumns(DatabaseMetaData meta, ResolvedTable rt) throws SQLException {
        List<ColumnMeta> out = new ArrayList<>();
        ResultSet rs = rt.asCatalog
                ? meta.getColumns(rt.schema, null, rt.table, "%")
                : meta.getColumns(null, rt.schema, rt.table, "%");
        try {
            while (rs.next()) {
                ColumnMeta c = new ColumnMeta();
                c.setName(rs.getString("COLUMN_NAME"));
                c.setTypeName(rs.getString("TYPE_NAME"));
                int precision = rs.getInt("COLUMN_SIZE");
                c.setPrecision(rs.wasNull() ? 0 : precision);
                int scale = rs.getInt("DECIMAL_DIGITS");
                c.setScale(rs.wasNull() ? 0 : scale);
                c.setNullable(!"NO".equals(rs.getString("IS_NULLABLE")));
                String auto = rs.getString("IS_AUTOINCREMENT");
                c.setAutoIncrement("YES".equals(auto));
                c.setDefaultValue(rs.getString("COLUMN_DEF"));
                c.setRemark(rs.getString("REMARKS"));
                int ordinal = rs.getInt("ORDINAL_POSITION");
                c.setOrdinal(rs.wasNull() ? out.size() + 1 : ordinal);
                out.add(c);
            }
        } finally {
            closeQuietly(rs);
        }
        markPrimaryKeys(meta, rt, out);
        return out;
    }

    private void markPrimaryKeys(DatabaseMetaData meta, ResolvedTable rt, List<ColumnMeta> columns)
            throws SQLException {
        Map<String, Boolean> pk = new LinkedHashMap<>();
        ResultSet rs = rt.asCatalog
                ? meta.getPrimaryKeys(rt.schema, null, rt.table)
                : meta.getPrimaryKeys(null, rt.schema, rt.table);
        try {
            while (rs.next()) {
                String col = rs.getString("COLUMN_NAME");
                if (col != null) {
                    pk.put(col, Boolean.TRUE);
                }
            }
        } finally {
            closeQuietly(rs);
        }
        if (pk.isEmpty()) {
            return;
        }
        for (ColumnMeta c : columns) {
            if (c.getName() != null && pk.containsKey(c.getName())) {
                c.setPrimaryKey(true);
            }
        }
    }

    private List<IndexMeta> readIndexes(DatabaseMetaData meta, ResolvedTable rt) throws SQLException {
        Map<String, IndexMeta> byName = new LinkedHashMap<>();
        ResultSet rs = null;
        try {
            rs = rt.asCatalog
                    ? meta.getIndexInfo(rt.schema, null, rt.table, false, true)
                    : meta.getIndexInfo(null, rt.schema, rt.table, false, true);
            int rows = 0;
            while (rs.next()) {
                if (++rows > MAX_INDEX_ROWS) {
                    break;
                }
                String name = rs.getString("INDEX_NAME");
                if (name == null) {
                    continue;
                }
                String column = rs.getString("COLUMN_NAME");
                boolean nonUnique = rs.getBoolean("NON_UNIQUE");
                IndexMeta m = byName.get(name);
                if (m == null) {
                    m = new IndexMeta();
                    m.setName(name);
                    m.setNonUnique(nonUnique);
                    byName.put(name, m);
                }
                if (column != null && !column.trim().isEmpty()) {
                    m.setColumns(m.getColumns() == null ? column : m.getColumns() + ", " + column);
                }
            }
        } catch (SQLException e) {
            // 有些库（视图 / 无权限）索引元数据拿不到：结构视图降级为「无索引」，不炸。
            log.debug("z-dms 读索引元数据失败（schema={} table={}）: {}", rt.schema, rt.table,
                    rootMessage(e));
        } finally {
            closeQuietly(rs);
        }
        return new ArrayList<>(byName.values());
    }

    /**
     * 建表语句：MySQL / MariaDB 用 {@code SHOW CREATE TABLE}；其它库返回 null
     * （前端显示「该库不支持」，不编造一份伪 DDL）。
     */
    private String showDdl(Connection conn, ResolvedTable rt) {
        String product = null;
        try {
            product = conn.getMetaData().getDatabaseProductName();
        } catch (SQLException ignored) {
            return null;
        }
        if (product == null || !product.toLowerCase().contains("mysql")) {
            return null;
        }
        String quoted = quote(rt.schema) + "." + quote(rt.table);
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(
                "SHOW CREATE TABLE " + quoted)) {
            if (rs.next()) {
                // MySQL 返回两列：Table / Create Table（视图是 Create View）。
                String ddl = rs.getString(2);
                return ddl == null ? null : ddl;
            }
        } catch (SQLException e) {
            log.debug("z-dms 取建表语句失败（schema={} table={}）: {}", rt.schema, rt.table,
                    rootMessage(e));
        }
        return null;
    }

    /** 标识符加引号（按驱动声明的引号字符；MySQL 是反引号）。 */
    static String quote(Connection conn, String identifier) throws SQLException {
        String q = conn.getMetaData().getIdentifierQuoteString();
        if (q == null || q.trim().isEmpty()) {
            q = "\"";
        }
        return q + identifier.replace(q, q + q) + q;
    }

    private String quote(String identifier) {
        return "`" + identifier.replace("`", "``") + "`";
    }

    static String rootMessage(SQLException e) {
        Throwable t = e;
        while (t.getCause() != null && t.getCause() != t) {
            t = t.getCause();
        }
        return t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
    }

    static void closeQuietly(ResultSet rs) {
        if (rs != null) {
            try {
                rs.close();
            } catch (SQLException ignored) {
                // 元数据游标关不掉不该影响主流程
            }
        }
    }

    /** 元数据里解析出来的真实表（schema / 表名都是库里的真实拼写）。 */
    static final class ResolvedTable {
        String schema;
        String table;
        String kind;
        String remark;
        boolean asCatalog;
        List<ColumnMeta> columns = Collections.emptyList();
    }
}
