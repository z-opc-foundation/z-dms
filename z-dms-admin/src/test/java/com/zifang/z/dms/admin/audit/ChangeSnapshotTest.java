package com.zifang.z.dms.admin.audit;

import com.zifang.z.dms.core.model.DataSourceDef;
import com.zifang.z.dms.core.model.DataSourceType;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 变更历史的「前后值快照」守卫：<b>快照里不得出现明文凭据</b>。
 *
 * <p>正常路径上敏感键根本进不来（写入口 {@code DataSourceDefValidator} 会拒），
 * 这里验的是<b>纵深防御</b>那一层：万一有别的路径塞了敏感键进来，
 * 审计表里躺着的也只能是 {@code ***}。
 */
class ChangeSnapshotTest {

    @Test
    void 敏感键在快照里被打码() {
        Map<String, String> props = new LinkedHashMap<>();
        props.put("host", "127.0.0.1");
        props.put("password", "SHOULD-NEVER-APPEAR");
        props.put("api-key", "SHOULD-NEVER-APPEAR-EITHER");
        DataSourceDef def = new DataSourceDef("ds-1", "示例", DataSourceType.JDBC, "g/k", props);

        String json = ChangeSnapshot.of(def);

        assertFalse(json.contains("SHOULD-NEVER-APPEAR"), "快照里出现了明文口令: " + json);
        assertTrue(json.contains("***"), "敏感键应被打码: " + json);
        assertTrue(json.contains("127.0.0.1"), "非敏感属性应保留: " + json);
        assertTrue(json.contains("g/k"), "credentialRef 应保留（那是引用不是口令）: " + json);
    }

    @Test
    void 空定义返回null() {
        assertNull(ChangeSnapshot.of(null));
    }

    @Test
    void 快照里只有引用与白名单属性() {
        Map<String, String> props = new LinkedHashMap<>();
        props.put("host", "127.0.0.1");
        props.put("port", "3306");
        DataSourceDef def = new DataSourceDef("ds-2", "示例", DataSourceType.JDBC, "g/k2", props);

        String json = ChangeSnapshot.of(def);

        assertTrue(json.contains("\"id\":\"ds-2\""));
        assertTrue(json.contains("\"credentialRef\":\"g/k2\""));
        assertTrue(json.contains("\"properties\""));
    }
}
