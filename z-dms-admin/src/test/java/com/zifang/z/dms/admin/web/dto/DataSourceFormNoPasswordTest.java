package com.zifang.z.dms.admin.web.dto;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 形态 B 的**红线守卫**：登记 / 编辑 / 自测的入参里**不许有口令类字段**。
 *
 * <p>凭据面只有 {@code credentialRef}（z-mist 键名引用）。
 * 这条测试把「不许有口令输入框」从口头纪律变成<b>会红的断言</b>：
 * 以后谁在这个 DTO 上加一个 password 字段，构建立刻失败。
 */
class DataSourceFormNoPasswordTest {

    /** 口令类字段名（大小写不敏感）。 */
    private static final String[] FORBIDDEN = {
            "password", "passwd", "pwd", "pass", "secret", "token", "apikey", "api_key", "credential"
    };

    @Test
    void 表单入参没有任何口令类字段() {
        Set<String> names = new HashSet<>();
        for (Field field : DataSourceForm.class.getDeclaredFields()) {
            names.add(field.getName().toLowerCase(Locale.ROOT));
        }
        for (String bad : FORBIDDEN) {
            assertFalse(names.contains(bad),
                    "DataSourceForm 上不允许出现口令类字段 [" + bad + "]；凭据面只有 credentialRef");
        }
        assertTrue(names.contains("credentialref"), "凭据面必须是 credentialRef 引用");
    }

    @Test
    void 字段清单就是文档里那五个() {
        Set<String> names = new HashSet<>();
        for (Field field : DataSourceForm.class.getDeclaredFields()) {
            names.add(field.getName());
        }
        assertTrue(names.containsAll(Arrays.asList("id", "name", "type", "credentialRef", "properties")),
                "表单字段应恰好是 id / name / type / credentialRef / properties，实际 = " + names);
        assertTrue(names.size() == 5, "表单不应有多余字段，实际 = " + names);
    }
}
