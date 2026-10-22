package com.zifang.z.dms.admin.web.dto;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 登记 / 编辑表单的入参。
 *
 * <p><b>这里刻意没有一个口令字段</b>：凭据面只有 {@code credentialRef}（z-mist 键名引用）。
 * 前端同理 —— 界面上<b>不允许</b>出现口令输入框。
 *
 * <p>{@code properties} 是非敏感连接属性（host / port / database / 池参数…）；
 * 万一有人塞了 password 这类敏感键，写入口的 {@code DataSourceDefValidator} 会直接拒掉
 * （报错消息只列键名，不带值）。
 */
public class DataSourceForm {

    private String id;
    private String name;
    private String type;
    /** 凭据引用，格式 {@code <group>/<key>}。 */
    private String credentialRef;
    private Map<String, String> properties = new LinkedHashMap<>();

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

    public String getType() {
        return type;
    }

    public void setType(String type) {
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
}
