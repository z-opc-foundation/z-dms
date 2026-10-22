package com.zifang.z.dms.core.pool;

import com.zifang.z.dms.core.model.DataSourceDef;

/**
 * 连接工厂（SPI）：按定义 + 凭据造一个池。
 */
public interface DataSourcePoolFactory {

    /**
     * 造池。<b>不负责探活</b> —— 探活由 {@code DataSourceService} 在登记时统一触发，
     * 这样「注册即探活」只有一个入口，不会出现某个工厂偷偷跳过探活。
     */
    DataSourcePool create(DataSourceDef def);
}
