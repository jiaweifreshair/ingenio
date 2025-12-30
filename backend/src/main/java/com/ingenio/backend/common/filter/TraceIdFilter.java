package com.ingenio.backend.common.filter;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.UUID;

/**
 * 注入 traceId 到 MDC，用于全链路日志追踪。
 * 使用 @Order(Ordered.HIGHEST_PRECEDENCE) 确保这是最先执行的过滤器之一。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter implements Filter {

    private static final String TRACE_ID_HEADER = "X-Trace-Id";
    private static final String TRACE_ID_MDC_KEY = "traceId";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        String traceId = extractOrGenerateTraceId(request);
        MDC.put(TRACE_ID_MDC_KEY, traceId);
        // 将 traceId 回传到响应头，便于前端/网关侧串联请求与后端日志
        if (response instanceof HttpServletResponse httpServletResponse) {
            httpServletResponse.setHeader(TRACE_ID_HEADER, traceId);
        }
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(TRACE_ID_MDC_KEY);
        }
    }

    private String extractOrGenerateTraceId(ServletRequest request) {
        String traceId = null;
        if (request instanceof HttpServletRequest httpServletRequest) {
            traceId = httpServletRequest.getHeader(TRACE_ID_HEADER);
        }

        if (StringUtils.hasText(traceId)) {
            return traceId;
        } else {
            return UUID.randomUUID().toString();
        }
    }
}
