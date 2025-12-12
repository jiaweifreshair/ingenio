package com.ingenio.backend.service;

import com.ingenio.backend.enums.DesignStyle;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 风格模板HTML生成器
 * 基于前端 src/templates/style-preview-template.ts 移植
 *
 * 核心功能:
 * 1. 生成7种设计风格的HTML预览页面
 * 2. 使用CSS变量系统实现主题切换
 * 3. 支持响应式布局
 * 4. 每个风格2-3秒生成完成
 *
 * @author Ingenio Team
 * @version 2.0.0
 * @since 2025-11-14
 */
@Slf4j
@Service
public class StyleTemplateGenerator {

    /**
     * 生成完整的HTML预览页面
     *
     * @param style 设计风格
     * @param appName 应用名称
     * @param appDescription 应用描述
     * @param features 功能列表
     * @return 完整的HTML字符串
     */
    public String generateStylePreviewHTML(
            DesignStyle style,
            String appName,
            String appDescription,
            List<String> features
    ) {
        log.debug("生成{}风格预览: appName={}", style.getDisplayName(), appName);

        String css = generateStyleCSS(style);
        String html = generateHTMLBody(style, appName, appDescription, features);

        return String.format("""
                <!DOCTYPE html>
                <html lang="zh-CN">
                <head>
                  <meta charset="UTF-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1.0">
                  <title>%s - %s</title>
                  <style>
                    %s
                    %s
                    %s
                  </style>
                </head>
                <body>
                  %s
                </body>
                </html>
                """,
                appName, style.getDisplayName(),
                css,
                getBaseStyles(),
                getStyleSpecificStyles(style),
                html
        );
    }

    /**
     * 生成风格的CSS字符串
     * 基于前端 design-styles.ts#generateStyleCSS
     */
    private String generateStyleCSS(DesignStyle style) {
        Map<String, String> cssVars = getStyleCSSVariables(style);
        Map<String, String> animations = getStyleAnimations(style);

        StringBuilder css = new StringBuilder(":root {\n");

        // CSS变量
        cssVars.forEach((key, value) -> {
            String cssVarName = camelToKebabCase(key);
            css.append(String.format("  --%s: %s;\n", cssVarName, value));
        });

        // 动画变量
        if (animations != null) {
            css.append(String.format("  --animation-duration: %s;\n", animations.get("duration")));
            css.append(String.format("  --animation-easing: %s;\n", animations.get("easing")));
        }

        css.append("}\n\n");

        // 自定义CSS
        String customCSS = getCustomCSS(style);
        if (customCSS != null && !customCSS.isEmpty()) {
            css.append(customCSS).append("\n");
        }

        return css.toString();
    }

