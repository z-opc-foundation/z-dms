package com.zifang.z.dms.core.explorer;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * 浏览 / 查询面的连接借出口（SPI）。
 *
 * <p>explorer 包是纯 JDBC 能力，不认识 Spring；宿主（admin 或形态 A）自己实现它 ——
 * 正路就是 {@code DataSourceServiceConnectionProvider} 包一层
 * {@link com.zifang.z.dms.core.service.DataSourceService#require}。
 * 这样管控台与嵌入式宿主走的是同一个池、同一份定义（不是两份代码）。
 */
public interface DmsConnectionProvider {

    /**
     * 借一个到指定数据源的连接。
     *
     * @param sourceId 数据源标识（z-dms 里登记过的那个 id）
     * @throws SQLException 借连接失败（网络 / 认证 / 池耗尽）
     * @throws IllegalArgumentException 数据源未登记
     */
    Connection acquire(String sourceId) throws SQLException;
}
