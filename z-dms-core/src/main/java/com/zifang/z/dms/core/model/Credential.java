package com.zifang.z.dms.core.model;

/**
 * 一份解析出来的凭据（来自 z-mist，按 credentialRef 取回）。
 * <p>
 * <b>只允许存在于非持久化的内存态</b>：不写回 {@link DataSourceDef}、不落库、不进日志
 * （{@link #toString()} 对口令一律打码）。
 */
public final class Credential {

    private final String username;
    private final String password;

    private Credential(String username, String password) {
        this.username = username;
        this.password = password;
    }

    public static Credential of(String username, String password) {
        return new Credential(username, password);
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    @Override
    public String toString() {
        return "Credential{username=" + username + ", password=***}";
    }
}
