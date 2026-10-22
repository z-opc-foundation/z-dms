package com.zifang.z.dms.starter.credential;

/**
 * z-mist 取密文的最小接口（把 z-mist 客户端挡在后面，便于单测替身）。
 * <p>
 * 形状对齐 z-mist 客户端 {@code MistClient#getSecret(String secretKey, String group, String namespace)}。
 */
public interface MistSecretSource {

    /**
     * @param secretKey  密钥键名
     * @param group      分组
     * @param namespace  命名空间
     * @return 密文（z-mist 返回的就是业务侧的明文内容，调用方自行按约定解析）
     */
    String getSecret(String secretKey, String group, String namespace);
}
