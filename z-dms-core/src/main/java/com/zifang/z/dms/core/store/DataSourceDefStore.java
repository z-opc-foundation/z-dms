package com.zifang.z.dms.core.store;

import com.zifang.z.dms.core.model.DataSourceDef;

import java.util.List;
import java.util.Optional;

/**
 * 数据源定义的持久化（登记 CRUD 的存储面）。
 * <p>
 * 存储介质：MySQL，键名 {@code z.base.db.dms.*}（starter 侧装配，见
 * {@code DmsMetaDataSourceFactory}）；表结构见 {@code _doc/002_deploy/init/}。
 * <p>
 * <b>落库的内容永远不含明文凭据</b>：只有 credential_ref（z-mist 键名）。
 */
public interface DataSourceDefStore {

    /** 全部定义。 */
    List<DataSourceDef> list();

    /** 按 id 取一个。 */
    Optional<DataSourceDef> find(String id);

    /** 新增或覆盖（upsert）。 */
    void save(DataSourceDef def);

    /** 删除。 */
    void delete(String id);
}
