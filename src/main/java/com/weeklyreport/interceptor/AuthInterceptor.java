package com.weeklyreport.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 接口鉴权 + 频率限制拦截器。
 *
 * <p>鉴权与限流为<b>两个独立开关</b>，可分别启用、互不影响：
 * <ul>
 *     <li>鉴权由 {@code app.security.auth-enabled} 控制（默认 false）：开启后校验请求头
 *     {@code X-Api-Token} 是否等于 {@code app.security.api-token}，不符返回 401；</li>
 *     <li>限流由 {@code app.rate-limit.enabled} 控制（默认 false）：开启后按客户端 IP 做
 *     固定窗口限流（{@code personal-qps} / {@code weekly-qps}），超限返回 429。</li>
 * </ul>
 *
 * <p>两者默认均关闭，不破坏本地 / 前端现有调用方式。
 *
 * <p>鉴权与限流的核心判定抽成独立方法（{@link #authorize}、{@link #tryAcquire}），便于脱离 Spring 容器单测。
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    @Value("${app.security.auth-enabled:false}")
    private boolean authEnabled;

    @Value("${app.security.api-token:}")
    private String apiToken;

    @Value("${app.rate-limit.enabled:false}")
    private boolean rateLimitEnabled;

    @Value("${app.rate-limit.personal-qps:10}")
    private double personalQps;

    @Value("${app.rate-limit.weekly-qps:5}")
    private double weeklyQps;

    /** 每 IP 的固定窗口限流计数（简单实现，适用于内部小规模服务） */
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1. 鉴权（独立开关 auth-enabled；关闭时 authorize 直接放行）
        if (!authorize(request.getHeader("X-Api-Token"), authEnabled, apiToken)) {
            response.sendError(HttpStatus.UNAUTHORIZED.value(), "missing or invalid X-Api-Token");
            return false;
        }
        // 2. 频率限制（独立开关 rate-limit.enabled；/personal 与 /weekly 分别配置 QPS）
        if (rateLimitEnabled) {
            String uri = request.getRequestURI();
            double qps = uri != null && uri.contains("/personal") ? personalQps : weeklyQps;
            if (!tryAcquire(clientIp(request), qps)) {
                response.sendError(HttpStatus.TOO_MANY_REQUESTS.value(), "rate limit exceeded");
                return false;
            }
        }
        return true;
    }

    /**
     * 鉴权决策（纯函数，可单测）。
     *
     * @param token           请求携带的 token
     * @param enabled         是否开启安全校验
     * @param configuredToken 配置的合法 token
     * @return 是否放行
     */
    public static boolean authorize(String token, boolean enabled, String configuredToken) {
        if (!enabled) {
            return true;
        }
        return configuredToken != null && !configuredToken.isBlank() && configuredToken.equals(token);
    }

    /**
     * 固定窗口限流判定（1 秒窗口，每 qps 对应允许次数 = max(1, round(qps))）。
     * qps ≤ 0 表示关闭限流。
     *
     * @param clientIp 客户端 IP
     * @param qps      每秒允许请求数
     * @return 是否放行
     */
    public boolean tryAcquire(String clientIp, double qps) {
        if (qps <= 0) {
            return true;
        }
        long now = System.currentTimeMillis();
        long window = 1000L;
        long limit = Math.max(1, Math.round(qps));
        Bucket b = buckets.computeIfAbsent(clientIp, k -> new Bucket(now, 0));
        synchronized (b) {
            if (now - b.windowStart >= window) {
                b.windowStart = now;
                b.count = 0;
            }
            if (b.count >= limit) {
                return false;
            }
            b.count++;
            return true;
        }
    }

    private String clientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip == null ? "unknown" : ip;
    }

    /** 固定窗口计数桶 */
    private static final class Bucket {
        long windowStart;
        long count;

        Bucket(long windowStart, long count) {
            this.windowStart = windowStart;
            this.count = count;
        }
    }
}
