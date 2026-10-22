package com.zifang.z.dms.core.pool;

import com.alibaba.druid.pool.DruidDataSource;
import com.zifang.z.dms.core.model.Credential;
import com.zifang.z.dms.core.model.DataSourceDef;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;

/**
 * Druid 连接池实现（<b>对齐 z-boot 约定</b>）。
 * <p>
 * 约定出处：{@code z-boot/z-boot-starter/z-boot-datasource-starter/.../ModuleDataSourceTemplate}
 * —— 用 {@code com.alibaba.druid.pool.DruidDataSource}、URL 形如
 * {@code jdbc:mysql://host:port/database?serverTimezone=UTC&useUnicode=true&characterEncoding=utf-8}、
 * 池参数键名 {@code initial-size} / {@code min-idle} / {@code max-active} / {@code max-wait}、
 * 保活口径 {@code testWhileIdle=true} + {@code validationQuery=SELECT 1} +
 * {@code timeBetweenEvictionRunsMillis=30000} + {@code minEvictableIdleTimeMillis=60000} + {@code keepAlive=true}。
 * <p>
 * <b>护栏（学 z-lc {@code LcModuleDataSource} 的两道）：</b>
 * <ol>
 *   <li>连接串和库名都拿不到 ⇒ <b>拒绝建池</b>，不拼 {@code jdbc:mysql://localhost:3306/}
 *       那种「能起来但一条连接都用不了」的串（z-lc 实测症状：进程带着 0 条可用连接起来、
 *       {@code /api/lc/health} 照样回 UP，第一条业务查询才 500 Connection refused）；</li>
 *   <li>驱动与 URL 不配套 ⇒ <b>拒绝建池</b>。驱动不受理该 url 时 {@code connect()} 返回 null
 *       而不是抛异常，Druid 建连线程会无退避死循环（z-lc 实测 8 分钟 13 GB 日志）。</li>
 * </ol>
 */
public class DruidDataSourcePool implements DataSourcePool {

    /** z-boot 约定的 URL 参数串（ModuleDataSourceTemplate:24 逐字对齐）。 */
    public static final String URL_PARAMS = "?serverTimezone=UTC&useUnicode=true&characterEncoding=utf-8";

    private final DataSourceDef definition;
    private final DruidDataSource delegate;
    private volatile boolean closed;

    public DruidDataSourcePool(DataSourceDef definition, Credential credential) {
        this.definition = definition.copy();
        this.delegate = build(definition, credential);
    }

    private static DruidDataSource build(DataSourceDef def, Credential credential) {
        Map<String, String> props = def.getProperties();
        String url = resolveUrl(props);
        String driver = trimToNull(props.get("driver-class-name"));

        // 护栏 2：驱动与 URL 的配套性要在建池之前判。
        String mismatch = driverUrlMismatch(driver, url);
        if (mismatch != null) {
            throw new IllegalStateException(
                    "z-dms 拒绝建池：数据源 [" + def.getId() + "] 的驱动与 JDBC 串对不上 —— " + mismatch
                            + "。驱动不受理这个 url 时 connect() 返回 null 而不是抛异常，"
                            + "Druid 建连线程会在 rawConn is null 上无退避死循环（实测 8 分钟 13 GB 日志）。");
        }

        DruidDataSource ds = new DruidDataSource();
        ds.setUrl(url);
        ds.setUsername(credential.getUsername());
        ds.setPassword(credential.getPassword());
        if (driver != null) {
            ds.setDriverClassName(driver);
        }
        // ⚠️ 与 z-boot 约定的<b>一处刻意偏差</b>：z-boot 是「应用自身库」，用 initial-size=5 / min-idle=5
        // 保证进程一起来就有连接；z-dms 管的是<b>外部业务数据源</b>，那个库可能本来就连不通
        // （下线、故障、凭据未就位）。min-idle>0 会让 Druid 的建连线程在后台<b>无退避地重试</b> ——
        // z-lc 实测那种情形 8 分钟打了 13 GB 日志（z-lc README 记录的驱动/URL 不配套事故）。
        // 所以这里默认 initial-size=0 / min-idle=0：连接按需创建，失败只影响那一次探活，
        // 不产生后台日志风暴。池参数仍用 z-boot 的键名，且可按数据源逐个覆盖。
        ds.setInitialSize(intProp(props, "initial-size", 0));
        ds.setMinIdle(intProp(props, "min-idle", 0));
        ds.setMaxActive(intProp(props, "max-active", 20));
        ds.setMaxWait(longProp(props, "max-wait", 60000L));
        // 连不上时 Druid 建连线程的重试间隔（默认 500ms 一次，太吵）
        ds.setTimeBetweenConnectErrorMillis(longProp(props, "time-between-connect-error-millis", 60000L));
        // 保活口径（与 z-boot 一致）
        ds.setTestWhileIdle(true);
        ds.setValidationQuery("SELECT 1");
        ds.setKeepAlive(true);
        ds.setTimeBetweenEvictionRunsMillis(30000L);
        ds.setMinEvictableIdleTimeMillis(60000L);
        ds.setRemoveAbandoned(boolProp(props, "remove-abandoned", false));
        ds.setRemoveAbandonedTimeout(intProp(props, "remove-abandoned-timeout", 300));
        return ds;
    }

