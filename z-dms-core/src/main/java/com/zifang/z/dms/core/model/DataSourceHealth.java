package com.zifang.z.dms.core.model;

/**
 * 探活状态。
 */
public enum DataSourceHealth {

    /** 探活通过：真的借到连接并执行了 SELECT 1。 */
    UP,
    /** 探活失败：连不通 / 超时 / 驱动不受理 —— 必须对宿主可见，不允许静默吞掉。 */
    DOWN
}