    /**
     * 获取风格的CSS变量配置
     * 移植自前端 design-styles.ts#STYLE_CSS_CONFIG_MAP
     */
    private Map<String, String> getStyleCSSVariables(DesignStyle style) {
        return switch (style) {
            case MODERN_MINIMAL -> Map.ofEntries(
                    Map.entry("primary", "#1a1a1a"),
                    Map.entry("secondary", "#6b7280"),
                    Map.entry("accent", "#3b82f6"),
                    Map.entry("background", "#ffffff"),
                    Map.entry("surface", "#f9fafb"),
                    Map.entry("text", "#111827"),
                    Map.entry("textSecondary", "#6b7280"),
                    Map.entry("border", "#e5e7eb"),
                    Map.entry("radiusSmall", "8px"),
                    Map.entry("radiusMedium", "12px"),
                    Map.entry("radiusLarge", "16px"),
                    Map.entry("shadowSmall", "0 1px 2px 0 rgba(0,0,0,0.05)"),
                    Map.entry("shadowMedium", "0 4px 6px -1px rgba(0,0,0,0.1)"),
                    Map.entry("shadowLarge", "0 20px 25px -5px rgba(0,0,0,0.1)"),
                    Map.entry("spacingUnit", "8px"),
                    Map.entry("fontFamily", "-apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif"),
                    Map.entry("fontSizeBase", "16px"),
                    Map.entry("lineHeight", "1.6")
            );
            case VIBRANT_FASHION -> Map.ofEntries(
                    Map.entry("primary", "#8b5cf6"),
                    Map.entry("secondary", "#ec4899"),
                    Map.entry("accent", "#f59e0b"),
                    Map.entry("background", "#ffffff"),
                    Map.entry("surface", "linear-gradient(135deg, #667eea 0%, #764ba2 100%)"),
                    Map.entry("text", "#1f2937"),
                    Map.entry("textSecondary", "#6b7280"),
                    Map.entry("border", "#e5e7eb"),
                    Map.entry("radiusSmall", "16px"),
                    Map.entry("radiusMedium", "24px"),
                    Map.entry("radiusLarge", "32px"),
                    Map.entry("shadowSmall", "0 4px 6px rgba(139,92,246,0.1)"),
                    Map.entry("shadowMedium", "0 10px 15px rgba(139,92,246,0.2)"),
                    Map.entry("shadowLarge", "0 25px 50px rgba(139,92,246,0.3)"),
                    Map.entry("spacingUnit", "12px"),
                    Map.entry("fontFamily", "'Inter', -apple-system, sans-serif"),
                    Map.entry("fontSizeBase", "16px"),
                    Map.entry("lineHeight", "1.7")
            );
            case CLASSIC_PROFESSIONAL -> Map.ofEntries(
                    Map.entry("primary", "#1e40af"),
                    Map.entry("secondary", "#475569"),
                    Map.entry("accent", "#0284c7"),
                    Map.entry("background", "#f8fafc"),
                    Map.entry("surface", "#ffffff"),
                    Map.entry("text", "#0f172a"),
                    Map.entry("textSecondary", "#64748b"),
                    Map.entry("border", "#cbd5e1"),
                    Map.entry("radiusSmall", "4px"),
                    Map.entry("radiusMedium", "6px"),
                    Map.entry("radiusLarge", "8px"),
                    Map.entry("shadowSmall", "0 1px 3px rgba(0,0,0,0.1)"),
                    Map.entry("shadowMedium", "0 2px 4px rgba(0,0,0,0.1)"),
                    Map.entry("shadowLarge", "0 4px 6px rgba(0,0,0,0.1)"),
                    Map.entry("spacingUnit", "8px"),
                    Map.entry("fontFamily", "'Roboto', 'Arial', sans-serif"),
                    Map.entry("fontSizeBase", "14px"),
                    Map.entry("lineHeight", "1.5")
            );
            case FUTURE_TECH -> Map.ofEntries(
                    Map.entry("primary", "#06b6d4"),
                    Map.entry("secondary", "#8b5cf6"),
                    Map.entry("accent", "#f59e0b"),
                    Map.entry("background", "#0a0a0a"),
                    Map.entry("surface", "#1a1a1a"),
                    Map.entry("text", "#f0f0f0"),
                    Map.entry("textSecondary", "#a0a0a0"),
                    Map.entry("border", "#333333"),
                    Map.entry("radiusSmall", "8px"),
                    Map.entry("radiusMedium", "12px"),
                    Map.entry("radiusLarge", "16px"),
                    Map.entry("shadowSmall", "0 0 10px rgba(6,182,212,0.3)"),
                    Map.entry("shadowMedium", "0 0 20px rgba(6,182,212,0.4)"),
                    Map.entry("shadowLarge", "0 0 40px rgba(6,182,212,0.5)"),
                    Map.entry("spacingUnit", "12px"),
                    Map.entry("fontFamily", "'JetBrains Mono', 'Courier New', monospace"),
                    Map.entry("fontSizeBase", "16px"),
                    Map.entry("lineHeight", "1.6")
            );
            case IMMERSIVE_3D -> Map.ofEntries(
                    Map.entry("primary", "#6366f1"),
                    Map.entry("secondary", "#8b5cf6"),
                    Map.entry("accent", "#ec4899"),
                    Map.entry("background", "linear-gradient(135deg, #667eea 0%, #764ba2 100%)"),
                    Map.entry("surface", "rgba(255, 255, 255, 0.1)"),
                    Map.entry("text", "#ffffff"),
                    Map.entry("textSecondary", "rgba(255, 255, 255, 0.7)"),
                    Map.entry("border", "rgba(255, 255, 255, 0.2)"),
                    Map.entry("radiusSmall", "16px"),
                    Map.entry("radiusMedium", "24px"),
                    Map.entry("radiusLarge", "32px"),
                    Map.entry("shadowSmall", "0 8px 32px rgba(0,0,0,0.1)"),
                    Map.entry("shadowMedium", "0 16px 64px rgba(0,0,0,0.2)"),
                    Map.entry("shadowLarge", "0 32px 128px rgba(0,0,0,0.3)"),
                    Map.entry("spacingUnit", "16px"),
                    Map.entry("fontFamily", "'SF Pro Display', -apple-system, sans-serif"),
                    Map.entry("fontSizeBase", "17px"),
                    Map.entry("lineHeight", "1.7")
            );
            case GAMIFIED -> Map.ofEntries(
                    Map.entry("primary", "#f59e0b"),
                    Map.entry("secondary", "#ef4444"),
                    Map.entry("accent", "#10b981"),
                    Map.entry("background", "#fef3c7"),
                    Map.entry("surface", "#ffffff"),
                    Map.entry("text", "#78350f"),
                    Map.entry("textSecondary", "#92400e"),
                    Map.entry("border", "#fbbf24"),
                    Map.entry("radiusSmall", "12px"),
                    Map.entry("radiusMedium", "20px"),
                    Map.entry("radiusLarge", "28px"),
                    Map.entry("shadowSmall", "0 4px 0 #d97706"),
                    Map.entry("shadowMedium", "0 6px 0 #d97706"),
                    Map.entry("shadowLarge", "0 8px 0 #d97706"),
                    Map.entry("spacingUnit", "12px"),
                    Map.entry("fontFamily", "'Comic Sans MS', 'Comic Neue', cursive"),
                    Map.entry("fontSizeBase", "18px"),
                    Map.entry("lineHeight", "1.8")
            );
            case NATURAL_FLOW -> Map.ofEntries(
                    Map.entry("primary", "#10b981"),
                    Map.entry("secondary", "#14b8a6"),
                    Map.entry("accent", "#f59e0b"),
                    Map.entry("background", "#f0fdf4"),
                    Map.entry("surface", "#ffffff"),
                    Map.entry("text", "#064e3b"),
                    Map.entry("textSecondary", "#059669"),
                    Map.entry("border", "#d1fae5"),
                    Map.entry("radiusSmall", "20px"),
                    Map.entry("radiusMedium", "32px"),
                    Map.entry("radiusLarge", "48px"),
                    Map.entry("shadowSmall", "0 4px 16px rgba(16,185,129,0.1)"),
                    Map.entry("shadowMedium", "0 8px 32px rgba(16,185,129,0.15)"),
                    Map.entry("shadowLarge", "0 16px 64px rgba(16,185,129,0.2)"),
                    Map.entry("spacingUnit", "16px"),
                    Map.entry("fontFamily", "'Georgia', serif"),
                    Map.entry("fontSizeBase", "17px"),
                    Map.entry("lineHeight", "1.8")
            );
        };
    }

