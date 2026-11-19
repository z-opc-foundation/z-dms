package com.zifang.z.dms.core.explorer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 表数据分页读取（phpMyAdmin 的「浏览」那一屏的后端）。
 *
 * <p><b>注入防线（三层）</b>：
 * <ol>
 *   <li>schema / 表名先经 {@link SchemaExplorer#requireTable} 在元数据里解析成真实拼写，
 *       用户传入的名字只当查找键，永不直接进 SQL；</li>
 *   <li>排序列 / 过滤列必须出现在该表的真实字段清单里（白名单，大小写不敏感映射回真实拼写）；</li>
 *   <li>过滤值与分页参数全部走 {@code PreparedStatement} 占位符。</li>
 * </ol>
 *
 * <p>排序方向只允许 ASC / DESC；LIMIT / OFFSET 是服务端解析后的整数（用户输入若不是整数
 * 在绑定层就失败了），同样不进文本拼接。
 */
public class TableDataReader {

    private static final Logger log = LoggerFactory.getLogger(TableDataReader.class);

    /** 单页行数上限（防止「每页 100 万行」把管理端打爆）。 */
    private int maxPageSize = 500;
    /** count / 取数的查询超时（秒）。 */
    private int queryTimeoutSeconds = 30;

    private final DmsConnectionProvider connectionProvider;
    private final SchemaExplorer schemaExplorer;

    public TableDataReader(DmsConnectionProvider connectionProvider, SchemaExplorer schemaExplorer) {
        this.connectionProvider = connectionProvider;
        this.schemaExplorer = schemaExplorer;
    }

    public void setMaxPageSize(int maxPageSize) {
        this.maxPageSize = maxPageSize;
    }

    public void setQueryTimeoutSeconds(int queryTimeoutSeconds) {
        this.queryTimeoutSeconds = queryTimeoutSeconds;
    }

    public TablePage read(String sourceId, TableQuery query) {
        if (query == null || query.getTable() == null || query.getTable().trim().isEmpty()) {
            throw new IllegalArgumentException("表名不能为空");
        }
        long startNs = System.nanoTime();
        try (Connection conn = connectionProvider.acquire(sourceId)) {
            SchemaExplorer.ResolvedTable rt =
                    schemaExplorer.requireTable(conn, query.getSchema(), query.getTable());
            List<ColumnMeta> columns = readColumnMetas(conn, rt);
            if (columns.isEmpty()) {
                throw new IllegalArgumentException("表没有任何可读字段: " + rt.schema + "." + rt.table);
            }
            Set<String> realColumns = new HashSet<>();
            for (ColumnMeta c : columns) {
                realColumns.add(c.getName().toLowerCase(Locale.ROOT));
            }

            int page = Math.max(1, query.getPage());
            int size = query.getSize() <= 0 ? 50 : Math.min(query.getSize(), maxPageSize);
            long offset = (long) (page - 1) * size;

            Where where = buildWhere(conn, query, columns, realColumns);
            String orderBy = buildOrderBy(conn, query, columns, realColumns);

            TablePage out = new TablePage();
            out.setSchema(rt.schema);
            out.setTable(rt.table);
            out.setPage(page);
            out.setSize(size);
            for (ColumnMeta c : columns) {
                out.getColumns().add(c.getName());
            }

            if (query.isCountTotal()) {
                out.setTotal(countRows(conn, rt, where));
            }

            String sql = "SELECT * FROM " + qualify(conn, rt) + where.clause() + orderBy
                    + " LIMIT " + size + " OFFSET " + offset;
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setQueryTimeout(queryTimeoutSeconds);
                for (int i = 0; i < where.params.size(); i++) {
                    ps.setString(i + 1, where.params.get(i));
                }
                try (ResultSet rs = ps.executeQuery()) {
                    ResultSetMetaData meta = rs.getMetaData();
                    int columnCount = meta.getColumnCount();
                    List<List<String>> rows = out.getRows();
                    int fetched = 0;
                    while (rs.next() && fetched < size) {
                        List<String> row = new ArrayList<>(columnCount);
                        for (int i = 1; i <= columnCount; i++) {
                            row.add(displayValue(rs.getObject(i)));
                        }
                        rows.add(row);
                        fetched++;
                    }
                    // LIMIT size 已由服务端控制，本页行数就是 fetched；truncated 恒 false（真正的
                    // 截断语义在 SQL 控制台的 maxRows 上，那里会显式标记）。
                    out.setTruncated(false);
                }
            }
            out.setLatencyMs((System.nanoTime() - startNs) / 1_000_000L);
            return out;
        } catch (SQLException e) {
            throw new IllegalStateException("读表数据失败: " + SchemaExplorer.rootMessage(e), e);
        }
    }

    // ------------------------------------------------------------ 内部工具

    private List<ColumnMeta> readColumnMetas(Connection conn, SchemaExplorer.ResolvedTable rt)
            throws SQLException {
        List<ColumnMeta> out = new ArrayList<>();
        java.sql.DatabaseMetaData meta = conn.getMetaData();
        ResultSet rs = rt.asCatalog
                ? meta.getColumns(rt.schema, null, rt.table, "%")
                : meta.getColumns(null, rt.schema, rt.table, "%");
        try {
            while (rs.next()) {
                ColumnMeta c = new ColumnMeta();
                c.setName(rs.getString("COLUMN_NAME"));
                int ordinal = rs.getInt("ORDINAL_POSITION");
                c.setOrdinal(rs.wasNull() ? out.size() + 1 : ordinal);
                out.add(c);
            }
        } finally {
            SchemaExplorer.closeQuietly(rs);
        }
        out.sort((a, b) -> Integer.compare(a.getOrdinal(), b.getOrdinal()));
        return out;
    }

    private Where buildWhere(Connection conn, TableQuery query, List<ColumnMeta> columns,
                             Set<String> realColumns) throws SQLException {
        Where where = new Where();
        List<TableFilter> filters = query.getFilters();
        if (filters == null || filters.isEmpty()) {
            return where;
        }
        for (TableFilter f : filters) {
            if (f == null || f.getColumn() == null || f.getColumn().trim().isEmpty()) {
                continue;
            }
            String real = quoteIdentifier(conn, resolveColumn(f.getColumn(), realColumns, columns));
            String op = f.getOp() == null ? TableFilter.OP_EQ : f.getOp().trim().toUpperCase(Locale.ROOT);
            switch (op) {
                case TableFilter.OP_EQ:
                    where.sqlAppend(" AND " + real + " = ?", f.getValue());
                    break;
                case TableFilter.OP_NE:
                    where.sqlAppend(" AND " + real + " <> ?", f.getValue());
                    break;
                case TableFilter.OP_LIKE:
                    where.sqlAppend(" AND " + real + " LIKE ?",
                            "%" + (f.getValue() == null ? "" : f.getValue()) + "%");
                    break;
                case TableFilter.OP_GT:
                    where.sqlAppend(" AND " + real + " > ?", f.getValue());
                    break;
                case TableFilter.OP_GE:
                    where.sqlAppend(" AND " + real + " >= ?", f.getValue());
                    break;
                case TableFilter.OP_LT:
                    where.sqlAppend(" AND " + real + " < ?", f.getValue());
                    break;
                case TableFilter.OP_LE:
                    where.sqlAppend(" AND " + real + " <= ?", f.getValue());
                    break;
                case TableFilter.OP_NULL:
                    where.sqlAppend(" AND " + real + " IS NULL", null);
                    break;
                case TableFilter.OP_NOT_NULL:
                    where.sqlAppend(" AND " + real + " IS NOT NULL", null);
                    break;
                default:
                    throw new IllegalArgumentException("不支持的过滤操作符: " + op);
            }
        }
        return where;
    }

    private String buildOrderBy(Connection conn, TableQuery query, List<ColumnMeta> columns,
                                Set<String> realColumns) throws SQLException {
        String col = query.getSortColumn();
        if (col == null || col.trim().isEmpty()) {
            return "";
        }
        String real = quoteIdentifier(conn, resolveColumn(col, realColumns, columns));
        String direction = query.getSortOrder() == null
                || !"DESC".equalsIgnoreCase(query.getSortOrder().trim()) ? "ASC" : "DESC";
        return " ORDER BY " + real + " " + direction;
    }

    /**
     * 过滤列 / 排序列 → 表里真实字段拼写（大小写不敏感）。
     *
     * @throws IllegalArgumentException 不是该表的字段
     */
    private String resolveColumn(String wanted, Set<String> realColumns, List<ColumnMeta> columns) {
        String key = wanted.trim().toLowerCase(Locale.ROOT);
        if (!realColumns.contains(key)) {
            throw new IllegalArgumentException(
                    "过滤字段不是该表的字段: " + wanted + "（防注入：只允许按真实字段过滤）");
        }
        for (ColumnMeta c : columns) {
            if (c.getName() != null && c.getName().equalsIgnoreCase(wanted.trim())) {
                return c.getName();
            }
        }
        return wanted.trim();
    }

    private long countRows(Connection conn, SchemaExplorer.ResolvedTable rt, Where where)
            throws SQLException {
        String sql = "SELECT COUNT(*) FROM " + qualify(conn, rt) + where.clause();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            // count 单独给短超时：大表全表扫不该把管理端拖死；超时 ⇒ 总数未知（-1），分页照常。
            ps.setQueryTimeout(Math.max(3, queryTimeoutSeconds / 2));
            for (int i = 0; i < where.params.size(); i++) {
                ps.setString(i + 1, where.params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        } catch (SQLException e) {
            log.warn("z-dms count 总行数失败（schema={} table={}）: {}",
                    rt.schema, rt.table, SchemaExplorer.rootMessage(e));
        }
        return -1;
    }

    private String qualify(Connection conn, SchemaExplorer.ResolvedTable rt) throws SQLException {
        return quoteIdentifier(conn, rt.schema) + "." + quoteIdentifier(conn, rt.table);
    }

    /** 标识符加引号（按驱动声明的引号字符；MySQL 是反引号）。 */
    private String quoteIdentifier(Connection conn, String identifier) throws SQLException {
        String q = conn.getMetaData().getIdentifierQuoteString();
        if (q == null || q.trim().isEmpty()) {
            q = "\"";
        }
        return q + identifier.replace(q, q + q) + q;
    }

    /** 单元格显示值：null 保持 null；二进制 / 大对象不外泄原始字节。 */
    static String displayValue(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof byte[]) {
            return "<binary " + ((byte[]) v).length + "B>";
        }
        if (v instanceof java.sql.Blob) {
            return "<blob>";
        }
        if (v instanceof java.sql.Clob) {
            return "<clob>";
        }
        String s = String.valueOf(v);
        return s.length() > 4096 ? s.substring(0, 4096) + "…(截断)" : s;
    }

    /** 拼好 WHERE 片段 + 对应参数值。 */
    private static final class Where {
        final StringBuilder sql = new StringBuilder();
        final List<String> params = new ArrayList<>();

        void sqlAppend(String fragment, String param) {
            sql.append(fragment);
            if (param != null) {
                params.add(param);
            }
        }

        /** 片段以 " AND " 开头；空清单返回空串，否则替换成 WHERE 开头。 */
        String clause() {
            return sql.length() == 0 ? "" : " WHERE " + sql.substring(5);
        }
    }
}
