package com.zifang.z.dms.core.support;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 敏感键名判定（组织 008 凭证规范的落地口径）。
 * <p>
 * <b>为什么要有一份显式清单</b>：z-dms 的 {@code DataSourceDef.properties} 是泛化键值袋，
 * 只要允许它自由写入，就会有「有人把 password 塞进 properties 顺手入库」这条路。
 * TASK-20261012-009 盘点 §3.6 实测：全组织 {@code z.base.db.*} 名下已有 8 条明文凭据、
 * yml 里另有 {@code password: ${XXX_PASSWORD:}} 形态的兜底默认值 ⇒ 收口必须做在<b>写入口</b>。
 * <p>
 * 判定口径：键名归一化（小写、去掉 {@code -} 与 {@code _}）后落在清单里即判敏感。
 * 这样 {@code password} / {@code Password} / {@code access-token} / {@code accessToken} 一网打尽。
 */
public final class SensitivePropertyKeys {

    private static final Set<String> SENSITIVE;

    static {
        Set<String> s = new HashSet<>(Arrays.asList(
                "password", "passwd", "pwd", "pass",
                "secret", "secretkey", "credential", "credentials",
                "token", "accesstoken", "refreshtoken", "authtoken",
                "accesskey", "secretaccesskey", "apikey", "apisecret",
                "privatekey", "publickey", "authorization", "auth",
                "sessionkey", "signature", "salt"));
        SENSITIVE = Collections.unmodifiableSet(s);
    }

    private SensitivePropertyKeys() {
    }

    /** 归一化：小写 + 去掉 '-' 与 '_'。 */
    public static String normalize(String key) {
        if (key == null) {
            return "";
        }
        return key.toLowerCase(Locale.ROOT).replace("-", "").replace("_", "");
    }

    public static boolean isSensitive(String key) {
        return SENSITIVE.contains(normalize(key));
    }

    /** 返回 properties 里所有敏感键（按原始键名返回，便于报给人看）。 */
    public static List<String> findSensitive(java.util.Map<String, String> properties) {
        List<String> hits = new java.util.ArrayList<>();
        if (properties == null) {
            return hits;
        }
        for (String key : properties.keySet()) {
            if (isSensitive(key)) {
                hits.add(key);
            }
        }
        return hits;
    }
}
