package com.ingenio.backend.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.filter.SaServletFilter;
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
        log.info("注册SaToken拦截器: 启用JWT Token验证");

        registry.addInterceptor(new SaInterceptor(handle -> {
            // CORS预检请求（OPTIONS）直接放行，不进行认证检查
            // 浏览器发送跨域请求前会先发送OPTIONS预检请求
            String method = SaHolder.getRequest().getMethod();
            if ("OPTIONS".equalsIgnoreCase(method)) {
                log.debug("放行CORS预检请求: OPTIONS");
                return;  // OPTIONS请求直接返回，不执行checkLogin()
            }

            // 拦截器处理逻辑：自动检查@SaCheckLogin等注解
            // 如果端点有@SaCheckLogin注解，强制验证登录状态
            // 如果没有注解且不在白名单中，也会验证登录状态
            StpUtil.checkLogin();
        }))
        .addPathPatterns("/**") // 拦截所有路径
        .excludePathPatterns(
            // 认证相关公开接口（无需Token）
            "/v1/auth/login",                      // 用户名密码登录
            "/v1/auth/register",                   // 用户注册
            "/v1/auth/reset-password/**",          // 密码重置（请求、验证Token、确认）
            "/v1/auth/wechat/**",                  // 微信扫码登录（生成二维码、检查状态、回调）
            "/v1/auth/oauth/**",                   // OAuth第三方登录（Google、GitHub回调）
            "/v1/auth/health",                     // 健康检查

            // 后台服务接口（Agent调用，无需认证）
            "/v1/openlovable/**",                  // Open-Lovable代理接口（创建沙箱、生成代码、应用代码等）
            "/v1/generate/**",                     // 代码生成接口（意图分析、生成流式代码等）
            "/v1/intent/**",                       // 意图识别接口
            "/v2/**",                              // V2版本接口
            "/v1/prototype/generate/stream",       // 原型SSE流式生成接口（前端匿名访问）
            "/v1/prototype/create-app-spec",       // 原型生成后创建AppSpec（需支持匿名调用）
            "/v1/notifications/unread-count",      // 通知中心未读数量轮询接口，前端需匿名降级

            // SSE流式接口（已包含在上述白名单中）
            "/v1/generate/analyze-stream",

            // API文档和监控（开发/运维使用）
            "/swagger-ui/**",                      // Swagger UI
            "/swagger-ui.html",                    // Swagger UI首页
            "/v3/api-docs/**",                     // OpenAPI 3.0文档
            "/api-docs/**",                        // API文档
            "/swagger-resources/**",               // Swagger资源
            "/webjars/**",                         // Swagger依赖的前端资源
            "/actuator/**",                        // Spring Boot Actuator监控端点

            // 静态资源（如果有）
            "/static/**",
            "/public/**",
            "/favicon.ico",

            // 错误页面
            "/error"
        )
        .order(1); // 拦截器优先级：数字越小优先级越高

        log.info("SaToken拦截器注册成功");
        log.info("  - 拦截规则: 所有路径（/**）");
        log.info("  - 白名单路径: 认证接口、API文档、监控端点等");
        log.info("  - Token验证: Authorization header (Bearer token)");
        log.info("  - Token格式: JWT (token-style: jwt)");
        log.info("  - Token有效期: 7天 (配置在application.yml)");
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
            // 指定拦截路由
            .addInclude("/**")
            // 指定排除路由（与拦截器白名单保持一致）
            .addExclude(
                // 认证相关公开接口
                "/v1/auth/login",
                "/v1/auth/register",
                "/v1/auth/reset-password/**",
                "/v1/auth/wechat/**",
                "/v1/auth/oauth/**",
                "/v1/auth/health",

                // 后台服务接口（Agent调用）
                "/v1/openlovable/**",
                "/v1/generate/**",
                "/v1/intent/**",
                "/v2/**",
                "/v1/prototype/generate/stream",
                "/v1/prototype/create-app-spec",
                "/v1/notifications/unread-count",
                "/v1/generate/analyze-stream",

                // API文档和监控
                "/swagger-ui/**",
                "/swagger-ui.html",
                "/v3/api-docs/**",
                "/api-docs/**",
                "/swagger-resources/**",
                "/webjars/**",
                "/actuator/**",

                // 静态资源
                "/static/**",
                "/public/**",
                "/favicon.ico",
                "/error"
            )
            // 认证函数：每次请求执行
            .setAuth(obj -> {
                // CORS预检请求放行
                String method = SaHolder.getRequest().getMethod();
                if ("OPTIONS".equalsIgnoreCase(method)) {
                    return;
                }
                // 校验登录
                SaRouter.match("/**", r -> StpUtil.checkLogin());
            })
            // 异常处理
            .setError(e -> {
                log.warn("SaToken过滤器拦截: {}", e.getMessage());
                return "{\"code\":\"401\",\"message\":\"" + e.getMessage() + "\",\"success\":false}";
            });
    }
}
