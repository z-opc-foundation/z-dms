package com.zifang.z.dms.core.registry;

import com.zifang.z.dms.core.pool.DataSourcePool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 数据源注册中心：id → 连接池。
 * <p>
 * <b>换绑旧池显式关闭</b>（卡面要求，行为沿用 z-lc report 子域 {@code DataSourceRegistry} 已验证的做法）：
 * 同 id 重复登记时，把被替换的旧池 {@code close()} 掉 ——
 * 不关的话每重复登记一次就泄漏一个 Druid 池（连接、线程、文件句柄全漏）。
 */
public class DataSourceRegistry {

    private static final Logger log = LoggerFactory.getLogger(DataSourceRegistry.class);

    private final Map<String, DataSourcePool> pools = new ConcurrentHashMap<>();

    /**
     * 登记一个池；同 id 已存在时<b>显式关闭旧池</b>后换绑。
     *
     * @return 被换掉的旧池（没有则 null）
     */
    public DataSourcePool register(DataSourcePool pool) {
        if (pool == null || pool.definition() == null || pool.definition().getId() == null) {
            throw new IllegalArgumentException("数据源池与 def/id 不能为空");
        }
        DataSourcePool previous = pools.put(pool.definition().getId(), pool);
        closeQuietly(previous, pool.definition().getId());
        return previous;
    }

    public DataSourcePool get(String id) {
        return pools.get(id);
    }

    /** 取数据源，不存在时抛异常（执行链路要求快速失败）。 */
    /** 是否已登记。 */
    public boolean contains(String id) {
        return pools.containsKey(id);
    }

    /** 取数据源，不存在时抛异常（执行链路要求快速失败）。 */
    public DataSourcePool require(String id) {
        DataSourcePool pool = pools.get(id);
        if (pool == null) {
            throw new IllegalArgumentException("数据源未登记: " + id);
        }
        return pool;
    }

    public List<DataSourcePool> all() {
        return new ArrayList<>(pools.values());
    }

    /** 摘除并显式关闭。 */
    public DataSourcePool remove(String id) {
        DataSourcePool removed = pools.remove(id);
        closeQuietly(removed, id);
        return removed;
    }

    /** 全量关闭（宿主停机时调用）。 */
    public void closeAll() {
        for (Map.Entry<String, DataSourcePool> e : pools.entrySet()) {
            closeQuietly(e.getValue(), e.getKey());
        }
        pools.clear();
    }

    private void closeQuietly(DataSourcePool pool, String id) {
        if (pool == null) {
            return;
        }
        try {
            pool.close();
            log.info("z-dms 关闭被替换/摘除的数据源池 [{}]", id);
        } catch (Exception e) {
            log.warn("关闭数据源池 [{}] 失败: {}", id, e.getMessage());
        }
    }
}
