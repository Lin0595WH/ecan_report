package com.weeklyreport.filter;

import com.weeklyreport.config.CryptoService;
import lombok.extern.slf4j.Slf4j;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 请求体解密过滤器：对 /personal、/weekly 的 POST 请求，将加密信封解密为明文 JSON 后回填，
 * 控制器以普通 @RequestBody 接收，零改动。
 *
 * <p>由 {@code app.encryption.enabled} 控制，默认关闭（不破坏现有联调）。
 * 关闭时、或非目标路径/非 POST 时直接放行。解密失败（信封非法/验签不过）返回 400。
 */
@Component
@Order(org.springframework.core.Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class DecryptFilter implements Filter {

    private final CryptoService cryptoService;

    @Value("${app.encryption.enabled:false}")
    private boolean enabled;

    public DecryptFilter(CryptoService cryptoService) {
        this.cryptoService = cryptoService;
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        if (!enabled || !"POST".equalsIgnoreCase(request.getMethod())
                || !isTargetPath(request.getServletPath())) {
            chain.doFilter(servletRequest, servletResponse);
            return;
        }

        try {
            String envelope = readBody(request);
            CryptoService.Decrypted decrypted = cryptoService.decryptEnvelope(envelope);
            request.setAttribute("crypto.aesKey", decrypted.aesKey);
            request.setAttribute("crypto.encrypted", Boolean.TRUE);
            chain.doFilter(new PlaintextRequest(request, decrypted.plaintext), response);
        } catch (Exception e) {
            // 不要静默吞异常：把根因打到控制台，否则 400 是黑盒（难以定位是 RSA 还是 AES 失败）
            log.warn("请求解密失败，返回 400。根因: {}: {}", e.getClass().getSimpleName(), e.getMessage(), e);
            response.sendError(HttpStatus.BAD_REQUEST.value(), "invalid encrypted request");
        }
    }

    private boolean isTargetPath(String servletPath) {
        return "/personal".equals(servletPath) || "/weekly".equals(servletPath);
    }

    private String readBody(HttpServletRequest request) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        try (ServletInputStream in = request.getInputStream()) {
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
            }
        }
        return out.toString(StandardCharsets.UTF_8);
    }

    /** 将解密后的明文作为请求体返回。 */
    private static final class PlaintextRequest extends HttpServletRequestWrapper {
        private final byte[] body;

        PlaintextRequest(HttpServletRequest request, String plaintext) {
            super(request);
            this.body = plaintext.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public ServletInputStream getInputStream() {
            final ByteArrayInputStream bais = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override
                public int read() {
                    return bais.read();
                }

                @Override
                public boolean isFinished() {
                    return bais.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener readListener) {
                    // 无需异步读取
                }
            };
        }

        @Override
        public java.io.BufferedReader getReader() {
            return new java.io.BufferedReader(
                    new java.io.InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }

        @Override
        public int getContentLength() {
            return body.length;
        }

        @Override
        public long getContentLengthLong() {
            return body.length;
        }
    }
}
