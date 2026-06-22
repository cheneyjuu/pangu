package com.pangu.infrastructure.security.crypto;

import com.pangu.domain.security.NameDecryptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 姓名容错解密器（SM4 实现）。
 *
 * <p>对正常 hex 密文：调用 {@link Sm4Util#decryptHex} 解密；
 * 对 {@code null}/空串：原样返回；
 * 对非 hex（开发期 MOCK_ 明文 / 历史脏数据）：回退到原值，不抛。
 *
 * <p>SM4 key 由 {@code platform.security.sm4-key-hex} 注入，与 {@link Sm4EncryptTypeHandler} 一致。
 */
@Component
public class Sm4NameDecryptor implements NameDecryptor {

    private final String sm4KeyHex;

    public Sm4NameDecryptor(@Value("${platform.security.sm4-key-hex}") String sm4KeyHex) {
        this.sm4KeyHex = sm4KeyHex;
    }

    @Override
    public String safeDecrypt(String cipher) {
        if (cipher == null || cipher.isEmpty()) {
            return cipher;
        }
        try {
            return Sm4Util.decryptHex(cipher, sm4KeyHex);
        } catch (RuntimeException e) {
            return cipher;
        }
    }
}