    /**
     * 获取风格的动画配置
     */
    private Map<String, String> getStyleAnimations(DesignStyle style) {
        return switch (style) {
            case MODERN_MINIMAL -> Map.of("duration", "0.2s", "easing", "ease-out");
            case VIBRANT_FASHION -> Map.of("duration", "0.3s", "easing", "cubic-bezier(0.4, 0, 0.2, 1)");
            case CLASSIC_PROFESSIONAL -> Map.of("duration", "0.15s", "easing", "ease-in-out");
            case FUTURE_TECH -> Map.of("duration", "0.4s", "easing", "cubic-bezier(0.68, -0.55, 0.265, 1.55)");
            case IMMERSIVE_3D -> Map.of("duration", "0.5s", "easing", "cubic-bezier(0.22, 1, 0.36, 1)");
            case GAMIFIED -> Map.of("duration", "0.1s", "easing", "ease-in-out");
            case NATURAL_FLOW -> Map.of("duration", "0.6s", "easing", "cubic-bezier(0.25, 0.46, 0.45, 0.94)");
        };
    }

    /**
     * 获取风格特定的自定义CSS
     */
    private String getCustomCSS(DesignStyle style) {
        return switch (style) {
            case FUTURE_TECH -> """
                    * {
                      text-shadow: 0 0 10px rgba(6,182,212,0.5);
                    }
                    .glow {
                      box-shadow: 0 0 20px rgba(6,182,212,0.6),
                                  0 0 40px rgba(139,92,246,0.4);
                    }
                    """;
            case IMMERSIVE_3D -> """
                    .glass {
                      background: rgba(255, 255, 255, 0.1);
                      backdrop-filter: blur(10px);
                      border: 1px solid rgba(255, 255, 255, 0.2);
                    }
                    """;
            case GAMIFIED -> """
                    .button {
                      border: 3px solid #78350f;
                      transform: translateY(0);
                      transition: all 0.1s;
                    }
                    .button:active {
                      transform: translateY(4px);
                      box-shadow: none;
                    }
                    """;
            case NATURAL_FLOW -> """
                    * {
                      border-radius: 24px;
                    }
                    .organic {
                      clip-path: ellipse(80% 100% at 50% 0%);
                    }
                    """;
            default -> "";
        };
    }

