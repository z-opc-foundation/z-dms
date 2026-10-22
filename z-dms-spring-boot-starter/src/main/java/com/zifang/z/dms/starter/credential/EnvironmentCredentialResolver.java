package com.zifang.z.dms.starter.credential;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zifang.z.dms.core.model.Credential;
import com.zifang.z.dms.core.credential.CredentialResolveException;
import com.zifang.z.dms.core.credential.CredentialResolver;
import org.springframework.core.env.Environment;

import java.util.Map;

/**
 * 从 Spring {@link Environment} 解析凭据（离线 / 自检 / 无 z-mist 宿主用）。
 * <p>
 * 取值路径：{@code <prefix><group>.<key>}，其中 {@code credentialRef = <group>/<key>}。
 * 值的内容格式：{@code {"username":"...","password":"..."}}。
 * <p>
 * <b>仓内不落明文</b>：值由环境变量 / 外部配置注入（yml 里写 {@code ${ENV_NAME}}），
 * 本类只做解析，不打印、不回写定义。
 */
public class EnvironmentCredentialResolver implements CredentialResolver {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Environment environment;
    private final String propertyPrefix;

    public EnvironmentCredentialResolver(Environment environment, String propertyPrefix) {
        this.environment = environment;
        this.propertyPrefix = propertyPrefix == null ? "z.dms.credential." : propertyPrefix;
    }

    @Override
    public Credential resolve(String credentialRef) {
        if (credentialRef == null || credentialRef.trim().isEmpty()) {
            throw new CredentialResolveException("credentialRef 为空，无法解析凭据");
        }
        String path = propertyPrefix + credentialRef.replace('/', '.');
        String raw = environment.getProperty(path);
        if (raw == null || raw.trim().isEmpty()) {
            throw new CredentialResolveException(
                    "凭据引用 [" + credentialRef + "] 在配置面里没有取值（找的是 " + path + "）。"
                            + "生产口径应由 z-mist 提供该键名；本地自检可用环境变量注入。");
        }
        return parse(raw, credentialRef);
    }

    /** 解析凭据 JSON；失败抛异常且<b>不带出原文</b>。 */
    static Credential parse(String raw, String credentialRef) {
        try {
            JsonNode node = MAPPER.readTree(raw);
            if (node == null || !node.isObject()) {
                throw new CredentialResolveException(
                        "凭据引用 [" + credentialRef + "] 的取值不是 JSON 对象，"
                                + "应为 {\"username\":\"...\",\"password\":\"...\"}");
            }
            String username = node.path("username").asText(null);
            String password = node.path("password").asText(null);
            if (username == null || password == null) {
                throw new CredentialResolveException(
                        "凭据引用 [" + credentialRef + "] 的 JSON 缺 username 或 password 字段");
            }
            return Credential.of(username, password);
        } catch (CredentialResolveException e) {
            throw e;
        } catch (Exception e) {
            throw new CredentialResolveException(
                    "凭据引用 [" + credentialRef + "] 的取值解析失败（内容不打印，避免泄露）", e);
        }
    }

    /** 供诊断用：只看引用是否存在，不取值。 */
    public boolean has(String credentialRef) {
        if (credentialRef == null) {
            return false;
        }
        String v = environment.getProperty(propertyPrefix + credentialRef.replace('/', '.'));
        return v != null && !v.trim().isEmpty();
    }

    /** 供诊断用：当前配置面里可用的引用键名（只有键名，没有值）。 */
    public Map<String, String> availableRefs() {
        return new java.util.LinkedHashMap<>();
    }
}
