/**
 * 设计风格类型定义
 * 支持7种不同的设计风格，每种风格有独特的视觉特征
 *
 * @author Ingenio Team
 * @version 2.0.0
 * @since 2025-11-14
 */

/**
 * 设计风格枚举
 * 定义SuperDesign支持的7种设计风格
 */
export enum DesignStyle {
  /** A: 现代极简 - 大留白、卡片式、简洁图标 */
  MODERN_MINIMAL = "modern_minimal",

  /** B: 活力时尚 - 渐变色彩、圆角设计、网格布局 */
  VIBRANT_FASHION = "vibrant_fashion",

  /** C: 经典专业 - 传统布局、信息密集、列表式 */
  CLASSIC_PROFESSIONAL = "classic_professional",

  /** D: 未来科技 - 深色主题、霓虹色彩、3D元素 */
  FUTURE_TECH = "future_tech",

  /** E: 沉浸式3D - 毛玻璃、深度阴影、视差滚动 */
  IMMERSIVE_3D = "immersive_3d",

  /** F: 游戏化设计 - 卡通风格、奖励反馈、成就系统 */
  GAMIFIED = "gamified",

  /** G: 自然流动 - 有机曲线、自然配色、流体动画 */
  NATURAL_FLOW = "natural_flow",
}

/**
 * 风格显示信息接口
 * 用于UI展示和用户选择
 */
export interface StyleDisplayInfo {
  /** 风格代码 */
  code: DesignStyle;

  /** 风格标识符（A-G） */
  identifier: string;

  /** 显示名称（中文） */
  displayName: string;

  /** 显示名称（英文） */
  displayNameEn: string;

  /** 详细描述 */
  description: string;

  /** 核心特征（3-5个关键词） */
  features: string[];

  /** 图标emoji */
  icon: string;

  /** 颜色类名（Tailwind渐变） */
  colorClass: string;

  /** 适用场景 */
  suitableFor: string[];

  /** 参考案例 */
  examples: string[];
}

/**
 * 风格CSS配置接口
 * 定义每种风格的CSS变量和样式规则
 */
export interface StyleCSSConfig {
  /** 风格代码 */
  style: DesignStyle;

  /** CSS变量定义 */
  cssVariables: {
    // 颜色系统
    primary: string;
    secondary: string;
    accent: string;
    background: string;
    surface: string;
    text: string;
    textSecondary: string;
    border: string;

    // 圆角系统
    radiusSmall: string;
    radiusMedium: string;
    radiusLarge: string;

    // 阴影系统
    shadowSmall: string;
    shadowMedium: string;
    shadowLarge: string;

    // 间距系统
    spacingUnit: string;

    // 字体系统
    fontFamily: string;
    fontSizeBase: string;
    lineHeight: string;
  };

  /** 额外的CSS规则 */
  customCSS?: string;

  /** 动画配置 */
  animations?: {
    duration: string;
    easing: string;
  };
}

/**
 * 风格预览响应接口
 * 后端返回的风格预览数据
 */
export interface StylePreviewResponse {
  /** 风格代码 */
  style: DesignStyle;

  /** 预览HTML内容 */
  htmlContent: string;

  /** 预览CSS内容 */
  cssContent: string;

  /** 生成时间（毫秒） */
  generationTime: number;

  /** 是否AI生成（false表示使用模板） */
  aiGenerated: boolean;

  /** 预览图URL（可选） */
  thumbnailUrl?: string;
}

/**
 * 7风格生成请求接口
 */
export interface Generate7StylesRequest {
  /** 用户需求描述 */
  userRequirement: string;

  /** 应用类型（可选） */
  appType?: string;

  /** 目标平台（可选） */
  targetPlatform?: string;

  /** 是否使用AI定制（默认false，仅使用模板） */
  useAICustomization?: boolean;
}

/**
 * 7风格生成响应接口
 */
export interface Generate7StylesResponse {
  /** 是否成功 */
  success: boolean;

  /** 7种风格的预览列表 */
  styles: StylePreviewResponse[];

  /** 总生成时间（毫秒） */
  totalGenerationTime: number;

  /** 错误信息（如果失败） */
  error?: string;

  /** 警告信息 */
  warnings?: string[];
}

/**
 * 用户选择的风格接口
 */
export interface SelectedStyle {
  /** 选择的风格 */
  style: DesignStyle;

  /** 选择时间 */
  selectedAt: Date;

  /** 用户是否请求AI定制 */
  requestAICustomization: boolean;

  /** 定制化需求（可选） */
  customizationNotes?: string;
}

/**
 * 7种设计风格的显示信息映射表
 * 用于前端UI展示和用户选择
 */
