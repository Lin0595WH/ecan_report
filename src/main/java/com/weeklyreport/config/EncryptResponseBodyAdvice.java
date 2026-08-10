package com.weeklyreport.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import javax.crypto.SecretKey;
import java.util.Map;

/**
 * 响应加密 Advice：当本次请求本身是加密信封（已协商 AES 密钥）时，将 JSON 响应也用同一把密钥加密为信封，
 * 实现请求/响应双向加密。不对二进制流（docx StreamingResponseBody、byte[]、Resource）加密，避免破坏文件下载。
 *
 * <p>与 {@link DecryptFilter} 共用请求属性 {@code crypto.encrypted} / {@code crypto.aesKey}：
 * 解密 Filter 在请求阶段写入，本 Advice 在响应阶段读取。两者都受 {@code app.encryption.enabled} 控制——
 * Filter 未放行则不写属性，本 Advice 自动跳过，即「请求没加密，响应也不加密」。
 */
@RestControllerAdvice
public class EncryptResponseBodyAdvice implements ResponseBodyAdvice<Object> {

    private final CryptoService cryptoService;
    private final ObjectMapper objectMapper;

    public EncryptResponseBodyAdvice(CryptoService cryptoService, ObjectMapper objectMapper) {
        this.cryptoService = cryptoService;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request, ServerHttpResponse response) {
        HttpServletRequest servletRequest = currentRequest();
        if (servletRequest == null) {
            return body;
        }

        Boolean encrypted = (Boolean) servletRequest.getAttribute("crypto.encrypted");
        SecretKey aesKey = (SecretKey) servletRequest.getAttribute("crypto.aesKey");
        if (encrypted == null || !encrypted || aesKey == null) {
            return body;
        }

        // 跳过二进制响应（docx 等），交给原生下载流程
        if (body instanceof org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody
                || body instanceof byte[]
                || body instanceof org.springframework.core.io.Resource) {
            return body;
        }

        try {
            String plaintextJson = (body instanceof String)
                    ? (String) body
                    : objectMapper.writeValueAsString(body);
            // 复用 CryptoService 生成新 IV 并加密，返回 {"iv","data"}
            Map<String, String> envelope = cryptoService.encryptResponse(plaintextJson, aesKey);
            response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
            return envelope;
        } catch (Exception e) {
            // 加密失败不应阻断业务，降级为明文（仅记录日志）
            org.slf4j.LoggerFactory.getLogger(EncryptResponseBodyAdvice.class)
                    .warn("响应加密失败，降级为明文: {}", e.getMessage());
            return body;
        }
    }

    private HttpServletRequest currentRequest() {
        var attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes) {
            return ((ServletRequestAttributes) attrs).getRequest();
        }
        return null;
    }
}
