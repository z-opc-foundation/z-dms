package com.zifang.z.dms.core.explorer;

/**
 * 表详情：字段 + 索引（结构视图一次拿全）。
 */
public class TableDetail {

    private String schema;
    private String table;
    /** TABLE / VIEW。 */
    private String kind;
    /** 表备注（可能为空）。 */
    private String remark;
    /** 建表语句（MySQL 的 SHOW CREATE TABLE；库不支持时为 null —— 不编造）。 */
    private String ddl;
    private java.util.List<ColumnMeta> columns = new java.util.ArrayList<>();
    private java.util.List<IndexMeta> indexes = new java.util.ArrayList<>();

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

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public String getDdl() {
        return ddl;
    }

    public void setDdl(String ddl) {
        this.ddl = ddl;
    }

    public java.util.List<ColumnMeta> getColumns() {
        return columns;
    }

    public void setColumns(java.util.List<ColumnMeta> columns) {
        this.columns = columns;
    }

    public java.util.List<IndexMeta> getIndexes() {
        return indexes;
    }

    public void setIndexes(java.util.List<IndexMeta> indexes) {
        this.indexes = indexes;
    }
}
