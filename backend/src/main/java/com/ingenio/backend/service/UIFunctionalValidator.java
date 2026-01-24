package com.ingenio.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Service
public class UIFunctionalValidator {

    private static final Logger log = LoggerFactory.getLogger(UIFunctionalValidator.class);

    public ValidationResult validateScrollability(String htmlContent, String cssContent) {
        List<String> issues = new ArrayList<>();

        // 检测1: body是否缺少overflow-y设置
        if (!cssContent.contains("overflow-y") && !cssContent.contains("overflow: auto")) {
            issues.add("CSS缺少滚动设置: body应包含 'overflow-y: auto' 或 'overflow: auto'");
        }

        // 检测2: 是否有固定背景但缺少滚动容器
        if (cssContent.contains("background-attachment: fixed") || cssContent.contains("bg-fixed")) {
            if (!cssContent.contains("min-height: 100vh")) {
                issues.add("检测到固定背景但缺少 'min-height: 100vh'，可能导致页面不可滚动");
            }
        }

        // 检测3: html/body是否设置了正确的高度
        if (!cssContent.contains("min-height") && !cssContent.contains("height: 100%")) {
            issues.add("建议为html/body添加 'min-height: 100vh' 以确保页面可滚动");
        }

        return new ValidationResult(!issues.isEmpty(), issues);
    }

    public record ValidationResult(boolean hasIssues, List<String> issues) {}
}
