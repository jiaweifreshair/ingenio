package com.ingenio.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.time.Duration;

/**
 * Web配置
 * 配置CORS、拦截器、HTTP客户端、异步请求超时等Web相关设置
 *
 * @author Ingenio Team
 * @since 1.0.0
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    /**
     * SSE流式响应的异步请求超时时间（毫秒）
     * 默认10分钟，AI代码生成可能需要较长时间
     */
    @Value("${spring.mvc.async.request-timeout:600000}")
    private long asyncRequestTimeout;

    /**
     * 配置异步请求支持
     * 设置SSE流式响应的超时时间，防止AI代码生成过程中连接被中断
     *
     * 重要：Spring MVC默认异步超时为30秒，这对于AI代码生成来说太短了
     * 必须通过此方法显式配置超时时间，否则application.yml中的配置不会生效
     */
    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        configurer.setDefaultTimeout(asyncRequestTimeout);
    }

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
     * 注册通用 CORS 过滤器
     *
     * 是什么：基于 Servlet Filter 的全局跨域配置。
     * 做什么：为所有请求补齐 CORS 响应头（含 SSE / 预检）。
     * 为什么：在某些过滤器链或代理场景下，WebMvcConfigurer 可能未生效。
     * 说明：设置最高优先级，确保在认证过滤器之前写入跨域响应头。
     */
    @Bean
    public FilterRegistrationBean<CorsFilter> corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(java.util.List.of(
                "http://localhost:*",
                "http://127.0.0.1:*",
                "http://0.0.0.0:*",
                "http://[::1]:*",
                "http://192.168.*.*:*"
        ));
        config.setAllowedMethods(java.util.List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        config.setAllowedHeaders(java.util.List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        CorsFilter corsFilter = new CorsFilter(source);

        FilterRegistrationBean<CorsFilter> registration = new FilterRegistrationBean<>(corsFilter);
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
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
