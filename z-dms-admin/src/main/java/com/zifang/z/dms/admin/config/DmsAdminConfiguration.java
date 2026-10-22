package com.zifang.z.dms.admin.config;

import com.zifang.z.dms.admin.audit.AuditTrailService;
import com.zifang.z.dms.admin.audit.OperatorResolver;
import com.zifang.z.dms.admin.store.JdbcChangeLogStore;
import com.zifang.z.dms.admin.store.JdbcUsageLogStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * 形态 B 宿主的装配面。
 *
 * <p><b>元数据库连接只用 starter 装出来的那一个</b>（{@code dmsMetaDataSource}）：
 * 登记定义（{@code z_dms_datasource_def}）、变更流水、使用流水三张表都在同一个库里。
 *
 * <p>⚠️ 这里**不能再注册第二个 DataSource bean**：一旦上下文里出现两个 DataSource，
 * starter 那边按类型注入的 {@code dmsDataSourceDefStore} 就会抛
 * {@code NoUniqueBeanDefinitionException}（实测：宿主直接起不来）。
 * 审计面没连接可用时**明确报错**，而不是悄悄换一条连接串。
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(DmsAdminProperties.class)
public class DmsAdminConfiguration {

    @Bean
    public JdbcChangeLogStore dmsChangeLogStore(@Qualifier("dmsMetaDataSource") ObjectProvider<DataSource> metaDataSource) {
        return new JdbcChangeLogStore(requireMetaDataSource(metaDataSource));
    }

    @Bean
    public JdbcUsageLogStore dmsUsageLogStore(@Qualifier("dmsMetaDataSource") ObjectProvider<DataSource> metaDataSource) {
        return new JdbcUsageLogStore(requireMetaDataSource(metaDataSource));
    }

    @Bean
    public AuditTrailService dmsAuditTrailService(JdbcChangeLogStore changeLogStore,
                                                  JdbcUsageLogStore usageLogStore) {
        return new AuditTrailService(changeLogStore, usageLogStore);
    }

    @Bean
    public OperatorResolver dmsOperatorResolver(DmsAdminProperties props) {
        return new OperatorResolver(props);
    }

    private DataSource requireMetaDataSource(ObjectProvider<DataSource> metaDataSource) {
        DataSource ds = metaDataSource.getIfAvailable();
        if (ds == null) {
            throw new IllegalStateException(
                    "z-dms-admin 拒绝启动：审计面需要元数据库（表 z_dms_datasource_change_log / "
                            + "z_dms_datasource_usage_log），但上下文里没有 dmsMetaDataSource — "
                            + "请把 z.dms.store-enabled 设为 true，并配好 z.base.db.dms.*。");
        }
        return ds;
    }
}
