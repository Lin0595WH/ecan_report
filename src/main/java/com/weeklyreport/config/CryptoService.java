package com.weeklyreport.config;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.weeklyreport.util.CryptoUtil;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 请求/响应信封加解密服务。
 *
 * <p>启动时为服务端生成 RSA 密钥对（私钥仅存内存，绝不暴露/入库）。
 * 请求方向：客户端用服务端公钥(RSA)包裹其随机 AES 密钥，服务端用私钥解开后 AES 解密报文。
 * 响应方向：服务端复用本次请求的 AES 密钥回加密文（无需前端持有私钥），前端用同一把密钥解密。
 *
 * <p>信封格式（JSON）：
 * <pre>
 *   请求: {"k": base64(RSA(服务端公钥).encrypt(AES密钥原始字节)), "iv": base64, "data": base64(AES(明文))}
 *   响应: {"iv": base64, "data": base64(AES(明文))}   // 不含 k，密钥已在请求阶段协商
 * </pre>
 */
@Service
public class CryptoService {

    @Value("${app.encryption.rsa-key-size:2048}")
    private int rsaKeySize;

    private KeyPair serverKeyPair;
    private final SecureRandom secureRandom = new SecureRandom();

    @PostConstruct
    public void init() throws GeneralSecurityException {
        int size = rsaKeySize > 0 ? rsaKeySize : 2048;
        this.serverKeyPair = CryptoUtil.generateRsaKeyPair(size);
    }

    /** 暴露服务端公钥（SPKI base64），供前端 importKey 使用。 */
    public String getPublicKeyPem() {
        return CryptoUtil.b64(serverKeyPair.getPublic().getEncoded());
    }

    /** 解密请求信封，返回明文 JSON 与本次协商的 AES 密钥（供响应加密复用）。 */
    public Decrypted decryptEnvelope(String envelopeJson) throws Exception {
        JSONObject env = JSONUtil.parseObj(envelopeJson);
        byte[] wrappedKey = CryptoUtil.b64d(env.getStr("k"));
        byte[] aesRaw = CryptoUtil.rsaDecrypt(serverKeyPair.getPrivate(), wrappedKey);
        SecretKey aesKey = new SecretKeySpec(aesRaw, "AES");
        byte[] iv = CryptoUtil.b64d(env.getStr("iv"));
        byte[] data = CryptoUtil.b64d(env.getStr("data"));
        byte[] plain = CryptoUtil.aesDecrypt(aesKey, iv, data);
        return new Decrypted(CryptoUtil.utf8(plain), aesKey);
    }

    /** 用请求阶段协商的 AES 密钥加密响应明文，返回响应信封 {"iv","data"}（base64）。 */
    public Map<String, String> encryptResponse(String plaintextJson, SecretKey aesKey) throws GeneralSecurityException {
        byte[] iv = new byte[CryptoUtil.GCM_IV_LEN];
        secureRandom.nextBytes(iv);
        byte[] ct = CryptoUtil.aesEncrypt(aesKey, iv, plaintextJson.getBytes(StandardCharsets.UTF_8));
        Map<String, String> env = new LinkedHashMap<>();
        env.put("iv", CryptoUtil.b64(iv));
        env.put("data", CryptoUtil.b64(ct));
        return env;
    }

    /** 解密结果载体：明文 + 本次请求协商的 AES 密钥。 */
    public static final class Decrypted {
        public final String plaintext;
        public final SecretKey aesKey;

        public Decrypted(String plaintext, SecretKey aesKey) {
            this.plaintext = plaintext;
            this.aesKey = aesKey;
        }
    }
}
