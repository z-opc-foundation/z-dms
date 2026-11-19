package com.zifang.z.dms.core.explorer;

/**
 * 索引元数据（表结构视图的索引块）。
 */
public class IndexMeta {

    /** 索引名（主键索引通常是 PRIMARY）。 */
    private String name;
    /** 索引列（按列在索引内的序号排序后拼接，如 {@code a, b}）。 */
    private String columns;
    /** 是否非唯一索引（false = 唯一索引）。 */
    private boolean nonUnique;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getColumns() {
        return columns;
    }

    public void setColumns(String columns) {
        this.columns = columns;
    }

    public boolean isNonUnique() {
        return nonUnique;
    }

    public void setNonUnique(boolean nonUnique) {
        this.nonUnique = nonUnique;
    }
}
