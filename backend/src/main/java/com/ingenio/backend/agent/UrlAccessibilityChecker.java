package com.ingenio.backend.agent;

/**
 * URL可达性检查器。
 *
 * 是什么：定义原型预览URL可达性检测的统一接口。
 * 做什么：对外提供可访问性判断能力，屏蔽具体检测实现细节。
 * 为什么：保留严格检测要求，同时支持测试环境替换实现以保证稳定性。
 */
public interface UrlAccessibilityChecker {

    /**
     * 判断URL是否可访问。
     *
     * @param urlString 待检测的URL
     * @return true表示可访问，false表示不可访问
     */
    boolean isAccessible(String urlString);
}
