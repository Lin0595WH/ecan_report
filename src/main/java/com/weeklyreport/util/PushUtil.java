package com.weeklyreport.util;

import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.json.JSONUtil;

import java.util.HashMap;
import java.util.Map;

/**
 * pushplus 消息推送工具（https://www.pushplus.plus/）
 *
 * <p>使用 Hutool 的 {@link HttpRequest} 以 JSON 形式 POST 到 pushplus 接口。
 * {@code token} 作为类内部静态变量内置，也可在调用时显式传入（为空时回退到内置 token）。
 */
public class PushUtil {

    /** pushplus 推送接口地址 */
    private static final String PUSH_URL = "https://www.pushplus.plus/send/";

    /** 内置默认 token（如为空字符串则使用调用方传入的 token） */
    private static final String TOKEN = "b1bfadb9af6749d5af42cba22bafe282";

    /**
     * 使用内置默认 token 推送消息。
     *
     * @param title   标题
     * @param content 消息内容
     * @return pushplus 接口的原始响应(JSON 字符串)；异常时返回包含 code=-1 的错误 JSON
     */
    public static String push(String title, String content) {
        return push(title, content, TOKEN);
    }

    /**
     * 使用指定 token 推送消息（token 为 null / 空白时回退到内置默认 token）。
     *
     * @param title   标题
     * @param content 消息内容
     * @param token   推送 token；为空时使用 {@link #TOKEN}
     * @return pushplus 接口的原始响应(JSON 字符串)；异常时返回包含 code=-1 的错误 JSON
     */
    public static String push(String title, String content, String token) {
        String useToken = CharSequenceUtil.isBlank(token) ? TOKEN : token;

        Map<String, Object> body = new HashMap<>(3);
        body.put("token", useToken);
        body.put("title", title);
        body.put("content", content);
        String json = JSONUtil.toJsonStr(body);

        try {
            return HttpRequest.post(PUSH_URL)
                    .header("Content-Type", "application/json")
                    .body(json)
                    .timeout(10000)
                    .execute()
                    .body();
        } catch (Exception e) {
            return "{\"code\":-1,\"msg\":\"推送异常:" + e.getMessage() + "\"}";
        }
    }
}
