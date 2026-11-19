package com.zifang.z.dms.core.explorer;

/**
 * 表数据过滤条件（AND 连接）。
 *
 * <p><b>列名必须出现在目标表的真实字段清单里</b>（{@code TableDataReader} 做白名单校验），
 * 值走 {@code PreparedStatement} 参数 —— 用户输入永不进 SQL 文本。
 */
public class TableFilter {

    public static final String OP_EQ = "EQ";
    public static final String OP_NE = "NE";
    public static final String OP_LIKE = "LIKE";
    public static final String OP_GT = "GT";
    public static final String OP_GE = "GE";
    public static final String OP_LT = "LT";
    public static final String OP_LE = "LE";
    public static final String OP_NULL = "IS_NULL";
    public static final String OP_NOT_NULL = "IS_NOT_NULL";

    private String column;
    private String op = OP_EQ;
    private String value;

    public TableFilter() {
    }

    public TableFilter(String column, String op, String value) {
        this.column = column;
        this.op = op;
        this.value = value;
    }

    public String getColumn() {
        return column;
    }

    public void setColumn(String column) {
        this.column = column;
    }

    public String getOp() {
        return op;
    }

    public void setOp(String op) {
        this.op = op;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}
