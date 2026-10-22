package com.zifang.z.dms.core.credential;

/**
 * 凭据解析失败（引用不存在 / 格式不对 / 后端不可用）。
 * <p>
 * <b>消息里只带引用名，不带任何明文。</b>
 */
public class CredentialResolveException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public CredentialResolveException(String message) {
        super(message);
    }

    public CredentialResolveException(String message, Throwable cause) {
        super(message, cause);
    }
}
