/**
 * Styles API Client - 设计风格API客户端
 *
 * 功能：
 * - 生成7种设计风格的快速预览（A-G）
 * - 支持Mock模式（Phase 5-6前端开发）和真实API模式（Phase 7-9后端开发）
 * - 返回简化的HTML/CSS预览内容
 *
 * @author Ingenio Team
 * @version 2.0.0
 * @since 2025-11-15
 */

import {
  DesignStyle,
  getAllStyles,
  getStyleDisplayInfo,
  Generate7StylesRequest,
  Generate7StylesResponse,
  StylePreviewResponse,
} from '@/types/design-style';
import { generateTraceId } from '@/lib/api/trace-id';

/**
 * Mock模式标志
 * TODO Phase 7-9: 切换为false，启用真实后端API
 */
const USE_MOCK_DATA = false;

/**
 * 生成7种风格的预览（主函数）
 *
 * @param request 风格生成请求
 * @returns 7种风格预览数据
 *
 * Phase 5-6: 使用Mock数据快速验证前端流程
 * Phase 7-9: 切换为真实SuperDesign API调用
 */
export async function generateStylePreviews(
  request: Generate7StylesRequest
): Promise<Generate7StylesResponse> {
  if (USE_MOCK_DATA) {
    return generateMockStylePreviews(request);
  } else {
    return generateRealStylePreviews(request);
  }
}

/**
 * Mock模式：生成7种风格的模拟预览数据
 * TODO Phase 7-9: 本函数仅用于前端开发验证，后端实现后弃用
 */
async function generateMockStylePreviews(
  request: Generate7StylesRequest
): Promise<Generate7StylesResponse> {
  console.log('[V2.0 Mock] 生成7种风格预览（Mock数据）:', request.userRequirement);

  // 模拟网络延迟（500ms）
  await new Promise((resolve) => setTimeout(resolve, 500));

  const startTime = Date.now();
  const allStyles = getAllStyles();

  // 为每种风格生成Mock预览数据
  const stylesPreviews: StylePreviewResponse[] = allStyles.map((style) => {
    const styleInfo = getStyleDisplayInfo(style);

    // 生成简化的Mock HTML
    const mockHtml = generateMockHTML(styleInfo.displayName, styleInfo.description, styleInfo.features);

    // 生成简化的Mock CSS
    const mockCss = generateMockCSS(styleInfo.colorClass);

    return {
      style,
      htmlContent: mockHtml,
      cssContent: mockCss,
      generationTime: Math.floor(Math.random() * 2000) + 500, // 500-2500ms
      aiGenerated: false, // Mock数据标记
      thumbnailUrl: undefined, // Phase 7-9实现
    };
  });

  const totalTime = Date.now() - startTime;

  return {
    success: true,
    styles: stylesPreviews,
    totalGenerationTime: totalTime,
    warnings: [
      '⚠️ Mock数据模式：当前使用简化预览数据',
      '✅ Phase 7-9后端实现后将生成真实SuperDesign预览',
    ],
  };
}

/**
 * 真实API模式：调用后端SuperDesign服务生成7种风格预览
 * TODO Phase 7-9: 实现此函数
 */
async function generateRealStylePreviews(
  request: Generate7StylesRequest
): Promise<Generate7StylesResponse> {
  try {
    // 注意：使用Next.js API route代理，路径保持 /api/v1/...
    // 如果需要直接调用后端，应使用 API_BASE_URL + '/v1/styles/generate-previews'
    const response = await fetch('/api/v1/styles/generate-previews', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'x-trace-id': generateTraceId(),
      },
      body: JSON.stringify(request),
    });

    if (!response.ok) {
      throw new Error(`API请求失败: ${response.status} ${response.statusText}`);
    }

    const data: Generate7StylesResponse = await response.json();
    return data;
  } catch (error) {
    console.error('[V2.0] 风格预览生成失败:', error);

    return {
      success: false,
      styles: [],
      totalGenerationTime: 0,
      error: error instanceof Error ? error.message : '未知错误',
      warnings: ['后端API调用失败，请检查网络连接或后端服务状态'],
    };
  }
}

