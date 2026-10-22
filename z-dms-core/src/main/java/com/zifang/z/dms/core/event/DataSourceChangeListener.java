package com.zifang.z.dms.core.event;

/**
 * 变更监听器（进程内）。
 */
public interface DataSourceChangeListener {

    void onChange(DataSourceChangeEvent event);
}
