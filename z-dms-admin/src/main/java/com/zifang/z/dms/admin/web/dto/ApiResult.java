package com.zifang.z.dms.admin.web.dto;

/**
 * 统一返回体（组织口径 {@code {code, data, message}}）。
 *
 * <p>{@code code=200} 表示成功；前端基座 {@code @yuku123/z-frontend-common} 的
 * {@code unwrapResponse} 认这个形状（有 code+data 就按包装解，code≠200 直接 reject）。
 */
public class ApiResult<T> {

    private int code;
    private String message;
    private T data;

    public ApiResult() {
    }

    public ApiResult(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public static <T> ApiResult<T> ok(T data) {
        return new ApiResult<T>(200, null, data);
    }

    public static <T> ApiResult<T> fail(int code, String message) {
        return new ApiResult<T>(code, message, null);
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }
}