export const STYLE_DISPLAY_MAP: Record<DesignStyle, StyleDisplayInfo> = {
  [DesignStyle.MODERN_MINIMAL]: {
    code: DesignStyle.MODERN_MINIMAL,
    identifier: "A",
    displayName: "现代极简",
    displayNameEn: "Modern Minimal",
    description: "大留白、卡片式布局、简洁图标，适合注重用户体验的现代应用",
    features: ["大留白设计", "卡片式布局", "简洁图标", "清晰导航", "高可读性"],
    icon: "✨",
    colorClass: "from-blue-400 to-cyan-500",
    suitableFor: ["企业官网", "SaaS平台", "效率工具", "内容管理"],
    examples: ["Notion", "Linear", "Stripe"],
  },

  [DesignStyle.VIBRANT_FASHION]: {
    code: DesignStyle.VIBRANT_FASHION,
    identifier: "B",
    displayName: "活力时尚",
    displayNameEn: "Vibrant Fashion",
    description: "渐变色彩、圆角设计、网格布局，适合年轻化、时尚的消费类应用",
    features: ["渐变色彩", "圆角设计", "网格布局", "动态交互", "视觉冲击"],
    icon: "🎨",
    colorClass: "from-pink-500 to-orange-400",
    suitableFor: ["电商平台", "社交应用", "时尚品牌", "创意设计"],
    examples: ["Instagram", "Dribbble", "Behance"],
  },

  [DesignStyle.CLASSIC_PROFESSIONAL]: {
    code: DesignStyle.CLASSIC_PROFESSIONAL,
    identifier: "C",
    displayName: "经典专业",
    displayNameEn: "Classic Professional",
    description: "传统布局、信息密集、列表式设计，适合B2B、金融、政务类应用",
    features: ["传统布局", "信息密集", "列表式", "表格展示", "数据导向"],
    icon: "📊",
    colorClass: "from-gray-600 to-gray-800",
    suitableFor: ["企业管理", "金融平台", "政务系统", "数据分析"],
    examples: ["Bloomberg", "SAP", "Oracle"],
  },

  [DesignStyle.FUTURE_TECH]: {
    code: DesignStyle.FUTURE_TECH,
    identifier: "D",
    displayName: "未来科技",
    displayNameEn: "Future Tech",
    description: "深色主题、霓虹色彩、3D元素，适合科技、游戏、创新类应用",
    features: ["深色主题", "霓虹色彩", "3D元素", "动态光效", "科技感"],
    icon: "🚀",
    colorClass: "from-purple-600 to-blue-800",
    suitableFor: ["游戏平台", "AI产品", "区块链", "科技展示"],
    examples: ["Cyberpunk", "GitHub Dark", "Discord"],
  },

  [DesignStyle.IMMERSIVE_3D]: {
    code: DesignStyle.IMMERSIVE_3D,
    identifier: "E",
    displayName: "沉浸式3D",
    displayNameEn: "Immersive 3D",
    description: "毛玻璃效果、深度阴影、视差滚动，适合品牌展示、营销类应用",
    features: ["毛玻璃效果", "深度阴影", "视差滚动", "3D交互", "沉浸体验"],
    icon: "🌌",
    colorClass: "from-indigo-500 to-purple-700",
    suitableFor: ["品牌官网", "营销活动", "产品展示", "创意作品"],
    examples: ["Apple", "Tesla", "Awwwards"],
  },

  [DesignStyle.GAMIFIED]: {
    code: DesignStyle.GAMIFIED,
    identifier: "F",
    displayName: "游戏化设计",
    displayNameEn: "Gamified",
    description: "卡通风格、奖励反馈、成就系统，适合教育、社交、健康类应用",
    features: ["卡通风格", "奖励反馈", "成就系统", "趣味交互", "用户激励"],
    icon: "🎮",
    colorClass: "from-yellow-400 to-red-500",
    suitableFor: ["教育平台", "健康应用", "社交游戏", "儿童产品"],
    examples: ["Duolingo", "Habitica", "Khan Academy"],
  },

  [DesignStyle.NATURAL_FLOW]: {
    code: DesignStyle.NATURAL_FLOW,
    identifier: "G",
    displayName: "自然流动",
    displayNameEn: "Natural Flow",
    description: "有机曲线、自然配色、流体动画，适合生活、健康、环保类应用",
    features: ["有机曲线", "自然配色", "流体动画", "舒缓节奏", "和谐美感"],
    icon: "🌿",
    colorClass: "from-green-400 to-teal-500",
    suitableFor: ["健康应用", "环保产品", "生活方式", "冥想工具"],
    examples: ["Calm", "Headspace", "Notion (柔和版)"],
  },
};

/**
 * 获取风格显示信息
 */
export function getStyleDisplayInfo(style: DesignStyle): StyleDisplayInfo {
  return STYLE_DISPLAY_MAP[style];
}

/**
 * 获取所有风格列表（按A-G顺序）
 */
export function getAllStyles(): DesignStyle[] {
  return [
    DesignStyle.MODERN_MINIMAL,
    DesignStyle.VIBRANT_FASHION,
    DesignStyle.CLASSIC_PROFESSIONAL,
    DesignStyle.FUTURE_TECH,
    DesignStyle.IMMERSIVE_3D,
    DesignStyle.GAMIFIED,
    DesignStyle.NATURAL_FLOW,
  ];
}
