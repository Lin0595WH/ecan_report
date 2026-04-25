package com.weeklyreport.config;


import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * @Author Lin
 * @Date 2025/10/12 0:01
 * @Descriptions 处理跨域
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**") // 匹配所有接口
                .allowedOriginPatterns("*") // 关键修复：用这个而不是 allowedOrigins
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS") // 加上 OPTIONS 很重要
                .allowedHeaders("*")
                .exposedHeaders(
                        "Content-Disposition",
                        "Content-Length",
                        "Content-Type",
                        "Authorization",
                        "X-Requested-With",
                        "X-Total-Count"
                )
                .allowCredentials(true)
                .maxAge(3600); // 预检缓存
    }
}
