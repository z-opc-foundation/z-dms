package com.zifang.z.dms.core.support;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zifang.z.dms.core.model.DataSourceDef;

import java.util.ArrayList;
import java.util.List;

/**
 * 定义的 JSON 形态（z-config 长连接推送的内容格式）。
 * <p>
 * <b>内容里只有 credentialRef（引用），永远没有明文口令。</b>
 * <p>
 * 格式：JSON 数组，每个元素是 {@code {id,name,type,credentialRef,properties}}。
 */
public final class DataSourceDefJson {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private DataSourceDefJson() {
    }

    public static String toJson(List<DataSourceDef> defs) {
        try {
            return MAPPER.writeValueAsString(defs == null ? new ArrayList<DataSourceDef>() : defs);
        } catch (Exception e) {
            throw new IllegalStateException("序列化数据源定义失败: " + e.getMessage(), e);
        }
    }

    public static List<DataSourceDef> fromJson(String json) {
        try {
            List<DataSourceDef> defs = MAPPER.readValue(json == null ? "[]" : json,
                    new TypeReference<ArrayList<DataSourceDef>>() {
                    });
            return defs == null ? new ArrayList<DataSourceDef>() : defs;
        } catch (Exception e) {
            throw new IllegalStateException("解析数据源定义 JSON 失败: " + e.getMessage(), e);
        }
    }
}
