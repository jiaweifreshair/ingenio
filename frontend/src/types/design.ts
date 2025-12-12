/**
 * SuperDesign设计相关类型定义
 */

/**
 * 设计风格枚举
 */
export enum DesignStyle {
  /** 方案A：现代极简 */
  MODERN_MINIMAL = 'modern_minimal',
  /** 方案B：活力时尚 */
  VIBRANT_TRENDY = 'vibrant_trendy',
  /** 方案C：经典专业 */
  CLASSIC_PROFESSIONAL = 'classic_professional'
}

/**
 * 色彩主题
 */
export interface ColorTheme {
  /** 主色调 */
  primaryColor: string;
  /** 次要色 */
  secondaryColor: string;
  /** 背景色 */
  backgroundColor: string;
  /** 文字色 */
  textColor: string;
  /** 强调色 */
  accentColor: string;
  /** 是否为深色模式 */
  darkMode: boolean;
}

/**
 * 字体方案
 */
export interface FontScheme {
  /** 标题字体 */
  heading: string;
  /** 正文字体 */
  body: string;
  /** 字体大小 */
  size: {
    small: string;
    medium: string;
    large: string;
  };
}

/**
 * UI设计方案（与后端DesignVariant对应）
 */
export interface DesignScheme {
  /** 方案标识 (A/B/C) */
  variantId: string;
  /** 设计风格描述 */
  style: string;
  /** 风格关键词 */
  styleKeywords: string[];
  /** 生成的Compose UI代码 */
  code: string;
  /** 代码文件路径 */
  codePath: string;
  /** 预览图URL或Base64 */
  preview: string;
  /** 设计资源（图标、图片等） */
  assets: Record<string, string>;
  /** 色彩主题 */
  colorTheme: ColorTheme;
  /** 布局类型 */
  layoutType: string;
  /** 组件库 */
  componentLibrary: string;
  /** 设计特点列表 */
  features: string[];
  /** AI模型生成的完整响应 */
  rawResponse?: string;
  /** 生成耗时（毫秒） */
  generationTimeMs?: number;
}

/**
 * 实体信息
 */
export interface EntityInfo {
  /** 实体名称 */
  name: string;
  /** 显示名称 */
  displayName: string;
  /** 主要字段列表 */
  primaryFields: string[];
  /** 实体类型 */
  viewType: 'list' | 'detail' | 'form' | 'dashboard';
}

/**
 * 设计请求
 */
export interface DesignRequest {
  /** 任务ID */
  taskId?: string;
  /** 用户原始需求描述 */
  userPrompt: string;
  /** 实体列表 */
  entities: EntityInfo[];
  /** 目标平台 */
  targetPlatform: 'android' | 'ios' | 'web';
  /** UI框架 */
  uiFramework: 'compose_multiplatform' | 'react_native' | 'flutter';
  /** 色彩方案偏好 */
  colorScheme: 'light' | 'dark' | 'auto';
  /** 是否包含资源文件 */
  includeAssets: boolean;
  /** 额外的设计约束 */
  constraints?: Record<string, unknown>;
}

/**
 * 设计生成响应
 */
export interface DesignGenerateResponse {
  /** 生成的3个设计方案 */
  designs: DesignScheme[];
  /** 生成耗时 */
  totalTimeMs?: number;
}

/**
 * 复杂度级别
 */
export enum ComplexityLevel {
  SIMPLE = 'simple',
  MEDIUM = 'medium',
  COMPLEX = 'complex'
}

/**
 * 方案对比项
 */
export interface ComparisonItem {
  /** 方案ID */
  variantId: string;
  /** 对比维度名称 */
  dimension: string;
  /** 分数或评级 */
  score: number;
  /** 描述 */
  description: string;
}
