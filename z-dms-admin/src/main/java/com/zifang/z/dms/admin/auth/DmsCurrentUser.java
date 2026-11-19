package com.zifang.z.dms.admin.auth;

import java.util.Collections;
import java.util.Set;

/**
 * z-dms 当前用户（登录态来自同集群 z-ctc 的 JWT）。
 *
 * <p>{@code userId} 是 z-ctc JWT 的 {@code userId} claim（= {@code z_ctc_ac_account.id}，
 * 与 z-team 跑通的口径一致）；权限码由 {@link DmsPermissionService} 走 z-ctc 授权数据拉取。
 */
public class DmsCurrentUser {

    private Long userId;
    private String username;
    private String nickname;
    private boolean authenticated;
    private boolean admin;
    /** 权限服务不可用时判定被降级（按无权限处理）。 */
    private boolean degraded;
    private Set<String> permissions = Collections.emptySet();

    /**
     * 终端用户的 z-ctc token（请求域内存对象：转发给 4A 授权链要用它）。
     * <b>永不进审计流水、日志与任何响应体</b>。
     */
    private String token;

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    /** 静态匿名（权限管控关闭 / 未登录时的占位；不猜、不编身份）。 */
    public static DmsCurrentUser anonymous() {
        DmsCurrentUser u = new DmsCurrentUser();
        return u;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public boolean isAuthenticated() {
        return authenticated;
    }

    public void setAuthenticated(boolean authenticated) {
        this.authenticated = authenticated;
    }

    public boolean isAdmin() {
        return admin;
    }

    public void setAdmin(boolean admin) {
        this.admin = admin;
    }

    public boolean isDegraded() {
        return degraded;
    }

    public void setDegraded(boolean degraded) {
        this.degraded = degraded;
    }

    public Set<String> getPermissions() {
        return permissions;
    }

    public void setPermissions(Set<String> permissions) {
        this.permissions = permissions == null ? Collections.emptySet() : permissions;
    }

    /** 审计流水里的操作人展示名（昵称 / 用户名 / userId；永不编造）。 */
    public String display() {
        if (nickname != null && !nickname.trim().isEmpty()) {
            return nickname.trim();
        }
        if (username != null && !username.trim().isEmpty()) {
            return username.trim();
        }
        return userId == null ? "anonymous" : "user-" + userId;
    }
}
