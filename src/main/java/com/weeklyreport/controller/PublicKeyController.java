package com.weeklyreport.controller;

import com.weeklyreport.config.CryptoService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 暴露服务端 RSA 公钥，供前端加密请求使用。
 * 不进鉴权拦截器（公钥本就该公开），路径：/report/public-key。
 */
@RestController
@RequestMapping("/public-key")
public class PublicKeyController {

    private final CryptoService cryptoService;

    public PublicKeyController(CryptoService cryptoService) {
        this.cryptoService = cryptoService;
    }

    @GetMapping
    public Map<String, String> publicKey() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("algorithm", "RSA");
        m.put("hash", "SHA-1");
        m.put("transform", "RSA/ECB/OAEPWithSHA-1AndMGF1Padding");
        m.put("key", cryptoService.getPublicKeyPem());
        return m;
    }
}
