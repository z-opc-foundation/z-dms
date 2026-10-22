package com.zifang.z.dms.core.model;

import com.zifang.z.dms.core.support.SensitivePropertyKeys;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * z-dms 数据源定义（数据源板块的管理单元）。
 * <p>
 * <b>对齐 TASK-20261012-009 的 DataSourceDef 草案（008 notes.md §4.2）</b>：
 * 沿用 z-lc report 子域同名同字段（{@code id} / {@code name} / {@code type} / {@code properties}），
 * <b>新增 {@code credentialRef}</b> —— 凭据面收口的关键：
 * 本实体<b>只存 z-mist 键名（引用），永不存明文口令</b>。
 * <p>
 * z-lc 现状是「泛化 properties 袋 + 明文 username/password 躺在袋里」，
 * 本仓把明文迁走：{@code properties} 只允许非敏感白名单键，
 * 敏感键在 {@link com.zifang.z.dms.core.support.DataSourceDefValidator} 里<b>直接拒绝</b>（不静默丢弃）。
 */
public class DataSourceDef {

    private String id;
    private String name;
    private DataSourceType type;

    /**
     * 凭据引用：指向凭据库（z-mist）里的一条记录，格式 {@code <group>/<key>}。
     * <p>可空（MEMORY / CSV / HTTP 源无需凭据）；JDBC 源必填。
     * <p><b>本字段任何时候都不得被替换成明文口令。</b>
     */
    private String credentialRef;

    /** 非敏感连接属性白名单；值也必须是非敏感项。 */
    private Map<String, String> properties = new LinkedHashMap<>();

    public DataSourceDef() {
    }

    public DataSourceDef(String id, String name, DataSourceType type, String credentialRef,
                         Map<String, String> properties) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.credentialRef = credentialRef;
        if (properties != null) {
            this.properties = new LinkedHashMap<>(properties);
        }
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public DataSourceType getType() {
        return type;
    }

    public void setType(DataSourceType type) {
        this.type = type;
    }

    public String getCredentialRef() {
        return credentialRef;
    }

    public void setCredentialRef(String credentialRef) {
        this.credentialRef = credentialRef;
    }

    public Map<String, String> getProperties() {
        return properties;
    }

    public void setProperties(Map<String, String> properties) {
        this.properties = properties == null ? new LinkedHashMap<String, String>() : properties;
    }

    /** 取一个非敏感属性；缺失返回 null。 */
    public String property(String key) {
        return properties == null ? null : properties.get(key);
    }

    /** 深拷贝（改副本不影响原对象；registry 换绑时用它做快照）。 */
    public DataSourceDef copy() {
        return new DataSourceDef(id, name, type, credentialRef,
                properties == null ? null : new LinkedHashMap<>(properties));
    }

    @Override
    public String toString() {
        // 防御性脱敏：即便调用方绕过 validator 塞了敏感键，打印时也不出值。
        StringBuilder sb = new StringBuilder("DataSourceDef{id=").append(id)
                .append(", name=").append(name)
                .append(", type=").append(type)
                .append(", credentialRef=").append(credentialRef)
                .append(", properties={");
        if (properties != null) {
            boolean first = true;
            for (Map.Entry<String, String> e : properties.entrySet()) {
                if (!first) {
                    sb.append(", ");
                }
                first = false;
                sb.append(e.getKey()).append('=');
                if (SensitivePropertyKeys.isSensitive(e.getKey())) {
                    sb.append("***");
                } else {
                    sb.append(e.getValue());
                }
            }
        }
        return sb.append("}}").toString();
    }
}
