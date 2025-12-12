package com.ingenio.backend.config;

import cn.dev33.satoken.strategy.SaStrategy;
import cn.dev33.satoken.stp.StpUtil;
import jakarta.annotation.PostConstruct;
import jakarta.mail.internet.MimeMessage;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.Ordered;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessagePreparator;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

/**
 * SaToken测试环境配置
 *
 * 功能：
 * - 禁用SaToken拦截器，允许所有请求通过
 * - 禁用所有@SaCheckXxx注解检查
 * - 使用Filter在每个请求前模拟登录状态
 * - 仅在测试环境激活
 *
 * @author Ingenio Team
 * @since 1.0.0
 */
@Slf4j
@TestConfiguration
public class TestSaTokenConfig implements WebMvcConfigurer {

    /**
     * 测试环境的模拟用户ID
     * 在所有E2E测试中，StpUtil.getLoginIdAsString()将返回此值
     */
    public static final UUID TEST_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    /**
     * 测试环境的模拟租户ID
     */
    public static final UUID TEST_TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    /**
     * 禁用SaToken拦截器，允许所有测试请求通过
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        log.info("测试环境：禁用SaToken拦截器");

        // 注意：这里不添加SaInterceptor，从而禁用所有SaToken拦截
        // 生产环境会通过正常配置添加SaInterceptor
    }

    /**
     * 配置SaToken全局策略 - 测试环境禁用所有权限检查
     * 使用@PostConstruct在Bean初始化后执行
     */
    @PostConstruct
    public void configureSaStrategy() {
        log.info("测试环境：配置SaToken全局策略");
        log.info("  - 禁用所有@SaCheckXxx注解");
        log.info("  - 模拟已登录用户: userId={}, tenantId={}", TEST_USER_ID, TEST_TENANT_ID);

        // 重写Sa-Token的注解处理器，让所有 @SaCheckXxx 注解都通过
        SaStrategy.instance.isAnnotationPresent = (element, annotationClass) -> false;
    }

    /**
     * 注册模拟登录过滤器
     * 在每个请求处理前自动调用 StpUtil.login() 模拟登录状态
     */
    @Bean
    public FilterRegistrationBean<MockLoginFilter> mockLoginFilter() {
        FilterRegistrationBean<MockLoginFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new MockLoginFilter());
        registration.addUrlPatterns("/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE); // 最高优先级，先于所有其他Filter
        registration.setName("mockLoginFilter");
        log.info("注册MockLoginFilter: 在每个请求前模拟登录状态");
        return registration;
    }

    /**
     * Mock邮件发送器
     * 测试环境不实际发送邮件，仅记录日志
     */
    @Bean
    @Primary
    public JavaMailSender mockJavaMailSender() {
        log.info("测试环境：使用Mock邮件发送器");
        return new JavaMailSender() {
            @Override
            public MimeMessage createMimeMessage() {
                return null;
            }

            @Override
            public MimeMessage createMimeMessage(InputStream contentStream) throws MailException {
                return null;
            }

            @Override
            public void send(MimeMessage mimeMessage) throws MailException {
                log.info("Mock邮件发送器: 模拟发送MimeMessage");
            }

            @Override
            public void send(MimeMessage... mimeMessages) throws MailException {
                log.info("Mock邮件发送器: 模拟发送{}封MimeMessage", mimeMessages.length);
            }

            @Override
            public void send(MimeMessagePreparator mimeMessagePreparator) throws MailException {
                log.info("Mock邮件发送器: 模拟发送MimeMessagePreparator");
            }

            @Override
            public void send(MimeMessagePreparator... mimeMessagePreparators) throws MailException {
                log.info("Mock邮件发送器: 模拟发送{}个MimeMessagePreparator", mimeMessagePreparators.length);
            }

            @Override
            public void send(SimpleMailMessage simpleMessage) throws MailException {
                log.info("Mock邮件发送器: 模拟发送SimpleMailMessage to={}, subject={}",
                        simpleMessage.getTo(), simpleMessage.getSubject());
            }

            @Override
            public void send(SimpleMailMessage... simpleMessages) throws MailException {
                log.info("Mock邮件发送器: 模拟发送{}封SimpleMailMessage", simpleMessages.length);
            }
        };
    }

    /**
     * 模拟登录过滤器
     * 在每个请求前调用 StpUtil.login() 模拟已登录状态
     */
    public static class MockLoginFilter extends OncePerRequestFilter {

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {

            // 模拟用户登录
            StpUtil.login(TEST_USER_ID.toString());

            // 设置Session中的租户ID
            StpUtil.getSession().set("tenantId", TEST_TENANT_ID.toString());

            // 继续请求链
            chain.doFilter(request, response);

            // 注意：不在finally中调用StpUtil.logout()
            // 因为MockMvc测试环境中，请求结束后无web上下文，调用logout会报错
            // 每个测试用例都会创建新的ApplicationContext，登录状态会自动清理
        }
    }
}
