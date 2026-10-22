package com.zifang.z.dms.starter.config;

import com.alibaba.druid.pool.DruidDataSource;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;

import javax.sql.DataSource;

/**
 * 元数据库（数据源定义的存储）连接装配：<b>键名 {@code z.base.db.dms.*}</b>。
 * <p>
 * 取值顺序与 {@code z-boot} 的 {@code ModuleDataSourceTemplate} 一致：
 * 模块段 {@code z.base.db.dms.*} → 缺省段 {@code z.base.db.default.*} → 代码兜底。
 * 连接串优先显式 {@code jdbc-url}（z-lc 的教训：模板只读 host/port 会让 jdbc-url 静默失效）。
 * <p>
 * <b>口令面</b>：{@code z.base.db.dms.password} 的值由环境变量 / 外部配置注入，
 * 本仓（yml / 代码 / 文档）不落明文。
 */
public final class DmsMetaDataSourceFactory {

    private DmsMetaDataSourceFactory() {
    }

    public static DataSource create(Environment environment) {
        Binder binder = Binder.get(environment);
        String jdbcUrl = firstString(binder,
                "z.base.db.dms.jdbc-url", "z.base.db.default.jdbc-url", "spring.datasource.url");
        String host = firstString(binder, "z.base.db.dms.host", "z.base.db.default.host");
        String database = firstString(binder, "z.base.db.dms.database", "z.base.db.default.database");
        if (jdbcUrl == null) {
            if (host == null || database == null) {
                throw new IllegalStateException(
                        "z-dms 拒绝启动：元数据库没有可用的连接串 —— z.base.db.dms.jdbc-url / "
                                + "z.base.db.default.jdbc-url / spring.datasource.url 三个都没有，"
                                + "且 z.base.db.dms.host 与 z.base.db.dms.database 没有同时给出。"
                                + "（不想用元数据库就把 z.dms.store-enabled 设成 false。）");
            }
            int port = binder.bind("z.base.db.dms.port", Integer.class)
                    .orElseGet(() -> binder.bind("z.base.db.default.port", Integer.class).orElse(3306));
            jdbcUrl = "jdbc:mysql://" + host + ":" + port + "/" + database
                    + "?serverTimezone=UTC&useUnicode=true&characterEncoding=utf-8";
        }
        DruidDataSource ds = new DruidDataSource();
        ds.setUrl(jdbcUrl);
        String username = firstString(binder,
                "z.base.db.dms.username", "z.base.db.default.username", "spring.datasource.username");
        String password = firstString(binder,
                "z.base.db.dms.password", "z.base.db.default.password", "spring.datasource.password");
        ds.setUsername(username == null ? "root" : username);
        ds.setPassword(password == null ? "" : password);
        String driver = firstString(binder,
                "z.base.db.dms.driver-class-name", "z.base.db.default.driver-class-name",
                "spring.datasource.driver-class-name");
        if (driver != null) {
            ds.setDriverClassName(driver);
        }
        ds.setInitialSize(1);
        ds.setMinIdle(1);
        ds.setMaxActive(10);
        ds.setMaxWait(60000L);
        ds.setTestWhileIdle(true);
        ds.setValidationQuery("SELECT 1");
        return ds;
    }

    private static String firstString(Binder binder, String... keys) {
        for (String key : keys) {
            String v = binder.bind(key, String.class).orElse(null);
            if (v != null && !v.trim().isEmpty()) {
                return v;
            }
        }
        return null;
    }
}
