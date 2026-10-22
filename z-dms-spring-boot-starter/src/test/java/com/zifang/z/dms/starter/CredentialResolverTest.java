package com.zifang.z.dms.starter;

import com.zifang.z.dms.core.model.Credential;
import com.zifang.z.dms.core.credential.CredentialResolveException;
import com.zifang.z.dms.starter.credential.EnvironmentCredentialResolver;
import com.zifang.z.dms.starter.credential.MistCredentialResolver;
import com.zifang.z.dms.starter.credential.MistSecretSource;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 凭据解析口径单测：<b>引用 → 凭据</b>，明文只落在内存里。
 * <p>
 * z-mist 服务端本轮没起（不连生产），所以 z-mist 那条路用替身 {@link MistSecretSource} 验：
 * <b>引用形态、缺失处理、异常不带值</b>三件事都能验到；真连 z-mist 留给人工项。
 */
class CredentialResolverTest {

    /** 假 z-mist：按键名返回密文。 */
    static class FakeMist implements MistSecretSource {
        @Override
        public String getSecret(String secretKey, String group, String namespace) {
            if ("mysql-main".equals(secretKey) && "dms-demo".equals(group)) {
                return "{\"username\":\"app_user\",\"password\":\"s3cr3t-value\"}";
            }
            return null;
        }
    }

    @Test
    void zmist路径按group与key取密文() {
        MistCredentialResolver resolver = new MistCredentialResolver(new FakeMist(), "DEFAULT_GROUP", "default");
        Credential c = resolver.resolve("dms-demo/mysql-main");
        assertEquals("app_user", c.getUsername());
        assertEquals("s3cr3t-value", c.getPassword());
    }

    @Test
    void 缺键时抛异常且不带值() {
        MistCredentialResolver resolver = new MistCredentialResolver(new FakeMist(), "DEFAULT_GROUP", "default");
        CredentialResolveException e = assertThrows(CredentialResolveException.class,
                () -> resolver.resolve("dms-demo/not-exist"));
        assertTrue(e.getMessage().contains("not-exist"), e.getMessage());
        assertTrue(!e.getMessage().contains("s3cr3t-value"), e.getMessage());
    }

    @Test
    void 引用形态不对时报错() {
        MistCredentialResolver resolver = new MistCredentialResolver(new FakeMist(), "DEFAULT_GROUP", "default");
        assertThrows(CredentialResolveException.class, () -> resolver.resolve("no-slash"));
        assertThrows(CredentialResolveException.class, () -> resolver.resolve(""));
    }

    @Test
    void 非JSON密文报错且不打印内容() {
        MistSecretSource bad = (key, group, ns) -> "just-a-plain-string-value";
        MistCredentialResolver resolver = new MistCredentialResolver(bad, "DEFAULT_GROUP", "default");
        CredentialResolveException e = assertThrows(CredentialResolveException.class,
                () -> resolver.resolve("dms-demo/mysql-main"));
        assertTrue(!e.getMessage().contains("just-a-plain-string-value"), e.getMessage());
    }

    @Test
    void environment路径按前缀取json() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("z.dms.credential.dms-demo.mysql-main",
                "{\"username\":\"app_user\",\"password\":\"s3cr3t-value\"}");
        EnvironmentCredentialResolver resolver = new EnvironmentCredentialResolver(env, "z.dms.credential.");
        Credential c = resolver.resolve("dms-demo/mysql-main");
        assertEquals("app_user", c.getUsername());
        assertEquals("s3cr3t-value", c.getPassword());
        assertTrue(resolver.has("dms-demo/mysql-main"));
    }

    @Test
    void environment路径取不到时报错且不带值() {
        MockEnvironment env = new MockEnvironment();
        EnvironmentCredentialResolver resolver = new EnvironmentCredentialResolver(env, "z.dms.credential.");
        CredentialResolveException e = assertThrows(CredentialResolveException.class,
                () -> resolver.resolve("dms-demo/mysql-main"));
        assertTrue(e.getMessage().contains("dms-demo/mysql-main"), e.getMessage());
    }

    @Test
    void 凭据对象的toString不吐口令() {
        Credential c = Credential.of("app_user", "s3cr3t-value");
        assertTrue(!c.toString().contains("s3cr3t-value"), c.toString());
    }
}
