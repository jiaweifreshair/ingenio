/**
 * 风格预览HTML模板生成器
 * 用于快速生成7种设计风格的预览页面
 *
 * @author Ingenio Team
 * @version 2.0.0
 * @since 2025-11-14
 */

import { DesignStyle } from "@/types/design-style";
import {
  getStyleCSSConfig,
  generateStyleCSS,
} from "@/constants/design-styles";

/**
 * 通用HTML模板接口
 */
export interface HTMLTemplateOptions {
  /** 应用名称 */
  appName: string;
  /** 应用描述 */
  appDescription: string;
  /** 功能列表 */
  features: string[];
  /** 是否包含导航栏 */
  includeNav?: boolean;
  /** 是否包含页脚 */
  includeFooter?: boolean;
}

/**
 * 生成完整的HTML预览页面
 *
 * @param style 设计风格
 * @param options HTML模板选项
 * @returns 完整的HTML字符串
 */
export function generateStylePreviewHTML(
  style: DesignStyle,
  options: HTMLTemplateOptions
): string {
  const cssConfig = getStyleCSSConfig(style);
  if (!cssConfig) {
    throw new Error(`Unknown style: ${style}`);
  }

  const css = generateStyleCSS(style);
  const html = generateHTMLBody(style, options);

  return `<!DOCTYPE html>
<html lang="zh-CN">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>${options.appName} - ${style}</title>
  <style>
    ${css}
    ${getBaseStyles()}
    ${getStyleSpecificStyles(style)}
  </style>
</head>
<body>
  ${html}
</body>
</html>`;
}

/**
 * 生成HTML主体内容
 */
function generateHTMLBody(
  style: DesignStyle,
  options: HTMLTemplateOptions
): string {
  const { appName, appDescription, features, includeNav = true, includeFooter = true } = options;

  return `
    <div class="container">
      ${includeNav ? generateNavbar(style, appName) : ""}

      <main class="main-content">
        ${generateHeroSection(style, appName, appDescription)}
        ${generateFeaturesSection(style, features)}
        ${generateCTASection(style, appName)}
      </main>

      ${includeFooter ? generateFooter(style, appName) : ""}
    </div>
  `;
}

/**
 * 生成导航栏
 */
function generateNavbar(_style: DesignStyle, appName: string): string {
  return `
    <nav class="navbar">
      <div class="nav-container">
        <div class="nav-logo">${appName}</div>
        <ul class="nav-menu">
          <li class="nav-item"><a href="#features">功能</a></li>
          <li class="nav-item"><a href="#about">关于</a></li>
          <li class="nav-item"><a href="#contact">联系</a></li>
        </ul>
        <button class="nav-cta">开始使用</button>
      </div>
    </nav>
  `;
}

/**
 * 生成Hero区域
 */
function generateHeroSection(
  _style: DesignStyle,
  appName: string,
  appDescription: string
): string {
  return `
    <section class="hero">
      <div class="hero-content">
        <h1 class="hero-title">${appName}</h1>
        <p class="hero-description">${appDescription}</p>
        <div class="hero-actions">
          <button class="btn btn-primary">立即开始</button>
          <button class="btn btn-secondary">了解更多</button>
        </div>
      </div>
      <div class="hero-image">
        <div class="placeholder-image">📱</div>
      </div>
    </section>
  `;
}

/**
 * 生成功能区域
 */
function generateFeaturesSection(
  _style: DesignStyle,
  features: string[]
): string {
  const featureCards = features
    .slice(0, 6)
    .map(
      (feature, index) => `
      <div class="feature-card">
        <div class="feature-icon">${["🚀", "⚡", "🎨", "🔒", "📊", "💡"][index]}</div>
        <h3 class="feature-title">${feature}</h3>
        <p class="feature-description">为您提供专业的${feature}解决方案</p>
      </div>
    `
    )
    .join("");

  return `
    <section class="features" id="features">
      <h2 class="section-title">核心功能</h2>
      <div class="features-grid">
        ${featureCards}
      </div>
    </section>
  `;
}

/**
 * 生成CTA区域
 */
function generateCTASection(_style: DesignStyle, appName: string): string {
  return `
    <section class="cta">
      <div class="cta-content">
        <h2 class="cta-title">准备好开始了吗？</h2>
        <p class="cta-description">立即体验${appName}，让工作更高效</p>
        <button class="btn btn-large">免费试用</button>
      </div>
    </section>
  `;
}

/**
 * 生成页脚
 */
function generateFooter(_style: DesignStyle, appName: string): string {
  return `
    <footer class="footer">
      <div class="footer-content">
        <div class="footer-section">
          <h4>${appName}</h4>
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
        <p>&copy; 2025 ${appName}. All rights reserved.</p>
      </div>
    </footer>
  `;
}

/**
 * 获取基础样式（所有风格共用）
 */
function getBaseStyles(): string {
  return `
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
  `;
}

/**
 * 获取风格特定的样式
 */
function getStyleSpecificStyles(style: DesignStyle): string {
  const config = getStyleCSSConfig(style);
  return config?.customCSS || "";
}

/**
 * 批量生成7种风格的HTML预览
 */
export function generateAll7StylePreviews(
  options: HTMLTemplateOptions
): Map<DesignStyle, string> {
  const previews = new Map<DesignStyle, string>();

  const styles = Object.values(DesignStyle);
  styles.forEach((style) => {
    try {
      const html = generateStylePreviewHTML(style, options);
      previews.set(style, html);
    } catch (error) {
      console.error(`Failed to generate preview for style ${style}:`, error);
    }
  });

  return previews;
}
