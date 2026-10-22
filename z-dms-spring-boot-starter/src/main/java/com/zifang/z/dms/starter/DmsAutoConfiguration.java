package com.zifang.z.dms.starter;

import com.zifang.z.dms.core.cache.DataSourceDefinitionCache;
import com.zifang.z.dms.core.credential.CredentialResolver;
import com.zifang.z.dms.core.event.DataSourceChangePublisher;
import com.zifang.z.dms.core.health.DataSourceHealthProber;
import com.zifang.z.dms.core.model.DataSourceDef;
import com.zifang.z.dms.core.pool.DataSourcePoolFactory;
import com.zifang.z.dms.core.pool.DefaultDataSourcePoolFactory;
import com.zifang.z.dms.core.registry.DataSourceRegistry;
import com.zifang.z.dms.core.service.DataSourceService;
import com.zifang.z.dms.core.store.DataSourceDefStore;
import com.zifang.z.dms.core.store.JdbcDataSourceDefStore;
import com.zifang.z.dms.starter.config.DmsMetaDataSourceFactory;
import com.zifang.z.dms.starter.config.DmsProperties;
import com.zifang.z.dms.starter.credential.EnvironmentCredentialResolver;
import com.zifang.z.dms.starter.credential.MistCredentialResolver;
import com.zifang.z.dms.starter.credential.MistSecretSource;
import com.zifang.z.dms.starter.health.DataSourceHealthScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import javax.sql.DataSource;
import java.util.List;

