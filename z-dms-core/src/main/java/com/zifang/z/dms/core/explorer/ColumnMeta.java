package com.zifang.z.dms.core.explorer;

/**
 * 字段元数据（表结构视图的一行）。
 */
public class ColumnMeta {

    /** 字段名（数据库里的真实拼写，作为排序 / 过滤的白名单依据）。 */
    private String name;
    /** 数据类型名（如 VARCHAR / BIGINT / TIMESTAMP）。 */
    private String typeName;
    /** 精度 / 长度（不适用为 0）。 */
    private int precision;
    /** 小数位。 */
    private int scale;
    /** 可否为 NULL。 */
    private boolean nullable;
    /** 是否主键成员。 */
    private boolean primaryKey;
    /** 是否自增。 */
    private boolean autoIncrement;
    /** 默认值（无默认为 null）。 */
    private String defaultValue;
    /** 字段备注。 */
    private String remark;
    /** 序号（从 1 起）。 */
    private int ordinal;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTypeName() {
        return typeName;
    }

    public void setTypeName(String typeName) {
        this.typeName = typeName;
    }

    public int getPrecision() {
        return precision;
    }

    public void setPrecision(int precision) {
        this.precision = precision;
    }

    public int getScale() {
        return scale;
    }

    public void setScale(int scale) {
        this.scale = scale;
    }

    public boolean isNullable() {
        return nullable;
    }

    public void setNullable(boolean nullable) {
        this.nullable = nullable;
    }

    public boolean isPrimaryKey() {
        return primaryKey;
    }

    public void setPrimaryKey(boolean primaryKey) {
        this.primaryKey = primaryKey;
    }

    public boolean isAutoIncrement() {
        return autoIncrement;
    }

    public void setAutoIncrement(boolean autoIncrement) {
        this.autoIncrement = autoIncrement;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public int getOrdinal() {
        return ordinal;
    }

    public void setOrdinal(int ordinal) {
        this.ordinal = ordinal;
    }
}
