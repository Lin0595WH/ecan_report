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
        registry.addMapping("/api/**")
                .allowedOrigins("http://weekly_report.coderlin.me")  // 允许的前端域名
                .allowedMethods("GET", "POST", "PUT", "DELETE")
                .allowedHeaders("*")
                .exposedHeaders(
                        "Content-Disposition",  // 文件下载
                        "Content-Length",       // 内容长度
                        "Content-Type",         // 内容类型
                        "Authorization",        // 认证
                        "X-Requested-With",     // AJAX请求标识
                        "X-Total-Count"         // 分页总数
                )
                .allowCredentials(true);
    }
}
