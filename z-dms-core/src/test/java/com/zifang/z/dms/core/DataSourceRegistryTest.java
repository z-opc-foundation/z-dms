package com.zifang.z.dms.core;

import com.zifang.z.dms.core.model.DataSourceDef;
import com.zifang.z.dms.core.model.DataSourceType;
import com.zifang.z.dms.core.pool.DataSourcePool;
import com.zifang.z.dms.core.registry.DataSourceRegistry;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 换绑旧池显式关闭（卡面要求的行为，行为沿用 z-lc report 子域已验证的做法）。
 */
class DataSourceRegistryTest {

    /** 记录自己有没有被关掉的假池。 */
    static class FakePool implements DataSourcePool {
        private final DataSourceDef def;
        private final AtomicBoolean closed = new AtomicBoolean(false);

        FakePool(String id) {
            this.def = new DataSourceDef(id, id, DataSourceType.JDBC, "g/" + id, null);
        }

        @Override
        public DataSourceDef definition() {
            return def;
        }

        @Override
        public DataSource dataSource() {
            return null;
        }

        @Override
        public Connection getConnection() throws SQLException {
            throw new SQLException("fake");
        }

        @Override
        public void close() {
            closed.set(true);
        }

        boolean isClosed() {
            return closed.get();
        }
    }

    @Test
    void 同id换绑时旧池被显式关闭() {
        DataSourceRegistry registry = new DataSourceRegistry();
        FakePool first = new FakePool("ds-x");
        FakePool second = new FakePool("ds-x");

        assertNull(registry.register(first));
        assertFalse(first.isClosed());

        DataSourcePool previous = registry.register(second);
        assertSame(first, previous, "换绑应返回被换掉的旧池");
        assertTrue(first.isClosed(), "旧池必须被显式关闭，否则每重复登记一次泄漏一个池");
        assertFalse(second.isClosed());
        assertSame(second, registry.require("ds-x"));
    }

    @Test
    void 摘除时池被关闭() {
        DataSourceRegistry registry = new DataSourceRegistry();
        FakePool pool = new FakePool("ds-y");
        registry.register(pool);
        assertNotNull(registry.remove("ds-y"));
        assertTrue(pool.isClosed());
        assertNull(registry.get("ds-y"));
    }

    @Test
    void 全量关闭() {
        DataSourceRegistry registry = new DataSourceRegistry();
        FakePool a = new FakePool("a");
        FakePool b = new FakePool("b");
        registry.register(a);
        registry.register(b);
        registry.closeAll();
        assertTrue(a.isClosed());
        assertTrue(b.isClosed());
    }
}
