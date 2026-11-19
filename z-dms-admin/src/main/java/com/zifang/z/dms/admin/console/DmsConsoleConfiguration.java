package com.zifang.z.dms.admin.console;

import com.zifang.z.dms.core.explorer.DataSourceServiceConnectionProvider;
import com.zifang.z.dms.core.explorer.DmsConnectionProvider;
import com.zifang.z.dms.core.explorer.SchemaExplorer;
import com.zifang.z.dms.core.explorer.SqlExecutor;
import com.zifang.z.dms.core.explorer.SqlStatementGuard;
import com.zifang.z.dms.core.explorer.TableDataReader;
import com.zifang.z.dms.core.service.DataSourceService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 数据浏览与 SQL 控制台的内核接线：全部复用 z-dms-core 的 explorer 包（纯 Java 8，无 Spring 依赖）。
 *
 * <p><b>读闸默认焊死</b>：{@link SqlStatementGuard} 的 bean 以 {@code readOnly=true} 构造 ——
 * 即使 {@code z.dms.console.write-enabled=true}，判定成 WRITE 的语句也要用户持有
 * {@code DMS_SQL_WRITE} 权限点才走 {@code check(sql, allowWrite)} 放行（在 Controller 里判）；
 * guard 本身永远拒绝「读权限跑写语句」。
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(DmsConsoleProperties.class)
public class DmsConsoleConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public DmsConnectionProvider dmsConnectionProvider(DataSourceService dataSourceService) {
        return new DataSourceServiceConnectionProvider(dataSourceService);
    }

    @Bean
    @ConditionalOnMissingBean
    public SchemaExplorer dmsSchemaExplorer(DmsConnectionProvider connectionProvider) {
        return new SchemaExplorer(connectionProvider);
    }

    @Bean
    @ConditionalOnMissingBean
    public TableDataReader dmsTableDataReader(DmsConnectionProvider connectionProvider,
                                              SchemaExplorer schemaExplorer,
                                              DmsConsoleProperties props) {
        TableDataReader reader = new TableDataReader(connectionProvider, schemaExplorer);
        reader.setMaxPageSize(props.getMaxPageSize());
        return reader;
    }

    @Bean
    @ConditionalOnMissingBean
    public SqlStatementGuard dmsSqlStatementGuard(DmsConsoleProperties props) {
        // readOnly=true：这个 guard 默认只放行读语句；写语句在 Controller 里凭 DMS_SQL_WRITE
        // 走 check(sql, allowWrite) 通道，guard 永远不凭空放行写。
        return new SqlStatementGuard(true, props.getMaxSqlLength(), props.getMaxRows());
    }

    @Bean
    @ConditionalOnMissingBean
    public SqlExecutor dmsSqlExecutor(DmsConnectionProvider connectionProvider,
                                      SqlStatementGuard guard,
                                      DmsConsoleProperties props) {
        SqlExecutor executor = new SqlExecutor(connectionProvider, guard);
        executor.setTimeoutSeconds(props.getTimeoutSeconds());
        return executor;
    }
}
