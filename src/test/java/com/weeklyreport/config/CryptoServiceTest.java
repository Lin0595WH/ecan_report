package com.weeklyreport.config;

import com.weeklyreport.util.CryptoUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * CryptoService 信封加解密单测：覆盖公钥导出、请求信封解密、响应信封加密回解。
 * 不启动 Spring（手动调用 init()）。
 */
public class CryptoServiceTest {

    private CryptoService cryptoService;

    @BeforeEach
    void setUp() throws Exception {
        cryptoService = new CryptoService();
        cryptoService.init(); // rsaKeySize 默认 0 → 内部取 2048
    }

    @Test
    void publicKeyPem_isValidSpki() throws Exception {
        String pem = cryptoService.getPublicKeyPem();
        byte[] der = Base64.getDecoder().decode(pem);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        kf.generatePublic(new X509EncodedKeySpec(der)); // 应可成功解析为 RSA 公钥
    }

    @Test
    void envelope_roundTrip() throws Exception {
        // 模拟前端：用服务端公钥 RSA 包裹随机 AES 密钥，AES 加密明文报文
        PublicKey serverPub = KeyFactory.getInstance("RSA")
                .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(cryptoService.getPublicKeyPem())));

        SecretKey aesKey = CryptoUtil.generateAesKey();
        byte[] wrapped = CryptoUtil.rsaEncrypt(serverPub, aesKey.getEncoded());

        byte[] iv = new byte[CryptoUtil.GCM_IV_LEN];
        new SecureRandom().nextBytes(iv);
        byte[] data = CryptoUtil.aesEncrypt(aesKey, iv, "请求明文".getBytes());

        String envelope = "{\"k\":\"" + CryptoUtil.b64(wrapped)
                + "\",\"iv\":\"" + CryptoUtil.b64(iv)
                + "\",\"data\":\"" + CryptoUtil.b64(data) + "\"}";

        CryptoService.Decrypted decrypted = cryptoService.decryptEnvelope(envelope);
        assertEquals("请求明文", decrypted.plaintext);
        assertNotNull(decrypted.aesKey);
    }

    @Test
    void encryptResponse_roundTrip() throws Exception {
        SecretKey aesKey = CryptoUtil.generateAesKey();
        Map<String, String> env = cryptoService.encryptResponse("{\"code\":0}", aesKey);
        assertNotNull(env.get("iv"));
        assertNotNull(env.get("data"));

        byte[] iv = CryptoUtil.b64d(env.get("iv"));
        byte[] ct = CryptoUtil.b64d(env.get("data"));
        byte[] pt = CryptoUtil.aesDecrypt(aesKey, iv, ct);
        assertEquals("{\"code\":0}", new String(pt));
    }
}
