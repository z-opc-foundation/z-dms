package com.zifang.z.dms.starter.credential;

import com.zifang.z.dms.core.model.Credential;
import com.zifang.z.dms.core.credential.CredentialResolveException;
import com.zifang.z.dms.core.credential.CredentialResolver;

/**
 * 走 z-mist 解析凭据（<b>形态 A 的正路</b>）。
 * <p>
 * {@code credentialRef} 格式 {@code <group>/<key>}：
 * <ul>
 *   <li>{@code group} 段是 z-mist 的 group（为空时用默认分组）；</li>
 *   <li>{@code key} 段是 z-mist 的密钥键名。</li>
 * </ul>
 * 取回的密文内容格式：{@code {"username":"...","password":"..."}}。
 * <p>
 * <b>明文只在内存里，且不回写定义、不落库、不打日志。</b>
 */
public class MistCredentialResolver implements CredentialResolver {

    private final MistSecretSource secretSource;
    private final String defaultGroup;
    private final String defaultNamespace;

    public MistCredentialResolver(MistSecretSource secretSource, String defaultGroup, String defaultNamespace) {
        this.secretSource = secretSource;
        this.defaultGroup = defaultGroup;
        this.defaultNamespace = defaultNamespace;
    }

    @Override
    public Credential resolve(String credentialRef) {
        if (credentialRef == null || credentialRef.trim().isEmpty()) {
            throw new CredentialResolveException("credentialRef 为空，无法解析凭据");
        }
        String ref = credentialRef.trim();
        int idx = ref.indexOf('/');
        if (idx <= 0 || idx == ref.length() - 1) {
            throw new CredentialResolveException(
                    "credentialRef 格式应为 <group>/<key>，实际 = " + ref);
        }
        String group = ref.substring(0, idx);
        String key = ref.substring(idx + 1);
        String secret;
        try {
            secret = secretSource.getSecret(key, group, defaultNamespace);
        } catch (Exception e) {
            throw new CredentialResolveException(
                    "从 z-mist 取凭据失败（引用 " + ref + "，namespace " + defaultNamespace + "）: "
                            + e.getClass().getSimpleName() + ": " + e.getMessage(), e);
        }
        if (secret == null || secret.trim().isEmpty()) {
            throw new CredentialResolveException(
                    "z-mist 上没有键 [" + key + "]（group " + group + "，namespace " + defaultNamespace + "）");
        }
        return EnvironmentCredentialResolver.parse(secret, ref);
    }
}
