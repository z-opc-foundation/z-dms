package com.zifang.z.dms.admin.web;

import com.zifang.z.dms.admin.web.dto.ApiResult;
import com.zifang.z.dms.core.credential.CredentialResolveException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 异常 → 统一返回体。
 *
 * <p>错误消息<b>不带任何口令</b>：core 侧的校验/凭据异常消息本身就不带值
 * （只列键名），这里原样透传即可。
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResult<Object>> badRequest(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResult.fail(400, e.getMessage()));
    }

    @ExceptionHandler(CredentialResolveException.class)
    public ResponseEntity<ApiResult<Object>> credentialError(CredentialResolveException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResult.fail(400, e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResult<Object>> error(Exception e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResult.fail(500, e.getClass().getSimpleName() + ": " + e.getMessage()));
    }
}
