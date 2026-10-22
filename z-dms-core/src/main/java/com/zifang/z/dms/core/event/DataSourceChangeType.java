package com.zifang.z.dms.core.event;

/**
 * 变更类型。
 */
public enum DataSourceChangeType {

    /** 新登记 */
    REGISTERED,
    /** 更新（换绑：旧池显式关闭） */
    UPDATED,
    /** 摘除 */
    REMOVED
}
