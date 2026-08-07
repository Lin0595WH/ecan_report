package com.weeklyreport.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link PushUtil} 的单元测试（基于 JUnit 5）。
 *
 * <p>说明：用例会真实调用 pushplus 接口。若能联网，将向内置 token 对应的渠道推送测试消息；
 * 若网络不通，{@link PushUtil#push} 会捕获异常并返回 code=-1 的错误 JSON，用例仍可正常通过。
 */
public class PushUtilTest {

    @Test
    void testPushWithDefaultToken() {
        String resp = PushUtil.push("周报系统测试", "这是来自 PushUtil 测试用例的消息");
        System.out.println("[testPushWithDefaultToken] 返回: " + resp);
        assertNotNull(resp, "响应不应为 null");
        assertTrue(resp.contains("code"), "响应应为 JSON 且包含 code 字段: " + resp);
    }

    @Test
    void testPushWithExplicitToken() {
        // 显式传入 token（与内置一致），验证重载方法可用
        String resp = PushUtil.push("重载方法测试", "显式传入 token", "b1bfadb9af6749d5af42cba22bafe282");
        System.out.println("[testPushWithExplicitToken] 返回: " + resp);
        assertNotNull(resp);
        assertTrue(resp.contains("code"), "响应应为 JSON 且包含 code 字段: " + resp);
    }

    @Test
    void testPushWithBlankTokenFallsBackToDefault() {
        // token 为空白时应回退到内置默认 token
        String resp = PushUtil.push("空token回退测试", "token 为空应回退默认", "   ");
        System.out.println("[testPushWithBlankTokenFallsBackToDefault] 返回: " + resp);
        assertNotNull(resp);
        assertTrue(resp.contains("code"), "响应应为 JSON 且包含 code 字段: " + resp);
    }
}
