package com.ingenio.backend.common.filter;

import cn.dev33.satoken.stp.StpUtil;
import com.ingenio.backend.common.context.TenantContextHolder;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;

/**
 * 从 Sa-Token session 中提取租户ID，并将其放入 TenantContextHolder。
 *
 * 重要说明：
 * - 本过滤器依赖 Sa-Token 的 Web 上下文（SaHolder/StpUtil）才能读取登录态与 Session。
 * - 若过滤器顺序早于 Sa-Token 的上下文初始化阶段，会触发 InvalidContextException 并导致请求 500。
 * - 因此这里将顺序放在较靠后的位置，并对“无上下文/非 Web 请求”做兜底，保证公开接口也能正常访问。
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 20)
public class TenantContextFilter implements Filter {

    /**
     * 从配置文件中读取默认租户ID，作为兜底。
     */
    @Value("${ingenio.tenant.default-tenant-id:00000000-0000-0000-0000-000000000001}")
    private String defaultTenantId;
    
    public static final String TENANT_ID_SESSION_KEY = "tenant_id";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        try {
            String tenantId = null;
            try {
                // 核心保护代码块: 尝试从 Sa-Token 获取会话信息
                // 在非Web上下文（如某些内部调用或Actuator）中，此代码可能抛出 NotWebContextException/InvalidContextException
                if (StpUtil.isLogin()) {
                    tenantId = StpUtil.getSession().getString(TENANT_ID_SESSION_KEY);
                }
            } catch (cn.dev33.satoken.exception.NotWebContextException e) {
                // 优雅地处理异常：记录一个debug日志（可选），然后继续执行
                // 这意味着当前不是一个需要用户登录的Web请求，因此没有租户ID是正常的
            } catch (cn.dev33.satoken.exception.InvalidContextException e) {
                // Sa-Token 上下文未就绪（通常是过滤器顺序或异步线程导致），直接回退到默认租户，避免请求失败
            }

            // 如果会话中没有，则使用配置文件中的默认值作为兜底
            if (!StringUtils.hasText(tenantId)) {
                tenantId = defaultTenantId;
            }
            
            if (StringUtils.hasText(tenantId)) {
                TenantContextHolder.setTenantId(tenantId);
            }

            chain.doFilter(request, response);
        } finally {
            // 请求处理完毕后，必须清除ThreadLocal，防止内存泄漏
            TenantContextHolder.clear();
        }
    }
}
