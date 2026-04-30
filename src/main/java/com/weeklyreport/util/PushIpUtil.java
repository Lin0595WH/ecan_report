package com.weeklyreport.util;

import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.text.StrPool;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import jakarta.servlet.http.HttpServletRequest;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Spliterators;


/**
 * 微信推送 + IP 工具类（兼容所有Hutool版本）
 */
public class PushIpUtil {

    // ====================== 配置项 ======================
    private static final String TOKEN = "ctwpaCtwfMCh1E3KhsuXxlAZm";
    private static final String PUSH_URL = "https://wx.xtuis.cn/" + TOKEN + ".send";
    private static final String IP_REGION_API = "https://www.cz88.net/api/cz88/ip/geo?ip=";
    // ===================================================

    /**
     * 发送微信推送（和你的curl完全一致）
     */
    public static String sendWechatPush(String text, String desp) {
        try (HttpResponse response = HttpRequest.post(PUSH_URL)
                .form("text", text)
                .form("desp", desp)
                .timeout(10000)
                .execute()) {
            return response.body();
        }
    }

    /**
     * 【手动实现】获取客户端真实IP（兼容所有Hutool、支持Nginx）
     * 替代 Hutool 的 NetUtil.getClientIp
     */
    public static String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (CharSequenceUtil.isBlank(ip) || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (CharSequenceUtil.isBlank(ip) || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (CharSequenceUtil.isBlank(ip) || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }

        // 多级代理取第一个IP
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    /**
     * 根据IP获取 省-市-区
     */
    public static String getIpRegion(String ip) {
        try {
            String encodedIp = URLEncoder.encode(ip, StandardCharsets.UTF_8);
            String resp = HttpUtil.get(IP_REGION_API + encodedIp);
            JSONObject json = JSONUtil.parseObj(resp);

            if (json.getInt("code") != 200) {
                return "未知地区";
            }
            String dataStr = json.getStr("data");
            Map<String, String> data = JSONUtil.toBean(dataStr, Map.class);
            if (data == null) {
                return "未知地区";
            }
            String country = data.get("country");
            String province = data.get("province");
            String city = data.get("city");
            String districts = data.get("districts");
            String isp = data.get("isp");
            String region = String.format("归属地：%s-%s-%s-%s", country, province, city, districts);
            String carrier = String.format("运营商：%s", isp);
            return region + StrPool.LF + carrier;
        } catch (Exception e) {
            return "获取地区失败";
        }
    }
}