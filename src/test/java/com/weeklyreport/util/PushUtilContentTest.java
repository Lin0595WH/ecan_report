package com.weeklyreport.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 针对 PushUtil.ipInfoContent（IP 信息推送文案构造）的单元测试。
 */
public class PushUtilContentTest {

    @Test
    void ipInfoContent_null() {
        assertEquals("IP信息获取失败", PushUtil.ipInfoContent(null));
    }

    @Test
    void ipInfoContent_normal() {
        IPUtil.IpInfo known = new IPUtil.IpInfo(
                "113.92.157.29", "中国", "广东省", "深圳市", "电信", "CN", "中国广东省深圳市", "中国|广东省|深圳市|电信|CN");
        String c = PushUtil.ipInfoContent(known);
        assertTrue(c.contains("ip：113.92.157.29"));
        assertTrue(c.contains("运营商：电信"));
        assertTrue(c.contains("城市：深圳市"));
        assertTrue(c.contains("原始地区：中国|广东省|深圳市|电信|CN"));
    }
}
