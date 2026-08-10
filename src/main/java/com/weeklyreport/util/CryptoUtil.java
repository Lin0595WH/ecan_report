package com.weeklyreport.util;

import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.Base64;

/**
 * 纯静态加解密工具：RSA(OAEP/SHA-1) + AES(GCM)。
 * 不依赖 Spring，便于脱离容器单测。
 *
 * <p>约定（前后端须一致）：
 * <ul>
 *   <li>RSA: {@code RSA/ECB/OAEPWithSHA-1AndMGF1Padding}（跨平台 RSA-OAEP 唯一互通组合：
 *       Web Crypto 在 hash=SHA-256 时底层 MGF1 实为 SHA-1，与 Java 对不上；统一 SHA-1 才互通）</li>
 *   <li>AES: {@code AES/GCM/NoPadding}，IV 12 字节，TAG 128 位</li>
 * </ul>
 */
public final class CryptoUtil {

    public static final String RSA_TRANSFORM = "RSA/ECB/OAEPWithSHA-1AndMGF1Padding";
    public static final String AES_TRANSFORM = "AES/GCM/NoPadding";
    public static final int GCM_IV_LEN = 12;
    public static final int GCM_TAG_LEN = 128;
    public static final int AES_KEY_BITS = 256;

    private CryptoUtil() {
    }

    public static KeyPair generateRsaKeyPair(int bits) throws GeneralSecurityException {
        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
        g.initialize(bits);
        return g.generateKeyPair();
    }

    public static SecretKey generateAesKey() throws GeneralSecurityException {
        KeyGenerator g = KeyGenerator.getInstance("AES");
        g.init(AES_KEY_BITS);
        return g.generateKey();
    }

    public static byte[] rsaEncrypt(PublicKey pub, byte[] data) throws GeneralSecurityException {
        Cipher c = Cipher.getInstance(RSA_TRANSFORM);
        c.init(Cipher.ENCRYPT_MODE, pub);
        return c.doFinal(data);
    }

    public static byte[] rsaDecrypt(PrivateKey priv, byte[] data) throws GeneralSecurityException {
        Cipher c = Cipher.getInstance(RSA_TRANSFORM);
        c.init(Cipher.DECRYPT_MODE, priv);
        return c.doFinal(data);
    }

    public static byte[] aesEncrypt(SecretKey key, byte[] iv, byte[] plaintext) throws GeneralSecurityException {
        Cipher c = Cipher.getInstance(AES_TRANSFORM);
        c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LEN, iv));
        return c.doFinal(plaintext);
    }

    public static byte[] aesDecrypt(SecretKey key, byte[] iv, byte[] ciphertext) throws GeneralSecurityException {
        Cipher c = Cipher.getInstance(AES_TRANSFORM);
        c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LEN, iv));
        return c.doFinal(ciphertext);
    }

    public static String b64(byte[] b) {
        return Base64.getEncoder().encodeToString(b);
    }

    public static byte[] b64d(String s) {
        return Base64.getDecoder().decode(s);
    }

    public static String utf8(byte[] b) {
        return new String(b, StandardCharsets.UTF_8);
    }

    public static byte[] utf8(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }
}