/**
 * 生成Mock HTML内容
 * TODO Phase 7-9: 弃用，使用SuperDesign真实生成的HTML
 */
function generateMockHTML(title: string, description: string, features: string[]): string {
  return `
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>${title}风格预览</title>
    <link rel="stylesheet" href="styles.css">
</head>
<body>
    <div class="preview-container">
        <header class="header">
            <h1 class="title">${title}</h1>
            <p class="subtitle">${description}</p>
        </header>

        <main class="content">
            <section class="features">
                <h2>核心特征</h2>
                <ul class="features-list">
                    ${features.map((feature) => `<li>${feature}</li>`).join('\n                    ')}
                </ul>
            </section>

            <section class="cta">
                <button class="btn-primary">体验此风格</button>
                <button class="btn-secondary">了解更多</button>
            </section>
        </main>

        <footer class="footer">
            <p>此为风格预览 - Powered by SuperDesign</p>
        </footer>
    </div>
</body>
</html>
  `.trim();
}

/**
 * 生成Mock CSS内容
 * TODO Phase 7-9: 弃用，使用SuperDesign真实生成的CSS
 */
function generateMockCSS(colorClass: string): string {
  // 从Tailwind colorClass提取颜色（简化处理）
  const colors = extractColorsFromClass(colorClass);

  return `
/* ${colorClass}风格的基础样式 */
:root {
    --primary-color: ${colors.primary};
    --secondary-color: ${colors.secondary};
    --text-color: #1a1a1a;
    --bg-color: #ffffff;
}

body {
    margin: 0;
    padding: 0;
    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
    color: var(--text-color);
    background: var(--bg-color);
}

.preview-container {
    max-width: 1200px;
    margin: 0 auto;
    padding: 2rem;
}

.header {
    text-align: center;
    padding: 3rem 0;
    background: linear-gradient(135deg, var(--primary-color), var(--secondary-color));
    -webkit-background-clip: text;
    -webkit-text-fill-color: transparent;
    background-clip: text;
}

.title {
    font-size: 3rem;
    font-weight: 700;
    margin: 0 0 1rem 0;
}

.subtitle {
    font-size: 1.25rem;
    opacity: 0.8;
}

.btn-primary {
    background: linear-gradient(135deg, var(--primary-color), var(--secondary-color));
    color: white;
    padding: 1rem 2rem;
    border: none;
    border-radius: 0.5rem;
    font-size: 1.1rem;
    cursor: pointer;
    transition: transform 0.2s;
}

.btn-primary:hover {
    transform: translateY(-2px);
}
  `.trim();
}

/**
 * 从Tailwind颜色类名提取颜色值（简化处理）
 * TODO Phase 7-9: SuperDesign真实生成的CSS包含完整颜色方案
 */
function extractColorsFromClass(colorClass: string): {
  primary: string;
  secondary: string;
} {
  // 简化映射：从Tailwind类名到实际颜色值
  const colorMap: Record<string, { primary: string; secondary: string }> = {
    'from-blue-400 to-cyan-500': { primary: '#60a5fa', secondary: '#06b6d4' },
    'from-pink-500 to-orange-400': { primary: '#ec4899', secondary: '#fb923c' },
    'from-gray-600 to-gray-800': { primary: '#4b5563', secondary: '#1f2937' },
    'from-purple-600 to-blue-800': { primary: '#9333ea', secondary: '#1e40af' },
    'from-indigo-500 to-purple-700': { primary: '#6366f1', secondary: '#7c3aed' },
    'from-yellow-400 to-red-500': { primary: '#facc15', secondary: '#ef4444' },
    'from-green-400 to-teal-500': { primary: '#4ade80', secondary: '#14b8a6' },
  };

  return colorMap[colorClass] || { primary: '#60a5fa', secondary: '#06b6d4' };
}

/**
 * 获取单个风格的预览（用于后续单独预览某个风格）
 * Phase 7-9实现
 */
export async function getStylePreview(
  style: DesignStyle,
  userRequirement: string
): Promise<StylePreviewResponse | null> {
  const result = await generateStylePreviews({ userRequirement });

  if (!result.success) {
    return null;
  }

  return result.styles.find((s) => s.style === style) || null;
}
