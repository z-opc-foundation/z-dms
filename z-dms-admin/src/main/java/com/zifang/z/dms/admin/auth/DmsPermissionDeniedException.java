package com.zifang.z.dms.admin.auth;

/** 无权限（403）：登录了但缺权限点；或权限服务不可用被降级成无权限。 */
public class DmsPermissionDeniedException extends RuntimeException {

    /** true = 判定失败（z-ctc 不可达）被降级，而不是「确实没有权限」。 */
    private final boolean degraded;

    public DmsPermissionDeniedException(String message) {
        this(message, false);
    }

    public DmsPermissionDeniedException(String message, boolean degraded) {
        super(message);
        this.degraded = degraded;
    }

    public boolean isDegraded() {
        return degraded;
    }
}
