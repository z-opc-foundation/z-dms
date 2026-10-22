package com.zifang.z.dms.core.pool;

import com.zifang.z.dms.core.model.DataSourceDef;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * 一个数据源连接池（z-dms 视角的管理单元）。
 * <p>
 * 换绑语义：同一 id 重新登记时，{@link com.zifang.z.dms.core.registry.DataSourceRegistry}
 * 会显式 {@link #close()} 掉被替换的旧池 —— 沿用 z-lc report 子域 {@code DataSourceRegistry}
 * 已验证的行为（不关的话每重复登记一次就泄漏一个池）。
 */
public interface DataSourcePool extends AutoCloseable {

    /** 该池对应的定义快照。 */
    DataSourceDef definition();

    /** 底层数据源（当前实现是 Druid）。 */
    DataSource dataSource();

    /** 借一个连接（宿主用它执行查询）。 */
    Connection getConnection() throws SQLException;

    /** 显式关闭池（幂等）。 */
    @Override
    void close();

    /**
     * 诊断线索：<b>不经过池</b>直连一次，把根因（Connection refused / 认证失败 / 库不存在）说出来。
     * <p>
     * 为什么需要它：Druid 建连失败时 {@code getConnection()} 会在池里<b>重试到 maxWait</b>，
     * 探活线程先撞上超时 ⇒ 报告里只会看到「probe timed out」，看不到「Connection refused」。
     * 这条线索就是把那层重试剥掉，让失败原因能直接被读出来（否则运维只能去翻 Druid 的日志）。
     * <p>
     * 默认实现返回 null（表示没有额外线索）。
     */
    default String diagnose() {
        return null;
    }
}
