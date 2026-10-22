package com.zifang.z.dms.starter.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * z-dms starter 的配置面（前缀 {@code z.dms}）。
 * <p>
 * <b>配置面里没有任何口令字段</b>：凭据只以 {@code credentialRef}（z-mist 键名）出现在
 * {@code z.dms.credential.*} 里；元数据库自身的口令走 {@code z.base.db.dms.password}，
 * 由环境变量 / 外部配置注入，仓内不落明文。
 */
@ConfigurationProperties(prefix = "z.dms")
public class DmsProperties {

    /** 总开关（默认开：starter 的意义就是「引了就用」）。 */
    private boolean enabled = true;

    /** 是否启用元数据库存储（登记落库）。默认关：宿主不配 {@code z.base.db.dms.*} 时也能用内存模式。 */
    private boolean storeEnabled = false;

    /** 定时探活间隔（毫秒）。 */
    private long probeIntervalMs = 60000L;

    /** 单池探活超时（毫秒）。 */
    private long probeTimeoutMs = 2000L;

    /** 健康端点路径（宿主可用 {@code z.dms.health-path} 改成自己的路径）。 */
    private String healthPath = "/dms/health";

    /** 凭据解析用的属性前缀：{@code <prefix><group>.<key>}。 */
    private String credentialPropertyPrefix = "z.dms.credential.";

    private final Mist mist = new Mist();
    private final Config config = new Config();

    /** z-mist 凭据解析（形态 A 的正路：credentialRef → z-mist 键名 → 密文）。 */
    public static class Mist {
        /** 是否启用 z-mist 客户端。 */
        private boolean enabled = false;
        private String host = "localhost";
        private int port = 9085;
        private String appName;
        private String appSecret;
        /** z-mist 命名空间。 */
        private String namespace = "default";
        /** z-mist 分组（credentialRef 的 group 段默认落这里）。 */
        private String group = "DEFAULT_GROUP";
        /** 是否允许明文回退（仅限本地自检；生产必须 false）。 */
        private boolean allowPlaintextFallback = false;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public String getAppName() {
            return appName;
        }

        public void setAppName(String appName) {
            this.appName = appName;
        }

        public String getAppSecret() {
            return appSecret;
        }

        public void setAppSecret(String appSecret) {
            this.appSecret = appSecret;
        }

        public String getNamespace() {
            return namespace;
        }

        public void setNamespace(String namespace) {
            this.namespace = namespace;
        }

        public String getGroup() {
            return group;
        }

        public void setGroup(String group) {
            this.group = group;
        }

        public boolean isAllowPlaintextFallback() {
            return allowPlaintextFallback;
        }

        public void setAllowPlaintextFallback(boolean allowPlaintextFallback) {
            this.allowPlaintextFallback = allowPlaintextFallback;
        }
    }

    /** z-config 长连接推送通道。 */
    public static class Config {
        /** 是否启用 z-config 通道（关掉则变更只在进程内生效）。 */
        private boolean enabled = false;
        /** 是否把本进程的变更回推 z-config。 */
        private boolean publish = false;
        /** z-config 服务地址（逗号分隔可多个）。 */
        private String serverAddr;
        /** z-config 命名空间。 */
        private String namespace = "default";
        /** z-config 分组。 */
        private String group = "DEFAULT_GROUP";
        /** z-config 上的 dataId（数据源定义全量 JSON 放这里）。 */
        private String dataId = "z-dms.datasource-defs";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isPublish() {
            return publish;
        }

        public void setPublish(boolean publish) {
            this.publish = publish;
        }

        public String getServerAddr() {
            return serverAddr;
        }

        public void setServerAddr(String serverAddr) {
            this.serverAddr = serverAddr;
        }

        public String getNamespace() {
            return namespace;
        }

        public void setNamespace(String namespace) {
            this.namespace = namespace;
        }

        public String getGroup() {
            return group;
        }

        public void setGroup(String group) {
            this.group = group;
        }

        public String getDataId() {
            return dataId;
        }

        public void setDataId(String dataId) {
            this.dataId = dataId;
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isStoreEnabled() {
        return storeEnabled;
    }

    public void setStoreEnabled(boolean storeEnabled) {
        this.storeEnabled = storeEnabled;
    }

    public long getProbeIntervalMs() {
        return probeIntervalMs;
    }

    public void setProbeIntervalMs(long probeIntervalMs) {
        this.probeIntervalMs = probeIntervalMs;
    }

    public long getProbeTimeoutMs() {
        return probeTimeoutMs;
    }

    public void setProbeTimeoutMs(long probeTimeoutMs) {
        this.probeTimeoutMs = probeTimeoutMs;
    }

    public String getHealthPath() {
        return healthPath;
    }

    public void setHealthPath(String healthPath) {
        this.healthPath = healthPath;
    }

    public String getCredentialPropertyPrefix() {
        return credentialPropertyPrefix;
    }

    public void setCredentialPropertyPrefix(String credentialPropertyPrefix) {
        this.credentialPropertyPrefix = credentialPropertyPrefix;
    }

    public Mist getMist() {
        return mist;
    }

    public Config getConfig() {
        return config;
    }
}
