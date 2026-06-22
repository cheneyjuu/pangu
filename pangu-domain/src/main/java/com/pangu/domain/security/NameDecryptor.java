package com.pangu.domain.security;

/**
 * 姓名容错解密器（领域接口）。
 *
 * <p>{@code t_account.real_name} 为 SM4 密文，但开发期 mock 数据是 {@code MOCK_xxx} 明文，
 * 直接走 {@code Sm4EncryptTypeHandler} 会在 {@code Sm4Util.decryptHex} 抛出
 * {@code RuntimeException("SM4 解密失败")} 导致接口 500。
 *
 * <p>本接口承诺：
 * <ul>
 *   <li>对正常 hex 密文：返回解密后的明文；</li>
 *   <li>对 {@code null}/空串：原样返回；</li>
 *   <li>对非法 hex / 解密异常：回退到原值（不抛）。</li>
 * </ul>
 *
 * <p>domain 层只声明契约，infrastructure 提供 SM4 实现，bootstrap 装配为 Spring Bean，
 * application 通过此接口获得容错解密能力（保 application → domain ← infrastructure 单向依赖）。
 */
public interface NameDecryptor {

    /**
     * 容错解密：对解密失败的输入回退到原值。
     *
     * @param cipher 密文（或 mock 明文）
     * @return 解密后的明文，或原值（解密失败/空输入时）
     */
    String safeDecrypt(String cipher);
}
