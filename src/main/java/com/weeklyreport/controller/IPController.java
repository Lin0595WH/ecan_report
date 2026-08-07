package com.weeklyreport.controller;

import com.weeklyreport.common.BaseResponse;
import com.weeklyreport.common.ResultUtils;
import com.weeklyreport.util.IPUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * IP 工具测试接口（基于 ip2region 离线库）
 */
@RestController
@RequestMapping("/ip")
public class IPController {

    /**
     * 测试1：根据当前请求获取真实客户端 IP 的归属地 / 运营商等信息
     *
     * @param request 当前请求
     */
    @GetMapping("/me")
    public BaseResponse<IPUtil.IpInfo> currentIpInfo(HttpServletRequest request) {
        return ResultUtils.success(IPUtil.getIpInfo(request));
    }

    /**
     * 测试2：根据指定 IP 查询归属地 / 运营商等信息
     *
     * @param ip IPv4 / IPv6 地址
     */
    @GetMapping("/query")
    public BaseResponse<IPUtil.IpInfo> queryIpInfo(@RequestParam("ip") String ip) {
        return ResultUtils.success(IPUtil.getIpInfo(ip));
    }
}