    /**
     * 生成HTML主体内容
     */
    private String generateHTMLBody(
            DesignStyle style,
            String appName,
            String appDescription,
            List<String> features
    ) {
        return String.format("""
                    <div class="container">
                      %s

                      <main class="main-content">
                        %s
                        %s
                        %s
                      </main>

                      %s
                    </div>
                """,
                generateNavbar(appName),
                generateHeroSection(appName, appDescription),
                generateFeaturesSection(features),
                generateCTASection(appName),
                generateFooter(appName)
        );
    }

    /**
     * 生成导航栏
     */
    private String generateNavbar(String appName) {
        return String.format("""
                    <nav class="navbar">
                      <div class="nav-container">
                        <div class="nav-logo">%s</div>
                        <ul class="nav-menu">
                          <li class="nav-item"><a href="#features">功能</a></li>
                          <li class="nav-item"><a href="#about">关于</a></li>
                          <li class="nav-item"><a href="#contact">联系</a></li>
                        </ul>
                        <button class="nav-cta">开始使用</button>
                      </div>
                    </nav>
                """, appName);
    }

    /**
     * 生成Hero区域
     */
    private String generateHeroSection(String appName, String appDescription) {
        return String.format("""
                    <section class="hero">
                      <div class="hero-content">
                        <h1 class="hero-title">%s</h1>
                        <p class="hero-description">%s</p>
                        <div class="hero-actions">
                          <button class="btn btn-primary">立即开始</button>
                          <button class="btn btn-secondary">了解更多</button>
                        </div>
                      </div>
                      <div class="hero-image">
                        <div class="placeholder-image">📱</div>
                      </div>
                    </section>
                """, appName, appDescription);
    }

    /**
     * 生成功能区域
     */
    private String generateFeaturesSection(List<String> features) {
        String[] icons = {"🚀", "⚡", "🎨", "🔒", "📊", "💡"};
        List<String> limitedFeatures = features.size() > 6 ? features.subList(0, 6) : features;

        StringBuilder featureCards = new StringBuilder();
        for (int i = 0; i < limitedFeatures.size(); i++) {
            String feature = limitedFeatures.get(i);
            String icon = icons[i % icons.length];
            featureCards.append(String.format("""
                          <div class="feature-card">
                            <div class="feature-icon">%s</div>
                            <h3 class="feature-title">%s</h3>
                            <p class="feature-description">为您提供专业的%s解决方案</p>
                          </div>
                    """, icon, feature, feature));
        }

        return String.format("""
                    <section class="features" id="features">
                      <h2 class="section-title">核心功能</h2>
                      <div class="features-grid">
                        %s
                      </div>
                    </section>
                """, featureCards);
    }

    /**
     * 生成CTA区域
     */
    private String generateCTASection(String appName) {
        return String.format("""
                    <section class="cta">
                      <div class="cta-content">
                        <h2 class="cta-title">准备好开始了吗？</h2>
                        <p class="cta-description">立即体验%s，让工作更高效</p>
                        <button class="btn btn-large">免费试用</button>
                      </div>
                    </section>
                """, appName);
    }

