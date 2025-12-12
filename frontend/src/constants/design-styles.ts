/**
 * 设计风格常量配置
 * 定义7种设计风格的详细信息和CSS配置
 *
 * @author Ingenio Team
 * @version 2.0.0
 * @since 2025-11-14
 */

import {
  DesignStyle,
  StyleDisplayInfo,
  StyleCSSConfig,
} from "@/types/design-style";

/**
 * 7种设计风格的显示信息映射表
 * 用于UI展示和用户选择
 */
export const STYLE_DISPLAY_MAP: Record<DesignStyle, StyleDisplayInfo> = {
  [DesignStyle.MODERN_MINIMAL]: {
    code: DesignStyle.MODERN_MINIMAL,
    identifier: "A",
    displayName: "现代极简",
    displayNameEn: "Modern Minimal",
    description: "大留白、卡片式布局、简洁图标，强调内容本身",
    features: ["大留白", "卡片式", "简洁图标", "单色系", "扁平化"],
    icon: "✨",
    colorClass: "from-gray-500 to-slate-600",
    suitableFor: ["企业官网", "作品集", "博客", "文档站点"],
    examples: ["Apple", "Notion", "Linear"],
  },

  [DesignStyle.VIBRANT_FASHION]: {
    code: DesignStyle.VIBRANT_FASHION,
    identifier: "B",
    displayName: "活力时尚",
    displayNameEn: "Vibrant Fashion",
    description: "渐变色彩、圆角设计、网格布局，充满活力和现代感",
    features: ["渐变色彩", "大圆角", "网格布局", "多彩", "动感"],
    icon: "🌈",
    colorClass: "from-purple-500 to-pink-500",
    suitableFor: ["创意工作室", "设计公司", "活动页面", "品牌站点"],
    examples: ["Stripe", "Vercel", "Figma"],
  },

  [DesignStyle.CLASSIC_PROFESSIONAL]: {
    code: DesignStyle.CLASSIC_PROFESSIONAL,
    identifier: "C",
    displayName: "经典专业",
    displayNameEn: "Classic Professional",
    description: "传统布局、信息密集、列表式展示，强调专业性和信息量",
    features: ["传统布局", "信息密集", "列表式", "蓝灰色系", "线条分割"],
    icon: "📊",
    colorClass: "from-blue-600 to-indigo-700",
    suitableFor: ["企业系统", "B2B平台", "专业服务", "数据平台"],
    examples: ["LinkedIn", "Bloomberg", "企业ERP"],
  },

  [DesignStyle.FUTURE_TECH]: {
    code: DesignStyle.FUTURE_TECH,
    identifier: "D",
    displayName: "未来科技",
    displayNameEn: "Future Tech",
    description: "深色主题、霓虹色彩、3D元素，展现科技感和未来感",
    features: ["深色主题", "霓虹色彩", "3D元素", "辉光效果", "科技感"],
    icon: "🚀",
    colorClass: "from-cyan-500 to-blue-600",
    suitableFor: ["科技公司", "游戏平台", "区块链", "AI产品"],
    examples: ["Cyberpunk", "GitLab Dark", "Steam"],
  },

  [DesignStyle.IMMERSIVE_3D]: {
    code: DesignStyle.IMMERSIVE_3D,
    identifier: "E",
    displayName: "沉浸式3D",
    displayNameEn: "Immersive 3D",
    description: "毛玻璃效果、深度阴影、视差滚动，打造沉浸式体验",
    features: ["毛玻璃", "深度阴影", "视差滚动", "层次感", "沉浸式"],
    icon: "🎭",
    colorClass: "from-indigo-500 to-purple-600",
    suitableFor: ["高端品牌", "艺术展示", "豪华产品", "体验式网站"],
    examples: ["iOS Design", "Windows 11", "Airbnb Premium"],
  },

  [DesignStyle.GAMIFIED]: {
    code: DesignStyle.GAMIFIED,
    identifier: "F",
    displayName: "游戏化设计",
    displayNameEn: "Gamified",
    description: "卡通风格、奖励反馈、成就系统，趣味性和互动性强",
    features: ["卡通风格", "奖励反馈", "成就徽章", "明亮色彩", "趣味性"],
    icon: "🎮",
    colorClass: "from-orange-500 to-red-500",
    suitableFor: ["教育应用", "儿童产品", "游戏平台", "社交应用"],
    examples: ["Duolingo", "Kahoot", "Discord"],
  },

  [DesignStyle.NATURAL_FLOW]: {
    code: DesignStyle.NATURAL_FLOW,
    identifier: "G",
    displayName: "自然流动",
    displayNameEn: "Natural Flow",
    description: "有机曲线、自然配色、流体动画，展现自然和谐之美",
    features: ["有机曲线", "自然配色", "流体动画", "柔和", "和谐"],
    icon: "🍃",
    colorClass: "from-green-500 to-teal-600",
    suitableFor: ["健康应用", "冥想平台", "环保产品", "生活方式"],
    examples: ["Calm", "Headspace", "植物图鉴"],
  },
};

