package com.weeklyreport.util;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;


@SpringBootTest
class PushIpUtilTest {



    @Test
    void sendWechatPush() {
        String res = PushIpUtil.sendWechatPush("测试", "测试");
        System.out.println(res);
    }

    @Test
    void getClientIp() {
        String ip = PushIpUtil.getClientIp(null);
        System.out.println(ip);
    }

    @Test
    void getIpRegion() {
        String region = PushIpUtil.getIpRegion("127.0.0.1");
        System.out.println(region);
    }
}