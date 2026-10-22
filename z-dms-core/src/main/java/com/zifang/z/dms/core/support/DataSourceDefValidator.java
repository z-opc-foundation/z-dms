package com.zifang.z.dms.core.support;

import com.zifang.z.dms.core.model.DataSourceDef;
import com.zifang.z.dms.core.model.DataSourceType;

import java.util.List;
import java.util.Map;

/**
 * {@link DataSourceDef} 写入口校验。
 * <p>
 * <b>三条硬规则</b>（008 notes.md §4.3）：
 * <ol>
 *   <li><b>写入口零明文</b>：properties 里出现敏感键 ⇒ 直接拒绝入库并报错，<b>不做静默丢弃</b>；</li>
 *   <li><b>读出口只解析引用</b>：真正建连前用 credentialRef 去凭据库取凭据，<b>不写回 def</b>；</li>
 *   <li><b>明文只允许存在于非持久化的内存态与凭据库本身</b>。</li>
 * </ol>
 * 规则 1 由本类实现；规则 2/3 由 {@code CredentialResolver} + {@code DruidDataSourcePool} 保证。
 */
public final class DataSourceDefValidator {

    /** JDBC 源必须给凭据引用（否则建连必然失败，且失败原因会被误读成网络问题）。 */
    private static final String CREDENTIAL_REF_FORMAT = "<group>/<key>";

    private DataSourceDefValidator() {
    }

    /** 基础校验：任何形态都要过。 */
    public static void validate(DataSourceDef def) {
        if (def == null) {
            throw new IllegalArgumentException("DataSourceDef 不能为空");
        }
        if (isBlank(def.getId())) {
            throw new IllegalArgumentException("DataSourceDef.id 不能为空");
        }
        if (isBlank(def.getName())) {
            throw new IllegalArgumentException("DataSourceDef.name 不能为空（id=" + def.getId() + "）");
        }
        if (def.getType() == null) {
            throw new IllegalArgumentException("DataSourceDef.type 不能为空（id=" + def.getId() + "）");
        }
        Map<String, String> props = def.getProperties();
        if (props == null) {
            throw new IllegalArgumentException("DataSourceDef.properties 不能为 null（id=" + def.getId() + "）");
        }
        List<String> sensitive = SensitivePropertyKeys.findSensitive(props);
        if (!sensitive.isEmpty()) {
            // 报错消息只列键名，绝不把值带出来（值可能是口令）。
            throw new IllegalArgumentException(
                    "拒绝登记数据源 [" + def.getId() + "]：properties 里出现敏感键 " + sensitive
                            + "。凭据只能走 credentialRef（指向 z-mist 键名，格式 " + CREDENTIAL_REF_FORMAT
                            + "），明文一律不入库。要迁走的现状明文键见 008 notes.md §4.3。");
        }
    }

    /** 登记/更新前的完整校验：基础校验 + 按类型的额外要求。 */
    public static void validateForRegistration(DataSourceDef def) {
        validate(def);
        if (def.getType() == DataSourceType.JDBC) {
            if (isBlank(def.getCredentialRef())) {
                throw new IllegalArgumentException(
                        "JDBC 数据源 [" + def.getId() + "] 必须给 credentialRef（指向 z-mist 键名，格式 "
                                + CREDENTIAL_REF_FORMAT + "）；不接受 properties 里的明文口令。");
            }
            if (!def.getCredentialRef().contains("/")) {
                throw new IllegalArgumentException(
                        "credentialRef 格式应为 " + CREDENTIAL_REF_FORMAT + "（group/key），实际 = "
                                + def.getCredentialRef());
            }
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
