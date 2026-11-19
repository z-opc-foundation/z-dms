package com.zifang.z.dms.admin.auth;

/** 未登录（401）：没带凭证或凭证验不过。 */
public class DmsUnauthenticatedException extends RuntimeException {

    public DmsUnauthenticatedException(String message) {
        super(message);
    }
}
