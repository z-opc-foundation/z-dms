package com.zifang.z.dms.core;

import com.zifang.z.dms.core.health.DataSourceHealthProber;
import com.zifang.z.dms.core.model.DataSourceDef;
import com.zifang.z.dms.core.model.DataSourceHealth;
import com.zifang.z.dms.core.model.DataSourceHealthReport;
import com.zifang.z.dms.core.model.DataSourceProbeResult;
import com.zifang.z.dms.core.model.DataSourceType;
import com.zifang.z.dms.core.pool.DataSourcePool;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验收第 2 项的一半：<b>探活失败必须落到结论里，不允许静默</b>。
 */
class DataSourceHealthProberTest {

    /** 连不通的池：getConnection 直接抛。 */
    static class BrokenPool implements DataSourcePool {
        private final DataSourceDef def;

        BrokenPool(String id) {
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
            throw new SQLException("Connection refused");
        }

        @Override
        public void close() {
        }
    }

    @Test
    void 连不通的池探活结论是DOWN且带原因() {
        DataSourceHealthProber prober = new DataSourceHealthProber(1000L);
        DataSourceProbeResult r = prober.probe(new BrokenPool("ds-broken"));
        assertEquals(DataSourceHealth.DOWN, r.getHealth());
        assertTrue(r.getDetail().contains("Connection refused"), r.getDetail());
        assertFalse(r.isUp());
    }

    @Test
    void 整体报告在有DOWN时不是UP() {
        DataSourceHealthProber prober = new DataSourceHealthProber(1000L);
        DataSourceHealthReport report = prober.report(
                Collections.singletonList(new BrokenPool("ds-broken")));
        assertEquals(DataSourceHealth.DOWN, report.getStatus());
        assertTrue(report.getReason().contains("ds-broken"), String.valueOf(report.getReason()));
        assertFalse(report.isUp());
    }

    @Test
    void 空上下文不算UP() {
        DataSourceHealthProber prober = new DataSourceHealthProber(1000L);
        DataSourceHealthReport report = prober.report(Collections.emptyList());
        assertEquals(DataSourceHealth.DOWN, report.getStatus());
    }
}
