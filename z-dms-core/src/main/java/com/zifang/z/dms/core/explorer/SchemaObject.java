package com.zifang.z.dms.core.explorer;

/**
 * schema（库）或表/视图对象 —— 浏览树上的一个节点。
 *
 * <p>{@code kind} 取值：{@code SCHEMA} / {@code TABLE} / {@code VIEW}。
 */
public class SchemaObject {

    public static final String KIND_SCHEMA = "SCHEMA";
    public static final String KIND_TABLE = "TABLE";
    public static final String KIND_VIEW = "VIEW";

    private String name;
    /** SCHEMA / TABLE / VIEW。 */
    private String kind;
    /** 表备注（REMARKS，可能为空）。 */
    private String remark;

    public SchemaObject() {
    }

    public SchemaObject(String name, String kind, String remark) {
        this.name = name;
        this.kind = kind;
        this.remark = remark;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
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
}
