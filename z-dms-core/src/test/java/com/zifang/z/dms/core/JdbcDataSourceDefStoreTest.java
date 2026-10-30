package com.zifang.z.dms.core;

import com.zifang.z.dms.core.model.DataSourceDef;
import com.zifang.z.dms.core.model.DataSourceType;
import com.zifang.z.dms.core.store.JdbcDataSourceDefStore;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 定义存储的 CRUD 单测（H2 MySQL 兼容模式，<b>不连任何真实库</b>）。
 * <p>
 * 真实 MySQL 的落库验证在 z-dms-demo-host 端到端跑（见 evidence/TASK-20261012-011/）。
 */
class JdbcDataSourceDefStoreTest {

    private JdbcDataSourceDefStore store;

    @BeforeEach
    void setUp() {
        JdbcDataSource ds = new JdbcDataSource();
        // DB_CLOSE_DELAY=-1：内存库在最后一个连接关闭后会被销毁，建表与用库要分开连接，必须留着。
        ds.setURL("jdbc:h2:mem:dms_store_" + System.nanoTime()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        ds.setUser("sa");
        ds.setPassword("");
        try (java.sql.Connection conn = ds.getConnection();
             java.sql.Statement st = conn.createStatement()) {
            // H2 兼容模式下的等价建表语句（生产权威 DDL 在 _doc/002_deploy/init/V1__z_dms_datasource_def.sql，
            // 那边带 InnoDB/utf8mb4，H2 不认这两段语法，故此处只取等价列定义）。
            st.execute("CREATE TABLE IF NOT EXISTS z_dms_datasource_def ("
                    + "id VARCHAR(64) NOT NULL PRIMARY KEY,"
                    + "name VARCHAR(128) NOT NULL,"
                    + "type VARCHAR(16) NOT NULL,"
                    + "credential_ref VARCHAR(255),"
                    + "properties TEXT,"
                    + "created_at DATETIME NOT NULL,"
                    + "updated_at DATETIME NOT NULL)");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        store = new JdbcDataSourceDefStore(ds);
    }

    @Test
    void 增删改查闭环() {
        Map<String, String> props = new LinkedHashMap<>();
        props.put("host", "127.0.0.1");
        props.put("port", "3306");
        props.put("database", "demo");
        DataSourceDef def = new DataSourceDef("ds-1", "演示源", DataSourceType.JDBC, "dms-demo/mysql-main", props);

        store.save(def);
        Optional<DataSourceDef> found = store.find("ds-1");
        assertTrue(found.isPresent());
        assertEquals("dms-demo/mysql-main", found.get().getCredentialRef());
        assertEquals("demo", found.get().getProperties().get("database"));
        assertEquals(1, store.list().size());

        // 覆盖写（upsert）
        Map<String, String> props2 = new LinkedHashMap<>(props);
        props2.put("database", "demo2");
        store.save(new DataSourceDef("ds-1", "演示源2", DataSourceType.JDBC, "dms-demo/mysql-main", props2));
        assertEquals(1, store.list().size());
        assertEquals("demo2", store.find("ds-1").get().getProperties().get("database"));

        store.delete("ds-1");
        assertFalse(store.find("ds-1").isPresent());
        assertEquals(0, store.list().size());
    }

    @Test
    void 落库前闸门拦住明文() {
        Map<String, String> props = new LinkedHashMap<>();
        props.put("password", "sup3r-s3cret");
        DataSourceDef def = new DataSourceDef("ds-2", "坏定义", DataSourceType.JDBC, "g/k", props);
        assertThrows(IllegalArgumentException.class, () -> store.save(def));
        assertEquals(0, store.list().size(), "被拒的定义不得落库");
    }
}
