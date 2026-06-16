package com.pangu.infrastructure.security.crypto;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.util.encoders.Hex;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.Security;

/**
 * 国密 SM4 对称加解密工具类 (基于 Bouncy Castle 引擎)
 * 采用 SM4/ECB/PKCS7Padding 模式
 */
public class Sm4Util {

    static {
        // 动态注册 Bouncy Castle 安全服务提供者
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    private static final String ALGORITHM_NAME = "SM4";
    private static final String CIPHER_ALGORITHM = "SM4/ECB/PKCS7Padding";

    /**
     * 加密为 Hex 十六进制字符串
     * @param plainText 明文
     * @param hexKey 十六进制密钥 (16字节/128位，即32个字符的Hex)
     * @return 十六进制加密密文
     */
    public static String encryptHex(String plainText, String hexKey) {
        if (plainText == null || plainText.isEmpty()) {
            return plainText;
        }
        try {
            byte[] keyBytes = Hex.decode(hexKey);
            byte[] plainBytes = plainText.getBytes(StandardCharsets.UTF_8);
            byte[] cipherBytes = encrypt(plainBytes, keyBytes);
            return Hex.toHexString(cipherBytes);
        } catch (Exception e) {
            throw new RuntimeException("SM4 加密失败", e);
        }
    }

    /**
     * 解密 Hex 十六进制密文字符串
     * @param hexCipher 十六进制加密密文
     * @param hexKey 十六进制密钥
     * @return 明文
     */
    public static String decryptHex(String hexCipher, String hexKey) {
        if (hexCipher == null || hexCipher.isEmpty()) {
            return hexCipher;
        }
        try {
            byte[] keyBytes = Hex.decode(hexKey);
            byte[] cipherBytes = Hex.decode(hexCipher);
            byte[] plainBytes = decrypt(cipherBytes, keyBytes);
            return new String(plainBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("SM4 解密失败", e);
        }
    }

    private static byte[] encrypt(byte[] data, byte[] key) throws Exception {
        SecretKeySpec keySpec = new SecretKeySpec(key, ALGORITHM_NAME);
        Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM, BouncyCastleProvider.PROVIDER_NAME);
        cipher.init(Cipher.ENCRYPT_MODE, keySpec);
        return cipher.doFinal(data);
    }

    private static byte[] decrypt(byte[] data, byte[] key) throws Exception {
        SecretKeySpec keySpec = new SecretKeySpec(key, ALGORITHM_NAME);
        Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM, BouncyCastleProvider.PROVIDER_NAME);
        cipher.init(Cipher.DECRYPT_MODE, keySpec);
        return cipher.doFinal(data);
    }
}
