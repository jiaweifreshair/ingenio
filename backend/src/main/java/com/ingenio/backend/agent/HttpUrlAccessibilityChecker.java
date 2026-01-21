package com.ingenio.backend.agent;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.HttpURLConnection;
import java.net.URL;

/**
 * HTTP URL可达性检查器。
 *
 * 是什么：基于HTTP HEAD请求的URL可达性检测实现。
 * 做什么：对原型预览URL执行真实网络探测，判断服务是否可用。
 * 为什么：满足生产环境严格检测要求，避免错误进入Execute阶段。
 */
@Slf4j
@Component
public class HttpUrlAccessibilityChecker implements UrlAccessibilityChecker {

    /**
     * URL可达性检测超时时间（毫秒）。
     *
     * 是什么：网络连接与读取超时阈值。
     * 做什么：限制检测耗时，避免阻塞执行链路。
     * 为什么：平衡严格检测与系统响应性能。
     */
    private static final int URL_CHECK_TIMEOUT_MS = 5000;

    @Override
    public boolean isAccessible(String urlString) {
        try {
            URL url = new URL(urlString);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("HEAD");
            connection.setConnectTimeout(URL_CHECK_TIMEOUT_MS);
            connection.setReadTimeout(URL_CHECK_TIMEOUT_MS);

            int responseCode = connection.getResponseCode();
            connection.disconnect();

            // 2xx 或 3xx 响应码表示可访问
            return responseCode >= 200 && responseCode < 400;
        } catch (Exception e) {
            log.warn("URL可访问性检查失败 - url: {}, error: {}", urlString, e.getMessage());
            return false;
        }
    }
}
