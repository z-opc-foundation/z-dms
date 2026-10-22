package com.zifang.z.dms.admin.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zifang.z.dms.core.model.DataSourceDef;
import com.zifang.z.dms.core.support.SensitivePropertyKeys;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 定义快照（变更历史的「前后值」）。
 *
 * <p>快照内容：{@code id / name / type / credentialRef / properties}。
 *
 * <p><b>纪律：快照里不得出现明文凭据。</b>
 * 敏感键（password / secret / token / …）在落快照前一律<b>打码</b>成 {@code ***}
 * —— 正常路径上敏感键根本进不来（{@code DataSourceDefValidator} 会在写入口拒掉），
 * 这里打码是<b>纵深防御</b>：万一有别的路径塞了进来，审计表里也不会躺着一个明文口令。
 */
public final class ChangeSnapshot {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ChangeSnapshot() {
    }

    /** 定义 → JSON 快照；def 为 null 时返回 null（新增的「前值」/ 删除的「后值」）。 */
    public static String of(DataSourceDef def) {
        if (def == null) {
            return null;
        }
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", def.getId());
        view.put("name", def.getName());
        view.put("type", def.getType() == null ? null : def.getType().name());
        view.put("credentialRef", def.getCredentialRef());
        Map<String, Object> props = new LinkedHashMap<>();
        if (def.getProperties() != null) {
            for (Map.Entry<String, String> e : def.getProperties().entrySet()) {
                if (SensitivePropertyKeys.isSensitive(e.getKey())) {
                    props.put(e.getKey(), "***");
                } else {
                    props.put(e.getKey(), e.getValue());
                }
            }
        }
        view.put("properties", props);
        try {
            return MAPPER.writeValueAsString(view);
        } catch (Exception e) {
            throw new IllegalStateException("序列化数据源定义快照失败: " + e.getMessage(), e);
        }
    }
}
