package com.zifang.z.dms.core.explorer;

import com.zifang.z.dms.core.service.DataSourceService;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * {@link DmsConnectionProvider} 的标准实现：包一层 {@link DataSourceService#require}。
 *
 * <p>管控台与嵌入式宿主都走这一条 —— 浏览 / 查询用的连接来自<b>同一个池</b>、
 * 同一份定义，不是另起的裸连接。
 */
public class DataSourceServiceConnectionProvider implements DmsConnectionProvider {

    private final DataSourceService service;

    public DataSourceServiceConnectionProvider(DataSourceService service) {
        this.service = service;
    }

    @Override
    public Connection acquire(String sourceId) throws SQLException {
        return service.require(sourceId).getConnection();
    }
}
