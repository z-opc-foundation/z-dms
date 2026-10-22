package com.zifang.z.dms.core.model;

/**
 * 数据源类型。
 * <p>
 * <b>沿用 z-lc report 子域的 5 值枚举，不另起枚举</b>（对齐 TASK-20261012-009 盘点 §4.4）：
 * z-lc 侧是 {@code com.zifang.z.lc.report.common.schema.DataSourceType}，
 * 值集合 MEMORY / JDBC / CSV / HTTP / SQL 完全一致 ⇒ z-lc 侧不需要为接 z-dms 改造。
 * <p>
 * 本仓（形态 A 嵌入式 starter）当前只真正落地 JDBC 一种：其余类型的连接工厂留给后续形态 B / z-etl 消费。
 */
public enum DataSourceType {

    /** 内存源（演示 / 测试用） */
    MEMORY,
    /** JDBC 源（MySQL 等）；本仓连接工厂当前唯一落地的类型 */
    JDBC,
    /** 文件源（CSV） */
    CSV,
    /** HTTP 源 */
    HTTP,
    /** 内存 SQL 源：把其它源的表搬进内存后，数据单元是一条 join / 聚合 SELECT */
    SQL
}
