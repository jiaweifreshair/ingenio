package com.ingenio.backend.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.time.Duration;

/**
 * Web配置
 * 配置CORS、拦截器、HTTP客户端等Web相关设置
 *
 * @author Ingenio Team
 * @since 1.0.0
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    /**
     * 配置CORS跨域
     * 允许前端应用（本地/局域网）访问后端API
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
            // 说明：
            // - 使用 allowedOriginPatterns 兼容“不同端口/IPv6/局域网 IP”访问前端页面；
            // - 避免因端口变化导致浏览器报错 “Failed to fetch”（典型为 CORS 拦截）。
            .allowedOriginPatterns(
                "http://localhost:*",
                "http://127.0.0.1:*",
                "http://0.0.0.0:*",
                "http://[::1]:*",
                "http://192.168.*.*:*"
            )
            .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH")
            .allowedHeaders("*")
            .allowCredentials(true)
            .maxAge(3600);
    }

    /**
     * 配置RestTemplate HTTP客户端
     * 用于与OpenLovable等本地服务进行HTTP通信
     * 使用SimpleClientHttpRequestFactory并设置NO_PROXY以避免系统代理干扰
     *
     * @return RestTemplate实例
     */
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        // 创建自定义请求工厂，显式设置不使用代理
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory() {
            @Override
            protected void prepareConnection(HttpURLConnection connection, String httpMethod) throws IOException {
                super.prepareConnection(connection, httpMethod);
            }

            @Override
            protected HttpURLConnection openConnection(java.net.URL url, Proxy proxy) throws IOException {
                // 强制使用NO_PROXY，忽略系统代理设置
                return (HttpURLConnection) url.openConnection(Proxy.NO_PROXY);
            }
        };

        requestFactory.setConnectTimeout(Duration.ofSeconds(10));
        requestFactory.setReadTimeout(Duration.ofSeconds(300));

        return builder
                .requestFactory(() -> requestFactory)
                .setConnectTimeout(Duration.ofSeconds(10))
                .setReadTimeout(Duration.ofSeconds(300))
                .build();
    }
}
