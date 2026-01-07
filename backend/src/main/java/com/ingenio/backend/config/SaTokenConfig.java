package com.ingenio.backend.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.filter.SaServletFilter;
import cn.dev33.satoken.util.SaResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * SaToken配置类
 *
 * 功能：
 * - 注册SaToken拦截器，实现JWT Token验证
 * - 配置认证拦截规则和白名单
 * - 自动验证@SaCheckLogin等注解
 *
 * 拦截策略：
 * - 默认拦截所有路径（/**）
 * - 白名单路径无需认证（登录、注册、OAuth回调、公开API等）
 * - 带有@SaCheckLogin注解的端点强制验证Token
 *
 * Token验证流程：
 * 1. 拦截器检查请求头中的Authorization Token
 * 2. 验证Token签名、过期时间、格式合法性
 * 3. 提取用户ID并注入StpUtil上下文
 * 4. 验证失败抛出NotLoginException（返回401）
 *
 * @author Ingenio Team
 * @since 1.0.0
 */
@Slf4j
@Configuration
public class SaTokenConfig implements WebMvcConfigurer {

    /**
     * 注册SaToken拦截器
     *
     * 拦截器配置说明：
     * - addInterceptor(): 注册SaToken拦截器，启用JWT验证
     * - addPathPatterns("/**"): 拦截所有路径
     * - excludePathPatterns(): 配置白名单，以下路径无需认证
     *
     * @param registry Spring拦截器注册器
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        log.info("注册拦截器...");

        // 注册C端用户拦截器
        registry.addInterceptor(new SaInterceptor(handle -> {
            // CORS预检请求（OPTIONS）直接放行，不进行认证检查
            String method = SaHolder.getRequest().getMethod();
            if ("OPTIONS".equalsIgnoreCase(method)) {
                log.debug("放行CORS预检请求: OPTIONS");
                return;
            }
            StpUtil.checkLogin();
        }))
        .addPathPatterns("/**") // 拦截所有路径
        .excludePathPatterns(
            // 认证相关公开接口（无需Token）
            // 兼容：后端配置了 server.servlet.context-path=/api 时，部分匹配逻辑可能包含前缀
            "/v1/auth/login",                      // 用户名密码登录
            "/api/v1/auth/login",
            "/v1/auth/register",                   // 用户注册
            "/api/v1/auth/register",
            "/v1/auth/reset-password/**",          // 密码重置
            "/api/v1/auth/reset-password/**",
            "/v1/auth/wechat/**",                  // 微信扫码登录
            "/api/v1/auth/wechat/**",
            "/v1/auth/oauth/**",                   // OAuth第三方登录
            "/api/v1/auth/oauth/**",
            "/v1/auth/health",                     // 健康检查
            "/api/v1/auth/health",

            // 后台服务接口（Agent调用，无需认证）
            "/v1/openlovable/**",
            "/api/v1/openlovable/**",
            "/v1/generate/**",
            "/api/v1/generate/**",
            "/v1/intent/**",
            "/api/v1/intent/**",
            "/v2/**",
            "/api/v2/**",
            "/v1/g3/**",
            "/api/v1/g3/**",
            "/v1/prototype/generate/stream",
            "/api/v1/prototype/generate/stream",
            "/v1/prototype/create-app-spec",
            "/api/v1/prototype/create-app-spec",
            "/v1/notifications/unread-count",
            "/api/v1/notifications/unread-count",

            // 模板API（公开接口，无需认证）
            "/v1/templates/**",
            "/api/v1/templates/**",

            // ProductShot API (Pilot)
            "/v1/product-shots/**",
            "/api/v1/product-shots/**",

            // SSE流式接口
            "/v1/generate/analyze-stream",
            "/api/v1/generate/analyze-stream",

            // API文档和监控
            "/swagger-ui/**",
            "/api/swagger-ui/**",
            "/swagger-ui.html",
            "/api/swagger-ui.html",
            "/v3/api-docs/**",
            "/api/v3/api-docs/**",
            "/api-docs/**",
            "/api/api-docs/**",
            "/swagger-resources/**",
            "/api/swagger-resources/**",
            "/webjars/**",
            "/api/webjars/**",
            "/actuator/**",
            "/api/actuator/**",

            // 静态资源
            "/static/**",
            "/api/static/**",
            "/public/**",
            "/api/public/**",
            "/favicon.ico",
            "/api/favicon.ico",

            // 错误页面
            "/error",
            "/api/error",

            // Admin API：由独立的服务间JWT保护，必须排除C端用户登录态校验
            "/admin/**",
            "/api/admin/**"
        )
        .order(1); // 优先级较低

        log.info("  - 已注册C端用户拦截器 (SaInterceptor, 优先级 1)");
        log.info("所有拦截器注册成功");
    }

    /**
     * 注册SaToken Servlet过滤器
     *
     * 重要：sa-token-spring-boot3-starter会自动注册SaPathCheckFilterForJakartaServlet，
     * 该过滤器在Spring MVC拦截器之前执行，需要单独配置白名单。
     * 此Bean覆盖自动配置，确保白名单与拦截器一致。
     *
     * @return SaServletFilter 配置好的过滤器
     */
    @Bean
    public SaServletFilter getSaServletFilter() {
        log.info("注册SaToken Servlet过滤器: 配置路径白名单");

        return new SaServletFilter()
            .addInclude("/**")
            .addExclude(
                // 认证相关公开接口
                "/v1/auth/login",
                "/api/v1/auth/login",
                "/v1/auth/register",
                "/api/v1/auth/register",
                "/v1/auth/reset-password/**",
                "/api/v1/auth/reset-password/**",
                "/v1/auth/wechat/**",
                "/api/v1/auth/wechat/**",
                "/v1/auth/oauth/**",
                "/api/v1/auth/oauth/**",
                "/v1/auth/health",
                "/api/v1/auth/health",

                // 后台服务接口（Agent调用）
                "/v1/openlovable/**",
                "/api/v1/openlovable/**",
                "/v1/generate/**",
                "/api/v1/generate/**",
                "/v1/intent/**",
                "/api/v1/intent/**",
                "/v2/**",
                "/api/v2/**",
                "/v1/g3/**",
                "/api/v1/g3/**",
                "/v1/prototype/generate/stream",
                "/api/v1/prototype/generate/stream",
                "/v1/prototype/create-app-spec",
                "/api/v1/prototype/create-app-spec",
                "/v1/notifications/unread-count",
                "/api/v1/notifications/unread-count",
                "/v1/generate/analyze-stream",
                "/api/v1/generate/analyze-stream",

                // 模板API（公开接口）
                "/v1/templates/**",
                "/api/v1/templates/**",

                // ProductShot API (Pilot)
                "/v1/product-shots/**",
                "/api/v1/product-shots/**",

                // API文档和监控
                "/swagger-ui/**",
                "/api/swagger-ui/**",
                "/swagger-ui.html",
                "/api/swagger-ui.html",
                "/v3/api-docs/**",
                "/api/v3/api-docs/**",
                "/api-docs/**",
                "/api/api-docs/**",
                "/swagger-resources/**",
                "/api/swagger-resources/**",
                "/webjars/**",
                "/api/webjars/**",
                "/actuator/**",
                "/api/actuator/**",

                // 静态资源
                "/static/**",
                "/api/static/**",
                "/public/**",
                "/api/public/**",
                "/favicon.ico",
                "/api/favicon.ico",
                "/error",
                "/api/error",

                // Admin API：由独立的服务间JWT保护，必须排除C端用户登录态校验
                "/admin/**",
                "/api/admin/**"
            )
            // 认证函数：每次请求执行
            .setAuth(obj -> {
                // CORS预检请求放行
                String method = SaHolder.getRequest().getMethod();
                if ("OPTIONS".equalsIgnoreCase(method)) {
                    return;
                }
                // 校验登录：能执行到这里的请求，都已经被 addExclude 排除规则过滤过了
                StpUtil.checkLogin();
            })
            // 异常处理
            .setError(e -> {
                log.warn("SaToken过滤器拦截: {}", e.getMessage());
                return SaResult.error(e.getMessage()).setCode(401);
            });
    }
}
