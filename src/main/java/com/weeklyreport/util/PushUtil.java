package com.weeklyreport.util;

import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.json.JSONUtil;

import java.util.HashMap;
import java.util.Map;

/**
 * pushplus 消息推送工具（https://www.pushplus.plus/）
 *
 * <p>使用 Hutool 的 {@link HttpRequest} 以 JSON 形式 POST 到 pushplus 接口。
 * token 默认取内置值，可由 {@link #setToken(String)} 从配置文件 / 环境变量注入覆盖。
 */
public class PushUtil {

    /** pushplus 推送接口地址 */
    private static final String PUSH_URL = "https://www.pushplus.plus/send/";

    /** 默认 token（可由配置通过 {@link #setToken} 覆盖） */
    private static volatile String TOKEN = "b1bfadb9af6749d5af42cba22bafe282";

    /**
     * 由配置类在启动时注入最新 token（支持从配置中心 / 环境变量覆盖内置默认值）。
     *
     * @param token 推送 token（空白则忽略）
     */
    public static void setToken(String token) {
        if (CharSequenceUtil.isNotBlank(token)) {
            TOKEN = token;
        }
    }

    /**
     * 使用默认 token 推送消息。
     *
     * @param title   标题
     * @param content 消息内容
     * @return pushplus 接口的原始响应(JSON 字符串)；异常时返回包含 code=-1 的错误 JSON
     */
    public static String push(String title, String content) {
        return push(title, content, TOKEN);
    }

    /**
     * 使用指定 token 推送消息（token 为 null / 空白时回退到默认 token）。
     *
     * @param title   标题
     * @param content 消息内容
     * @param token   推送 token；为空时回退到 {@link #TOKEN}
     * @return pushplus 接口的原始响应(JSON 字符串)；异常时返回包含 code=-1 的错误 JSON
     */
    public static String push(String title, String content, String token) {
        String useToken = CharSequenceUtil.isBlank(token) ? TOKEN : token;

        Map<String, Object> body = new HashMap<>(3);
        body.put("token", useToken);
        body.put("title", title);
        body.put("content", content);

        try {
            return HttpRequest.post(PUSH_URL)
                    .header("Content-Type", "application/json")
                    .body(JSONUtil.toJsonStr(body))
                    .timeout(10000)
                    .execute()
                    .body();
        } catch (Exception e) {
            // 用 JSONUtil 构造，避免异常信息含特殊字符导致 JSON 非法
            return JSONUtil.toJsonStr(Map.of("code", -1, "msg", "推送异常:" + e.getMessage()));
        }
    }

    /**
     * 构造 IP 信息的推送文案（独立方法，便于单测）。
     *
     * @param ipInfo IP 信息（来自 {@link IPUtil}）；为 null 时返回提示文案
     * @return 推送内容文本
     */
    static String ipInfoContent(IPUtil.IpInfo ipInfo) {
        if (ipInfo == null) {
            return "IP信息获取失败";
        }
        return StrUtil.format(
                "ip：{}\n运营商：{}\n城市：{}\n原始地区：{}\n",
                ipInfo.ip(), ipInfo.isp(), ipInfo.city(), ipInfo.rawRegion());
    }

    /**
     * 推送 IP 相关信息（统一两处控制器的重复拼接逻辑）。
     *
     * @param title  标题（如 "xxx 个人周报查询" / "部门周报文件生成"）
     * @param ipInfo IP 信息（来自 {@link IPUtil}）；为 null 时推送提示文案
     * @return pushplus 接口的原始响应(JSON 字符串)
     */
    public static String pushIpInfo(String title, IPUtil.IpInfo ipInfo) {
        return push(title, ipInfoContent(ipInfo));
    }
}
