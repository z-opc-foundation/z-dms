package com.zifang.z.dms.core;

import com.zifang.z.dms.core.model.DataSourceDef;
import com.zifang.z.dms.core.model.DataSourceType;
import com.zifang.z.dms.core.support.DataSourceDefValidator;
import com.zifang.z.dms.core.support.SensitivePropertyKeys;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验收第 3 项的写入口闸单测：<b>明文口令进不来</b>。
 */
class DataSourceDefValidatorTest {

    @Test
    void 敏感键必须被拒绝且错误消息不带值() {
        Map<String, String> props = new LinkedHashMap<>();
        props.put("host", "127.0.0.1");
        props.put("password", "sup3r-s3cret-value");
        DataSourceDef def = new DataSourceDef("ds-1", "demo", DataSourceType.JDBC, "g/k", props);

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> DataSourceDefValidator.validateForRegistration(def));
        assertTrue(e.getMessage().contains("password"), "错误消息应点名敏感键: " + e.getMessage());
        assertTrue(!e.getMessage().contains("sup3r-s3cret-value"), "错误消息绝不能带值: " + e.getMessage());
    }

    @Test
    void 各种大小写与连字符变体都要判敏感() {
        assertTrue(SensitivePropertyKeys.isSensitive("password"));
        assertTrue(SensitivePropertyKeys.isSensitive("Password"));
        assertTrue(SensitivePropertyKeys.isSensitive("PASSWORD"));
        assertTrue(SensitivePropertyKeys.isSensitive("pass-word"));
        assertTrue(SensitivePropertyKeys.isSensitive("access_token"));
        assertTrue(SensitivePropertyKeys.isSensitive("access-token"));
        assertTrue(SensitivePropertyKeys.isSensitive("secretKey"));
        assertTrue(SensitivePropertyKeys.isSensitive("api-key"));
        assertTrue(!SensitivePropertyKeys.isSensitive("host"));
        assertTrue(!SensitivePropertyKeys.isSensitive("database"));
        assertTrue(!SensitivePropertyKeys.isSensitive("max-active"));
    }

    @Test
    void jdbc源必须给credentialRef() {
        Map<String, String> props = new LinkedHashMap<>();
        props.put("host", "127.0.0.1");
        props.put("database", "demo");
        DataSourceDef def = new DataSourceDef("ds-2", "demo", DataSourceType.JDBC, null, props);

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> DataSourceDefValidator.validateForRegistration(def));
        assertTrue(e.getMessage().contains("credentialRef"), e.getMessage());
    }

    @Test
    void 合法定义能过() {
        Map<String, String> props = new LinkedHashMap<>();
        props.put("host", "127.0.0.1");
        props.put("database", "demo");
        DataSourceDef def = new DataSourceDef("ds-3", "demo", DataSourceType.JDBC, "dms-demo/mysql-main", props);
        DataSourceDefValidator.validateForRegistration(def);
    }

    @Test
    void toString不吐敏感值() {
        Map<String, String> props = new LinkedHashMap<>();
        props.put("token", "abc123secret");
        DataSourceDef def = new DataSourceDef("ds-4", "demo", DataSourceType.JDBC, "g/k", props);
        assertEquals("***", def.toString().contains("abc123secret") ? "abc123secret" : "***");
        assertTrue(!def.toString().contains("abc123secret"), def.toString());
    }
}
