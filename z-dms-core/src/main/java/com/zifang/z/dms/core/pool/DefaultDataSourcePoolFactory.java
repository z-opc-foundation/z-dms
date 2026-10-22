package com.zifang.z.dms.core.pool;

import com.zifang.z.dms.core.model.Credential;
import com.zifang.z.dms.core.credential.CredentialResolver;
import com.zifang.z.dms.core.model.DataSourceDef;


/**
 * 默认连接工厂：先按 credentialRef 解析凭据，再造 Druid 池。
 * <p>
 * 凭据只在建池那一刻被解出来并塞进池的 username/password，
 * <b>绝不写回 {@link DataSourceDef}</b>（008 notes.md §4.3 规则 2）。
 */
public class DefaultDataSourcePoolFactory implements DataSourcePoolFactory {

    private final CredentialResolver credentialResolver;

    public DefaultDataSourcePoolFactory(CredentialResolver credentialResolver) {
        this.credentialResolver = credentialResolver;
    }

    @Override
    public DataSourcePool create(DataSourceDef def) {
        Credential credential = credentialResolver.resolve(def.getCredentialRef());
        return new DruidDataSourcePool(def, credential);
    }
}
