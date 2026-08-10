package com.weeklyreport.interceptor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 针对 AuthInterceptor 决策逻辑的纯单元测试（不依赖 Spring 容器）。
 */
public class AuthInterceptorTest {

    @Test
    void authorize_disabled_alwaysTrue() {
        assertTrue(AuthInterceptor.authorize("anything", false, "secret"));
        assertTrue(AuthInterceptor.authorize(null, false, "secret"));
    }

    @Test
    void authorize_enabled_noToken_false() {
        assertFalse(AuthInterceptor.authorize(null, true, "secret"));
        assertFalse(AuthInterceptor.authorize("", true, "secret"));
    }

    @Test
    void authorize_enabled_wrongToken_false() {
        assertFalse(AuthInterceptor.authorize("wrong", true, "secret"));
    }

    @Test
    void authorize_enabled_correctToken_true() {
        assertTrue(AuthInterceptor.authorize("secret", true, "secret"));
    }

    @Test
    void tryAcquire_qpsZero_alwaysTrue() {
        AuthInterceptor interceptor = new AuthInterceptor();
        for (int i = 0; i < 100; i++) {
            assertTrue(interceptor.tryAcquire("1.2.3.4", 0));
        }
    }

    @Test
    void tryAcquire_limitsWithinWindow() {
        AuthInterceptor interceptor = new AuthInterceptor();
        // qps=0.1 → 每秒允许次数 = max(1, round(0.1)) = 1
        String ip = "9.9.9.9";
        assertTrue(interceptor.tryAcquire(ip, 0.1));
        assertFalse(interceptor.tryAcquire(ip, 0.1));
    }
}
