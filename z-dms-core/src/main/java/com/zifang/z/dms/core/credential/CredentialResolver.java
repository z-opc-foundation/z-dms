package com.zifang.z.dms.core.credential;

import com.zifang.z.dms.core.model.Credential;

/**
 * 凭据解析器（SPI）：把 {@code credentialRef}（z-mist 键名）解析成一份凭据。
 * <p>
 * <b>这是「只存引用不存明文」这条纪律的唯一出口</b>：解析出来的明文只活在内存里，
 * 拼进连接参数后即丢，不回写 {@code DataSourceDef}、不落库、不进日志。
 * <p>
 * 实现方：
 * <ul>
 *   <li>{@code MistCredentialResolver}（starter）：走 z-mist 客户端长连接协议取密文；</li>
 *   <li>{@code EnvironmentCredentialResolver}（starter）：从 Spring Environment 取
 *       （值由环境变量 / 外部配置注入，仓内不落明文），用于离线自检与无 z-mist 的宿主。</li>
 * </ul>
 */
public interface CredentialResolver {

    /**
     * @param credentialRef 凭据引用，格式 {@code <group>/<key>}
     * @return 解析出的凭据；解析不到必须抛异常，<b>不允许返回 null 或空口令</b>
     * @throws CredentialResolveException 引用缺失 / 格式不对 / 解析失败
     */
    Credential resolve(String credentialRef);
}
