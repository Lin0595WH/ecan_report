package com.weeklyreport.util;

import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.security.KeyPair;
import java.security.SecureRandom;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * CryptoUtil 纯函数单测（不依赖 Spring）。
 */
public class CryptoUtilTest {

    @Test
    void rsaRoundTrip() throws Exception {
        KeyPair kp = CryptoUtil.generateRsaKeyPair(2048);
        byte[] plain = "hello rsa".getBytes();
        byte[] enc = CryptoUtil.rsaEncrypt(kp.getPublic(), plain);
        byte[] dec = CryptoUtil.rsaDecrypt(kp.getPrivate(), enc);
        assertArrayEquals(plain, dec);
    }

    @Test
    void aesRoundTrip() throws Exception {
        SecretKey key = CryptoUtil.generateAesKey();
        byte[] iv = new byte[CryptoUtil.GCM_IV_LEN];
        new SecureRandom().nextBytes(iv);
        byte[] plain = "secret payload".getBytes();
        byte[] enc = CryptoUtil.aesEncrypt(key, iv, plain);
        byte[] dec = CryptoUtil.aesDecrypt(key, iv, enc);
        assertArrayEquals(plain, dec);
    }

    @Test
    void base64Helpers() {
        byte[] data = {1, 2, 3, 4, 5};
        String s = CryptoUtil.b64(data);
        assertArrayEquals(data, CryptoUtil.b64d(s));
    }

    @Test
    void utf8Helpers() {
        String s = "中文测试";
        assertEquals(s, CryptoUtil.utf8(CryptoUtil.utf8(s)));
    }
}