/**
 * 7种设计风格的CSS配置映射表
 * 定义每种风格的CSS变量和样式规则
 */
export const STYLE_CSS_CONFIG_MAP: Record<DesignStyle, StyleCSSConfig> = {
  [DesignStyle.MODERN_MINIMAL]: {
    style: DesignStyle.MODERN_MINIMAL,
    cssVariables: {
      primary: "#1a1a1a",
      secondary: "#6b7280",
      accent: "#3b82f6",
      background: "#ffffff",
      surface: "#f9fafb",
      text: "#111827",
      textSecondary: "#6b7280",
      border: "#e5e7eb",
      radiusSmall: "8px",
      radiusMedium: "12px",
      radiusLarge: "16px",
      shadowSmall: "0 1px 2px 0 rgba(0,0,0,0.05)",
      shadowMedium: "0 4px 6px -1px rgba(0,0,0,0.1)",
      shadowLarge: "0 20px 25px -5px rgba(0,0,0,0.1)",
      spacingUnit: "8px",
      fontFamily: "-apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif",
      fontSizeBase: "16px",
      lineHeight: "1.6",
    },
    animations: {
      duration: "0.2s",
      easing: "ease-out",
    },
  },

  [DesignStyle.VIBRANT_FASHION]: {
    style: DesignStyle.VIBRANT_FASHION,
    cssVariables: {
      primary: "#8b5cf6",
      secondary: "#ec4899",
      accent: "#f59e0b",
      background: "#ffffff",
      surface: "linear-gradient(135deg, #667eea 0%, #764ba2 100%)",
      text: "#1f2937",
      textSecondary: "#6b7280",
      border: "#e5e7eb",
      radiusSmall: "16px",
      radiusMedium: "24px",
      radiusLarge: "32px",
      shadowSmall: "0 4px 6px rgba(139,92,246,0.1)",
      shadowMedium: "0 10px 15px rgba(139,92,246,0.2)",
      shadowLarge: "0 25px 50px rgba(139,92,246,0.3)",
      spacingUnit: "12px",
      fontFamily: "'Inter', -apple-system, sans-serif",
      fontSizeBase: "16px",
      lineHeight: "1.7",
    },
    animations: {
      duration: "0.3s",
      easing: "cubic-bezier(0.4, 0, 0.2, 1)",
    },
  },

  [DesignStyle.CLASSIC_PROFESSIONAL]: {
    style: DesignStyle.CLASSIC_PROFESSIONAL,
    cssVariables: {
      primary: "#1e40af",
      secondary: "#475569",
      accent: "#0284c7",
      background: "#f8fafc",
      surface: "#ffffff",
      text: "#0f172a",
      textSecondary: "#64748b",
      border: "#cbd5e1",
      radiusSmall: "4px",
      radiusMedium: "6px",
      radiusLarge: "8px",
      shadowSmall: "0 1px 3px rgba(0,0,0,0.1)",
      shadowMedium: "0 2px 4px rgba(0,0,0,0.1)",
      shadowLarge: "0 4px 6px rgba(0,0,0,0.1)",
      spacingUnit: "8px",
      fontFamily: "'Roboto', 'Arial', sans-serif",
      fontSizeBase: "14px",
      lineHeight: "1.5",
    },
    animations: {
      duration: "0.15s",
      easing: "ease-in-out",
    },
  },

  [DesignStyle.FUTURE_TECH]: {
    style: DesignStyle.FUTURE_TECH,
    cssVariables: {
      primary: "#06b6d4",
      secondary: "#8b5cf6",
      accent: "#f59e0b",
      background: "#0a0a0a",
      surface: "#1a1a1a",
      text: "#f0f0f0",
      textSecondary: "#a0a0a0",
      border: "#333333",
      radiusSmall: "8px",
      radiusMedium: "12px",
      radiusLarge: "16px",
      shadowSmall: "0 0 10px rgba(6,182,212,0.3)",
      shadowMedium: "0 0 20px rgba(6,182,212,0.4)",
      shadowLarge: "0 0 40px rgba(6,182,212,0.5)",
      spacingUnit: "12px",
      fontFamily: "'JetBrains Mono', 'Courier New', monospace",
      fontSizeBase: "16px",
      lineHeight: "1.6",
    },
    customCSS: `
      * {
        text-shadow: 0 0 10px rgba(6,182,212,0.5);
      }
      .glow {
        box-shadow: 0 0 20px rgba(6,182,212,0.6),
                    0 0 40px rgba(139,92,246,0.4);
      }
    `,
    animations: {
      duration: "0.4s",
      easing: "cubic-bezier(0.68, -0.55, 0.265, 1.55)",
    },
  },

  [DesignStyle.IMMERSIVE_3D]: {
    style: DesignStyle.IMMERSIVE_3D,
    cssVariables: {
      primary: "#6366f1",
      secondary: "#8b5cf6",
      accent: "#ec4899",
      background: "linear-gradient(135deg, #667eea 0%, #764ba2 100%)",
      surface: "rgba(255, 255, 255, 0.1)",
      text: "#ffffff",
      textSecondary: "rgba(255, 255, 255, 0.7)",
      border: "rgba(255, 255, 255, 0.2)",
      radiusSmall: "16px",
      radiusMedium: "24px",
      radiusLarge: "32px",
      shadowSmall: "0 8px 32px rgba(0,0,0,0.1)",
      shadowMedium: "0 16px 64px rgba(0,0,0,0.2)",
      shadowLarge: "0 32px 128px rgba(0,0,0,0.3)",
      spacingUnit: "16px",
      fontFamily: "'SF Pro Display', -apple-system, sans-serif",
      fontSizeBase: "17px",
      lineHeight: "1.7",
    },
    customCSS: `
      .glass {
        background: rgba(255, 255, 255, 0.1);
        backdrop-filter: blur(10px);
        border: 1px solid rgba(255, 255, 255, 0.2);
      }
    `,
    animations: {
      duration: "0.5s",
      easing: "cubic-bezier(0.22, 1, 0.36, 1)",
    },
  },

  [DesignStyle.GAMIFIED]: {
    style: DesignStyle.GAMIFIED,
    cssVariables: {
      primary: "#f59e0b",
      secondary: "#ef4444",
      accent: "#10b981",
      background: "#fef3c7",
      surface: "#ffffff",
      text: "#78350f",
      textSecondary: "#92400e",
      border: "#fbbf24",
      radiusSmall: "12px",
      radiusMedium: "20px",
      radiusLarge: "28px",
      shadowSmall: "0 4px 0 #d97706",
      shadowMedium: "0 6px 0 #d97706",
      shadowLarge: "0 8px 0 #d97706",
      spacingUnit: "12px",
      fontFamily: "'Comic Sans MS', 'Comic Neue', cursive",
      fontSizeBase: "18px",
      lineHeight: "1.8",
    },
    customCSS: `
      .button {
        border: 3px solid #78350f;
        transform: translateY(0);
        transition: all 0.1s;
      }
      .button:active {
        transform: translateY(4px);
        box-shadow: none;
      }
    `,
    animations: {
      duration: "0.1s",
      easing: "ease-in-out",
    },
  },

  [DesignStyle.NATURAL_FLOW]: {
    style: DesignStyle.NATURAL_FLOW,
    cssVariables: {
      primary: "#10b981",
      secondary: "#14b8a6",
      accent: "#f59e0b",
      background: "#f0fdf4",
      surface: "#ffffff",
      text: "#064e3b",
      textSecondary: "#059669",
      border: "#d1fae5",
      radiusSmall: "20px",
      radiusMedium: "32px",
      radiusLarge: "48px",
      shadowSmall: "0 4px 16px rgba(16,185,129,0.1)",
      shadowMedium: "0 8px 32px rgba(16,185,129,0.15)",
      shadowLarge: "0 16px 64px rgba(16,185,129,0.2)",
      spacingUnit: "16px",
      fontFamily: "'Georgia', serif",
      fontSizeBase: "17px",
      lineHeight: "1.8",
    },
    customCSS: `
      * {
        border-radius: 24px;
      }
      .organic {
        clip-path: ellipse(80% 100% at 50% 0%);
      }
    `,
    animations: {
      duration: "0.6s",
      easing: "cubic-bezier(0.25, 0.46, 0.45, 0.94)",
    },
  },
};

