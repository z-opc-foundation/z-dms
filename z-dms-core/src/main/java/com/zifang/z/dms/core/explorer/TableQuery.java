package com.zifang.z.dms.core.explorer;

import java.util.ArrayList;
import java.util.List;

/**
 * 表数据查询请求（分页 / 排序 / 过滤）。
 */
public class TableQuery {

    private String schema;
    private String table;
    /** 页号，从 1 起。 */
    private int page = 1;
    /** 每页行数（服务端会按上限收敛）。 */
    private int size = 50;
    /** 排序列（必须真实存在）。 */
    private String sortColumn;
    /** ASC / DESC（默认 ASC）。 */
    private String sortOrder = "ASC";
    /** 过滤条件（AND 连接；可为空）。 */
    private List<TableFilter> filters = new ArrayList<>();
    /** 是否返回精确总行数（大表可以关掉 count 以省一次全表扫）。 */
    private boolean countTotal = true;

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

    public String getSortColumn() {
        return sortColumn;
    }

    public void setSortColumn(String sortColumn) {
        this.sortColumn = sortColumn;
    }

    public String getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(String sortOrder) {
        this.sortOrder = sortOrder;
    }

    public List<TableFilter> getFilters() {
        return filters;
    }

    public void setFilters(List<TableFilter> filters) {
        this.filters = filters == null ? new ArrayList<TableFilter>() : filters;
    }

    public boolean isCountTotal() {
        return countTotal;
    }

    public void setCountTotal(boolean countTotal) {
        this.countTotal = countTotal;
    }
}