    /**
     * 解析连接串：显式 {@code jdbc-url} 优先，否则用 host/port/database 拼。
     * <p>两者都拿不到 ⇒ 抛异常（护栏 1）。
     */
    public static String resolveUrl(Map<String, String> props) {
        String url = trimToNull(props.get("jdbc-url"));
        if (url == null) {
            url = trimToNull(props.get("url"));
        }
        if (url != null) {
            if (!url.startsWith("jdbc:")) {
                throw new IllegalStateException(
                        "z-dms 拒绝建池：jdbc-url 不是 jdbc: 开头的串（实际前缀不可打印，见 properties 键 jdbc-url）。");
            }
            return url;
        }
        String host = trimToNull(props.get("host"));
        String database = trimToNull(props.get("database"));
        if (host == null || database == null) {
            throw new IllegalStateException(
                    "z-dms 拒绝建池：数据源没有可用的连接串 —— properties 里既没有 jdbc-url，"
                            + "也没有同时给出 host 与 database。拒绝拼默认串 jdbc:mysql://localhost:3306/"
                            + "（无库名）：实测那样进程带着 0 条可用连接起来、健康检查照样回 UP，"
                            + "第一条业务查询才 Connection refused。");
        }
        int port = intProp(props, "port", 3306);
        return "jdbc:mysql://" + host + ":" + port + "/" + database + URL_PARAMS;
    }

    /**
     * 驱动与 URL 的配套性检查（对齐 z-lc {@code DataSourceConfigGuard#driverUrlMismatch} 的口径）。
     *
     * @return 不配套时给出说明；配套或无法判定时返回 null
     */
    public static String driverUrlMismatch(String driver, String url) {
        if (driver == null || url == null) {
            return null;
        }
        String d = driver.toLowerCase();
        String u = url.toLowerCase();
        boolean mysqlDriver = d.contains("mysql") || d.contains("mariadb");
        boolean mysqlUrl = u.startsWith("jdbc:mysql:") || u.startsWith("jdbc:mariadb:");
        boolean h2Driver = d.contains("h2");
        boolean h2Url = u.startsWith("jdbc:h2:");
        if (h2Driver && mysqlUrl) {
            return "驱动是 H2 而连接串是 MySQL";
        }
        if (mysqlDriver && h2Url) {
            return "驱动是 MySQL 而连接串是 H2";
        }
        return null;
    }

    private static int intProp(Map<String, String> props, String key, int def) {
        String v = trimToNull(props.get(key));
        if (v == null) {
            return def;
        }
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            throw new IllegalStateException("数据源池参数 " + key + " 不是整数: " + v);
        }
    }

    private static long longProp(Map<String, String> props, String key, long def) {
        String v = trimToNull(props.get(key));
        if (v == null) {
            return def;
        }
        try {
            return Long.parseLong(v.trim());
        } catch (NumberFormatException e) {
            throw new IllegalStateException("数据源池参数 " + key + " 不是整数: " + v);
        }
    }

    private static boolean boolProp(Map<String, String> props, String key, boolean def) {
        String v = trimToNull(props.get(key));
        return v == null ? def : Boolean.parseBoolean(v.trim());
    }

    private static String trimToNull(String v) {
        if (v == null) {
            return null;
        }
        String t = v.trim();
        return t.isEmpty() ? null : t;
    }

    @Override
    public DataSourceDef definition() {
        return definition;
    }

    @Override
    public DataSource dataSource() {
        return delegate;
    }

    @Override
    public Connection getConnection() throws SQLException {
        if (closed) {
            throw new SQLException("数据源池已关闭: " + definition.getId());
        }
        return delegate.getConnection();
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        delegate.close();
    }

    /** 测试与诊断用：直接拿到底层 Druid 池。 */
    public DruidDataSource druid() {
        return delegate;
    }

    /**
     * 不经池直连一次，把根因说出来（见 {@link DataSourcePool#diagnose()}）。
     * <p>
     * 用 2 秒 connectTimeout 打一次 {@code DriverManager} 连接：连不上就拿到真正的错误
     * （Connection refused / Access denied / Unknown database），而不是池里重试到 maxWait 之后的
     * 一句「timed out」。
     */
    @Override
    public String diagnose() {
        String url = delegate.getUrl();
        if (url == null) {
            return null;
        }
        String probeUrl = url;
        if (!probeUrl.contains("connectTimeout=")) {
            probeUrl = probeUrl + (probeUrl.contains("?") ? "&" : "?") + "connectTimeout=2000&socketTimeout=3000";
        }
        long startNs = System.nanoTime();
        try (java.sql.Connection conn = java.sql.DriverManager.getConnection(probeUrl,
                delegate.getUsername(), delegate.getPassword())) {
            long latencyMs = (System.nanoTime() - startNs) / 1_000_000L;
            return "直连成功 (" + latencyMs + "ms)";
        } catch (Exception e) {
            // 一路走到最深的根因（CommunicationsException → ConnectException: Connection refused）
            Throwable root = e;
            int guard = 0;
            while (root.getCause() != null && root.getCause() != root && guard++ < 8) {
                root = root.getCause();
            }
            String msg = root.getMessage();
            return root.getClass().getSimpleName() + ": " + brief(msg);
        }
    }

    private static String brief(String message) {
        if (message == null) {
            return "";
        }
        String flat = message.replace('\n', ' ').replace('\r', ' ').trim();
        return flat.length() <= 300 ? flat : flat.substring(0, 300) + "…";
    }
}