/**
 * 获取所有风格的列表（按字母顺序）
 */
export const getAllStyles = (): StyleDisplayInfo[] => {
  return Object.values(STYLE_DISPLAY_MAP).sort((a, b) =>
    a.identifier.localeCompare(b.identifier)
  );
};

/**
 * 根据风格代码获取显示信息
 */
export const getStyleDisplayInfo = (
  style: DesignStyle
): StyleDisplayInfo | undefined => {
  return STYLE_DISPLAY_MAP[style];
};

/**
 * 根据风格代码获取CSS配置
 */
export const getStyleCSSConfig = (
  style: DesignStyle
): StyleCSSConfig | undefined => {
  return STYLE_CSS_CONFIG_MAP[style];
};

/**
 * 生成风格的CSS字符串
 */
export const generateStyleCSS = (style: DesignStyle): string => {
  const config = STYLE_CSS_CONFIG_MAP[style];
  if (!config) return "";

  const { cssVariables, customCSS, animations } = config;

  let css = `:root {\n`;

  // CSS变量
  Object.entries(cssVariables).forEach(([key, value]) => {
    const cssVarName = key.replace(/([A-Z])/g, "-$1").toLowerCase();
    css += `  --${cssVarName}: ${value};\n`;
  });

  // 动画变量
  if (animations) {
    css += `  --animation-duration: ${animations.duration};\n`;
    css += `  --animation-easing: ${animations.easing};\n`;
  }

  css += `}\n\n`;

  // 自定义CSS
  if (customCSS) {
    css += customCSS + "\n";
  }

  return css;
};
