package com.zifang.z.dms.core;

import com.zifang.z.dms.core.pool.DruidDataSourcePool;
import com.zifang.z.dms.core.model.Credential;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 连接工厂的两道护栏（学 z-lc LcModuleDataSource）：
 * ① 拿不到连接串/库名 ⇒ 拒绝建池，不拼「能起来但一条连接都用不了」的默认串；
 * ② 驱动与 URL 不配套 ⇒ 拒绝建池，避免 Druid 建连线程无退避死循环。
 */
class DruidDataSourcePoolTest {

    @Test
    void 没有host与database时拒绝建池() {
        Map<String, String> props = new LinkedHashMap<>();
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> new DruidDataSourcePool(def(props), Credential.of("u", "p")));
        assertTrue(e.getMessage().contains("拒绝建池"), e.getMessage());
    }

    @Test
    void 驱动与url不配套时拒绝建池() {
        Map<String, String> props = new LinkedHashMap<>();
        props.put("jdbc-url", "jdbc:mysql://127.0.0.1:3306/demo");
        props.put("driver-class-name", "org.h2.Driver");
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> new DruidDataSourcePool(def(props), Credential.of("u", "p")));
        assertTrue(e.getMessage().contains("驱动"), e.getMessage());
    }

    @Test
    void url拼装对齐zboot约定() {
        Map<String, String> props = new LinkedHashMap<>();
        props.put("host", "10.0.0.9");
        props.put("port", "3307");
        props.put("database", "oc");
        String url = DruidDataSourcePool.resolveUrl(props);
        assertEquals("jdbc:mysql://10.0.0.9:3307/oc" + DruidDataSourcePool.URL_PARAMS, url);
        assertTrue(url.contains("serverTimezone=UTC"), url);
    }

    @Test
    void 显式jdbcurl优先() {
        Map<String, String> props = new LinkedHashMap<>();
        props.put("host", "10.0.0.9");
        props.put("database", "oc");
        props.put("jdbc-url", "jdbc:mysql://10.0.0.9:3306/other?x=1");
        assertEquals("jdbc:mysql://10.0.0.9:3306/other?x=1", DruidDataSourcePool.resolveUrl(props));
    }

    @Test
    void 驱动配套判定() {
        assertNotNull(DruidDataSourcePool.driverUrlMismatch("org.h2.Driver", "jdbc:mysql://h:3306/d"));
        assertNotNull(DruidDataSourcePool.driverUrlMismatch("com.mysql.cj.jdbc.Driver", "jdbc:h2:mem:x"));
        assertEquals(null, DruidDataSourcePool.driverUrlMismatch("com.mysql.cj.jdbc.Driver", "jdbc:mysql://h:3306/d"));
        assertEquals(null, DruidDataSourcePool.driverUrlMismatch(null, "jdbc:mysql://h:3306/d"));
    }

    private static com.zifang.z.dms.core.model.DataSourceDef def(Map<String, String> props) {
        return new com.zifang.z.dms.core.model.DataSourceDef("ds", "ds",
                com.zifang.z.dms.core.model.DataSourceType.JDBC, "g/k", props);
    }
}
