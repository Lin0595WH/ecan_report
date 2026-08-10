package com.weeklyreport.config;

import com.weeklyreport.util.PushUtil;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 将外部配置（pushplus token）注入到静态工具类 {@link PushUtil}。
 * 默认值与历史内置值保持一致，未配置时行为不变。
 */
@Component
public class PushConfig {

    @Value("${pushplus.token:b1bfadb9af6749d5af42cba22bafe282}")
    private String pushplusToken;

    @PostConstruct
    public void init() {
        PushUtil.setToken(pushplusToken);
    }
}