/**
 * z-dms 形态 A 的自动装配：<b>宿主一行依赖引入即用</b>。
 * <p>
 * 宿主侧只需要：
 * <pre>
 * &lt;dependency&gt;
 *   &lt;groupId&gt;io.github.yuku123&lt;/groupId&gt;
 *   &lt;artifactId&gt;z-dms-spring-boot-starter&lt;/artifactId&gt;
 *   &lt;version&gt;${z-dms.version}&lt;/version&gt;
 * &lt;/dependency&gt;
 * </pre>
 * 然后 {@code @Autowired DataSourceService} 就能登记 / 取连接 / 探活。
 * <p>
 * 装出来的能力：登记 CRUD（含 MySQL 存储，键 {@code z.base.db.dms.*}）、
 * Druid 连接工厂（注册即探活、换绑关旧池）、本地定义缓存 + z-config 长连接推送、
 * 定时探活（失败打 ERROR 日志）、宿主健康端点 {@code /dms/health} 与 actuator 贡献。
 * <p>
 * <b>装配纪律</b>：凡是引用了可选依赖（z-mist / z-config / actuator / web）类型的 bean，
 * 一律放进带 {@code @ConditionalOnClass} 的<b>嵌套配置类</b>里 ——
 * 否则宿主 classpath 上没有那个 jar 时，Spring 解析本类的方法签名就会 NoClassDefFoundError。
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(DmsProperties.class)
@ConditionalOnProperty(prefix = "z.dms", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DmsAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(DmsAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public DataSourceRegistry dmsRegistry() {
        return new DataSourceRegistry();
    }

    @Bean
    @ConditionalOnMissingBean
    public DataSourceHealthProber dmsHealthProber(DmsProperties props) {
        return new DataSourceHealthProber(props.getProbeTimeoutMs());
    }

    @Bean
    @ConditionalOnMissingBean
    public DataSourceDefinitionCache dmsDefinitionCache() {
        return new DataSourceDefinitionCache();
    }

    /**
     * 凭据解析器：z-mist 优先（形态 A 正路），否则退到 Environment（离线自检）。
     * <p>两个实现都<b>只把明文留在内存里</b>。
     */
    @Bean
    @ConditionalOnMissingBean
    public CredentialResolver dmsCredentialResolver(DmsProperties props, Environment environment,
                                                    ObjectProvider<MistSecretSource> mistSecretSource) {
        if (props.getMist().isEnabled()) {
            MistSecretSource source = mistSecretSource.getIfAvailable();
            if (source == null) {
                throw new IllegalStateException(
                        "z.dms.mist.enabled=true 但 classpath 里没有 z-mist 客户端 —— "
                                + "请加依赖 io.github.yuku123:z-mist-client，或把 z.dms.mist.enabled 设成 false。");
            }
            log.info("z-dms 凭据解析走 z-mist（host={} port={}）", props.getMist().getHost(),
                    props.getMist().getPort());
            return new MistCredentialResolver(source, props.getMist().getGroup(),
                    props.getMist().getNamespace());
        }
        log.info("z-dms 凭据解析走 Environment（属性前缀 {}）——生产口径应改用 z-mist",
                props.getCredentialPropertyPrefix());
        return new EnvironmentCredentialResolver(environment, props.getCredentialPropertyPrefix());
    }

    @Bean
    @ConditionalOnMissingBean
    public DataSourcePoolFactory dmsPoolFactory(CredentialResolver credentialResolver) {
        return new DefaultDataSourcePoolFactory(credentialResolver);
    }

    /** 元数据库连接（键 z.base.db.dms.*）。 */
    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(prefix = "z.dms", name = "store-enabled", havingValue = "true")
    @ConditionalOnMissingBean(name = "dmsMetaDataSource")
    public DataSource dmsMetaDataSource(Environment environment) {
        return DmsMetaDataSourceFactory.create(environment);
    }

    @Bean
    @ConditionalOnProperty(prefix = "z.dms", name = "store-enabled", havingValue = "true")
    @ConditionalOnMissingBean
    public DataSourceDefStore dmsDataSourceDefStore(ObjectProvider<DataSource> metaDataSource) {
        DataSource ds = metaDataSource.getIfAvailable();
        if (ds == null) {
            throw new IllegalStateException("z.dms.store-enabled=true 但没有元数据库 DataSource bean");
        }
        return new JdbcDataSourceDefStore(ds);
    }

    /**
     * 主服务。
     * <p>
     * <b>发布方向用 ObjectProvider 延迟取</b>：publisher 可能由 z-config 那个嵌套配置类注册，
     * 而那个配置类又需要本服务 ⇒ 构造期直接注入会成环。延迟到「真有变更要发布」时再取。
     */
    @Bean
    @ConditionalOnMissingBean
    public DataSourceService dmsService(DataSourceRegistry registry,
                                       DataSourcePoolFactory poolFactory,
                                       DataSourceHealthProber prober,
                                       DataSourceDefinitionCache cache,
                                       ObjectProvider<DataSourceDefStore> storeProvider,
                                       ObjectProvider<DataSourceChangePublisher> publisherProvider) {
        DataSourceChangePublisher lazyPublisher = event -> {
            DataSourceChangePublisher publisher = publisherProvider.getIfAvailable();
            if (publisher != null) {
                publisher.publish(event);
            }
        };
        DataSourceService service = new DataSourceService(registry, poolFactory, prober, cache,
                storeProvider.getIfAvailable(), lazyPublisher);
        // 启动时把元数据库里的定义装进本地缓存（每个都探活；失败要看得见）
        DataSourceDefStore store = storeProvider.getIfAvailable();
        if (store != null) {
            try {
                List<DataSourceDef> defs = store.list();
                for (DataSourceDef def : defs) {
                    try {
                        service.register(def);
                    } catch (Exception e) {
                        log.error("z-dms 启动装载数据源 [{}] 失败: {}", def.getId(), e.getMessage());
                    }
                }
                log.info("z-dms 启动装载了 {} 条数据源定义", defs.size());
            } catch (Exception e) {
                log.error("z-dms 启动装载数据源定义失败（不影响宿主启动）: {}", e.getMessage());
            }
        }
        return service;
    }

    @Bean(destroyMethod = "stop")
    @ConditionalOnMissingBean
    public DataSourceHealthScheduler dmsHealthScheduler(DataSourceService service, DmsProperties props) {
        DataSourceHealthScheduler scheduler = new DataSourceHealthScheduler(service, props.getProbeIntervalMs());
        scheduler.start();
        return scheduler;
    }

    // ------------------------------------------------------------- web / actuator

    /** 宿主可见的健康端点（引用 web 类型的 bean 单独放一个条件配置类里）。 */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.springframework.web.bind.annotation.RestController")
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    static class WebConfiguration {

        @Bean
        @ConditionalOnMissingBean
        public com.zifang.z.dms.starter.health.DmsHealthController dmsHealthController(
                DataSourceService service) {
            return new com.zifang.z.dms.starter.health.DmsHealthController(service);
        }
    }

    /** actuator 健康贡献：有池 DOWN ⇒ 整体 DOWN（actuator 回 503）。 */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.springframework.boot.actuate.health.HealthIndicator")
    static class ActuatorConfiguration {

        @Bean
        @ConditionalOnMissingBean
        public com.zifang.z.dms.starter.health.DmsHealthIndicator dmsHealthIndicator(
                DataSourceService service) {
            return new com.zifang.z.dms.starter.health.DmsHealthIndicator(service);
        }
    }

    // ------------------------------------------------------------------ z-mist

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "com.zifang.z.mist.client.config.MistClient")
    @ConditionalOnProperty(prefix = "z.dms.mist", name = "enabled", havingValue = "true")
    static class MistClientConfiguration {

        @Bean(destroyMethod = "stop")
        @ConditionalOnMissingBean
        public MistSecretSource dmsMistSecretSource(DmsProperties props) {
            DmsProperties.Mist mist = props.getMist();
            if (mist.getAppName() == null || mist.getAppSecret() == null) {
                throw new IllegalStateException(
                        "z.dms.mist.enabled=true 需要 z.dms.mist.app-name 与 z.dms.mist.app-secret"
                                + "（app-secret 请由环境变量注入，仓内不落明文）");
            }
            com.zifang.z.mist.client.config.MistClient client = new com.zifang.z.mist.client.config.MistClient(
                    mist.getHost(), mist.getPort(), mist.getAppName(), mist.getAppSecret());
            client.start();
            return new MistSecretSource() {
                @Override
                public String getSecret(String secretKey, String group, String namespace) {
                    return client.getSecret(secretKey, group, namespace);
                }
            };
        }
    }

    // ---------------------------------------------------------------- z-config

    /**
     * z-config 长连接通道（定义变更推送）。
     * <p>
     * bridge 用 {@code ObjectProvider<DataSourceService>} 拿服务（延迟），
     * 避免「服务 → publisher → bridge → 服务」的构造期循环。
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "com.zifang.z.config.client.config.ZConfigService")
    @ConditionalOnProperty(prefix = "z.dms.config", name = "enabled", havingValue = "true")
    static class ZConfigConfiguration {

        @Bean(destroyMethod = "shutDown")
        @ConditionalOnMissingBean
        public com.zifang.z.config.client.config.ZConfigService dmsZConfigService(DmsProperties props) {
            DmsProperties.Config cfg = props.getConfig();
            if (cfg.getServerAddr() == null || cfg.getServerAddr().trim().isEmpty()) {
                throw new IllegalStateException(
                        "z.dms.config.enabled=true 需要 z.dms.config.server-addr（z-config 服务地址）");
            }
            java.util.Properties p = new java.util.Properties();
            p.setProperty("serverAddr", cfg.getServerAddr());
            p.setProperty("namespace", cfg.getNamespace());
            return com.zifang.z.config.client.config.ZConfigFactory.createConfigService(p);
        }

        @Bean
        @ConditionalOnMissingBean
        public com.zifang.z.dms.starter.event.ZConfigDataSourceChangeBridge dmsZConfigBridge(
                com.zifang.z.config.client.config.ZConfigService zConfigService,
                ObjectProvider<DataSourceService> serviceProvider,
                DmsProperties props) {
            DmsProperties.Config cfg = props.getConfig();
            com.zifang.z.dms.starter.event.ZConfigDataSourceChangeBridge bridge =
                    new com.zifang.z.dms.starter.event.ZConfigDataSourceChangeBridge(
                            zConfigService, serviceProvider, cfg.getGroup(), cfg.getDataId(), cfg.isPublish());
            bridge.start();
            return bridge;
        }

        @Bean
        @ConditionalOnMissingBean(DataSourceChangePublisher.class)
        public DataSourceChangePublisher dmsZConfigChangePublisher(
                com.zifang.z.dms.starter.event.ZConfigDataSourceChangeBridge bridge) {
            return new com.zifang.z.dms.starter.event.ZConfigDataSourceChangePublisher(bridge);
        }
    }
}
