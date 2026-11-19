package com.zifang.z.dms.core.explorer;

import java.util.ArrayList;
import java.util.List;

/**
 * 表数据分页结果。
 *
 * <p>单元格值一律 String（二进制 / 大对象降级为占位描述，不外泄原始字节）。
 */
public class TablePage {

    private String schema;
    private String table;
    /** 列名（真实拼写，按表结构顺序）。 */
    private List<String> columns = new ArrayList<>();
    /** 行数据（与 columns 对齐；null 表示 NULL）。 */
    private List<List<String>> rows = new ArrayList<>();
    /** 精确总行数；{@code countTotal=false} 或 count 超时时为 -1（未知）。 */
    private long total = -1;
    private int page = 1;
    private int size = 50;
    /** 本页实际行数是否被服务端截断（超出单页上限）。 */
    private boolean truncated;
    private long latencyMs;

    public String getSchema() {
        return schema;
    }

    public void setSchema(String schema) {
        this.schema = schema;
    }

    public String getTable() {
        return table;
    }

    public void setTable(String table) {
        this.table = table;
    }

    public List<String> getColumns() {
        return columns;
    }

    public void setColumns(List<String> columns) {
        this.columns = columns;
    }

    public List<List<String>> getRows() {
        return rows;
    }

    public void setRows(List<List<String>> rows) {
        this.rows = rows;
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public boolean isTruncated() {
        return truncated;
    }

    public void setTruncated(boolean truncated) {
        this.truncated = truncated;
    }

    public long getLatencyMs() {
        return latencyMs;
    }

    public void setLatencyMs(long latencyMs) {
        this.latencyMs = latencyMs;
    }
}