    /**
     * 生成页脚
     */
    private String generateFooter(String appName) {
        return String.format("""
                    <footer class="footer">
                      <div class="footer-content">
                        <div class="footer-section">
                          <h4>%s</h4>
                          <p>由Ingenio AI生成</p>
                        </div>
                        <div class="footer-section">
                          <h4>产品</h4>
                          <ul>
                            <li><a href="#">功能</a></li>
                            <li><a href="#">定价</a></li>
                            <li><a href="#">文档</a></li>
                          </ul>
                        </div>
                        <div class="footer-section">
                          <h4>公司</h4>
                          <ul>
                            <li><a href="#">关于我们</a></li>
                            <li><a href="#">博客</a></li>
                            <li><a href="#">联系</a></li>
                          </ul>
                        </div>
                      </div>
                      <div class="footer-bottom">
                        <p>&copy; 2025 %s. All rights reserved.</p>
                      </div>
                    </footer>
                """, appName, appName);
    }

    /**
     * 获取基础样式（所有风格共用）
     * 完整移植自frontend src/templates/style-preview-template.ts#getBaseStyles
     *
     * 包含:
     * - CSS Reset和基础排版
     * - 导航栏布局和样式
     * - Hero区域响应式布局
     * - 功能卡片网格系统
     * - CTA渐变背景
     * - 页脚三栏布局
     * - 响应式媒体查询
     */
    private String getBaseStyles() {
        return """
                * {
                  margin: 0;
                  padding: 0;
                  box-sizing: border-box;
                }

                body {
                  font-family: var(--font-family);
                  font-size: var(--font-size-base);
                  line-height: var(--line-height);
                  color: var(--text);
                  background: var(--background);
                  overflow-x: hidden;
                }

                .container {
                  width: 100%;
                  min-height: 100vh;
                  display: flex;
                  flex-direction: column;
                }

                /* 导航栏 */
                .navbar {
                  background: var(--surface);
                  border-bottom: 1px solid var(--border);
                  padding: 1rem 0;
                  position: sticky;
                  top: 0;
                  z-index: 100;
                }

                .nav-container {
                  max-width: 1200px;
                  margin: 0 auto;
                  padding: 0 2rem;
                  display: flex;
                  justify-content: space-between;
                  align-items: center;
                }

                .nav-logo {
                  font-size: 1.5rem;
                  font-weight: bold;
                  color: var(--primary);
                }

                .nav-menu {
                  display: flex;
                  gap: 2rem;
                  list-style: none;
                }

                .nav-item a {
                  color: var(--text-secondary);
                  text-decoration: none;
                  transition: color var(--animation-duration) var(--animation-easing);
                }

                .nav-item a:hover {
                  color: var(--primary);
                }

                .nav-cta {
                  background: var(--primary);
                  color: white;
                  border: none;
                  padding: 0.75rem 1.5rem;
                  border-radius: var(--radius-medium);
                  cursor: pointer;
                  font-size: 1rem;
                  font-weight: 600;
                  transition: all var(--animation-duration) var(--animation-easing);
                }

                .nav-cta:hover {
                  transform: translateY(-2px);
                  box-shadow: var(--shadow-medium);
                }

                /* 主内容 */
                .main-content {
                  flex: 1;
                  max-width: 1200px;
                  width: 100%;
                  margin: 0 auto;
                  padding: 0 2rem;
                }

                /* Hero区域 */
                .hero {
                  display: grid;
                  grid-template-columns: 1fr 1fr;
                  gap: 4rem;
                  align-items: center;
                  min-height: 600px;
                  padding: 4rem 0;
                }

                .hero-title {
                  font-size: 3.5rem;
                  font-weight: bold;
                  color: var(--text);
                  margin-bottom: 1.5rem;
                  line-height: 1.2;
                }

                .hero-description {
                  font-size: 1.25rem;
                  color: var(--text-secondary);
                  margin-bottom: 2rem;
                  line-height: 1.6;
                }

                .hero-actions {
                  display: flex;
                  gap: 1rem;
                }

                .btn {
                  padding: 1rem 2rem;
                  border: none;
                  border-radius: var(--radius-medium);
                  font-size: 1rem;
                  font-weight: 600;
                  cursor: pointer;
                  transition: all var(--animation-duration) var(--animation-easing);
                }

                .btn-primary {
                  background: var(--primary);
                  color: white;
                }

                .btn-primary:hover {
                  transform: translateY(-2px);
                  box-shadow: var(--shadow-large);
                }

                .btn-secondary {
                  background: transparent;
                  color: var(--primary);
                  border: 2px solid var(--primary);
                }

                .btn-secondary:hover {
                  background: var(--primary);
                  color: white;
                }

                .btn-large {
                  padding: 1.25rem 2.5rem;
                  font-size: 1.125rem;
                }

                .placeholder-image {
                  width: 100%;
                  aspect-ratio: 1;
                  background: var(--surface);
                  border-radius: var(--radius-large);
                  display: flex;
                  align-items: center;
                  justify-content: center;
                  font-size: 8rem;
                  box-shadow: var(--shadow-large);
                }

                /* 功能区域 */
                .features {
                  padding: 6rem 0;
                }

                .section-title {
                  text-align: center;
                  font-size: 2.5rem;
                  font-weight: bold;
                  color: var(--text);
                  margin-bottom: 4rem;
                }

                .features-grid {
                  display: grid;
                  grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
                  gap: 2rem;
                }

                .feature-card {
                  background: var(--surface);
                  padding: 2rem;
                  border-radius: var(--radius-large);
                  border: 1px solid var(--border);
                  box-shadow: var(--shadow-small);
                  transition: all var(--animation-duration) var(--animation-easing);
                }

                .feature-card:hover {
                  transform: translateY(-4px);
                  box-shadow: var(--shadow-large);
                }

                .feature-icon {
                  font-size: 3rem;
                  margin-bottom: 1rem;
                }

                .feature-title {
                  font-size: 1.5rem;
                  font-weight: bold;
                  color: var(--text);
                  margin-bottom: 0.75rem;
                }

                .feature-description {
                  color: var(--text-secondary);
                  line-height: 1.6;
                }

                /* CTA区域 */
                .cta {
                  background: linear-gradient(135deg, var(--primary) 0%, var(--secondary) 100%);
                  border-radius: var(--radius-large);
                  padding: 6rem 4rem;
                  margin: 4rem 0;
                  text-align: center;
                  color: white;
                }

                .cta-title {
                  font-size: 3rem;
                  font-weight: bold;
                  margin-bottom: 1rem;
                }

                .cta-description {
                  font-size: 1.25rem;
                  margin-bottom: 2rem;
                  opacity: 0.9;
                }

                .cta .btn {
                  background: white;
                  color: var(--primary);
                }

                /* 页脚 */
                .footer {
                  background: var(--surface);
                  border-top: 1px solid var(--border);
                  padding: 4rem 0 2rem;
                  margin-top: 4rem;
                }

                .footer-content {
                  max-width: 1200px;
                  margin: 0 auto;
                  padding: 0 2rem;
                  display: grid;
                  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
                  gap: 3rem;
                  margin-bottom: 2rem;
                }

                .footer-section h4 {
                  color: var(--text);
                  margin-bottom: 1rem;
                  font-size: 1.125rem;
                }

                .footer-section ul {
                  list-style: none;
                }

                .footer-section li {
                  margin-bottom: 0.5rem;
                }

                .footer-section a {
                  color: var(--text-secondary);
                  text-decoration: none;
                  transition: color var(--animation-duration) var(--animation-easing);
                }

                .footer-section a:hover {
                  color: var(--primary);
                }

                .footer-section p {
                  color: var(--text-secondary);
                }

                .footer-bottom {
                  max-width: 1200px;
                  margin: 0 auto;
                  padding: 2rem 2rem 0;
                  border-top: 1px solid var(--border);
                  text-align: center;
                  color: var(--text-secondary);
                }

                /* 响应式 */
                @media (max-width: 768px) {
                  .hero {
                    grid-template-columns: 1fr;
                    gap: 2rem;
                  }

                  .hero-title {
                    font-size: 2.5rem;
                  }

                  .nav-menu {
                    display: none;
                  }

                  .features-grid {
                    grid-template-columns: 1fr;
                  }

                  .cta {
                    padding: 4rem 2rem;
                  }

                  .cta-title {
                    font-size: 2rem;
                  }
                }
                """;
    }

    /**
     * 获取风格特定的样式
     */
    private String getStyleSpecificStyles(DesignStyle style) {
        return ""; // 基本样式已经足够，特殊样式在customCSS中定义
    }

    /**
     * 驼峰命名转短横线命名
     * 例如: primaryColor -> primary-color
     */
    private String camelToKebabCase(String camelCase) {
        return camelCase.replaceAll("([a-z])([A-Z])", "$1-$2").toLowerCase();
    }
}
