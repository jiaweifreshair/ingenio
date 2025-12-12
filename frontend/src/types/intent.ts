/**
 * 意图识别相关类型定义
 * 对应后端的Intent相关DTO
 *
 * @author Ingenio Team
 * @version 2.0.0
 * @since 2025-11-14
 */

/**
 * 需求意图枚举
 * 对应后端 RequirementIntent.java
 */
export enum RequirementIntent {
  /** 克隆已有网站 */
  CLONE_EXISTING_WEBSITE = "CLONE_EXISTING_WEBSITE",
  /** 从零开始设计 */
  DESIGN_FROM_SCRATCH = "DESIGN_FROM_SCRATCH",
  /** 混合模式（克隆+定制） */
  HYBRID_CLONE_AND_CUSTOMIZE = "HYBRID_CLONE_AND_CUSTOMIZE",
}

/**
 * 意图显示信息
 */
export interface IntentDisplayInfo {
  /** 意图代码 */
  code: RequirementIntent;
  /** 显示名称 */
  displayName: string;
  /** 描述信息 */
  description: string;
  /** 图标emoji */
  icon: string;
  /** 颜色类名（Tailwind） */
  colorClass: string;
}

/**
 * 意图显示信息映射表
 */
export const INTENT_DISPLAY_MAP: Record<RequirementIntent, IntentDisplayInfo> = {
  [RequirementIntent.CLONE_EXISTING_WEBSITE]: {
    code: RequirementIntent.CLONE_EXISTING_WEBSITE,
    displayName: "克隆已有网站",
    description: "直接爬取参考网站，快速生成原型",
    icon: "🔄",
    colorClass: "from-blue-500 to-cyan-500",
  },
  [RequirementIntent.DESIGN_FROM_SCRATCH]: {
    code: RequirementIntent.DESIGN_FROM_SCRATCH,
    displayName: "从零开始设计",
    description: "AI生成7种设计风格，用户选择最满意的方案",
    icon: "✨",
    colorClass: "from-purple-500 to-pink-500",
  },
  [RequirementIntent.HYBRID_CLONE_AND_CUSTOMIZE]: {
    code: RequirementIntent.HYBRID_CLONE_AND_CUSTOMIZE,
    displayName: "混合模式",
    description: "爬取参考网站后，根据需求定制化修改",
    icon: "🎨",
    colorClass: "from-orange-500 to-amber-500",
  },
};

/**
 * 意图识别结果接口
 * 对应后端 IntentClassificationResult.java
 */
export interface IntentClassificationResult {
  /** 识别出的意图类型 */
  intent: RequirementIntent;

  /** 置信度分数（0.0 - 1.0） */
  confidence: number;

  /** AI推理过程说明 */
  reasoning: string;

  /** 提取的参考网站URL列表 */
  referenceUrls: string[];

  /** 提取的关键词列表 */
  extractedKeywords: string[];

  /** 定制化需求描述（仅混合模式） */
  customizationRequirement?: string;

  /** 建议的下一步操作 */
  suggestedNextAction: string;

  /** 警告信息列表 */
  warnings: string[];

  /** AI模型使用的提示词（调试用） */
  promptUsed?: string;

  /** AI原始响应（调试用） */
  rawResponse?: string;
}

/**
 * 置信度等级
 */
export enum ConfidenceLevel {
  /** 非常确定（90-100%） */
  VERY_HIGH = "very_high",
  /** 比较确定（70-90%） */
  HIGH = "high",
  /** 中等确定（50-70%） */
  MEDIUM = "medium",
  /** 不确定（<50%） */
  LOW = "low",
}

/**
 * 获取置信度等级
 */
export function getConfidenceLevel(confidence: number): ConfidenceLevel {
  if (confidence >= 0.9) return ConfidenceLevel.VERY_HIGH;
  if (confidence >= 0.7) return ConfidenceLevel.HIGH;
  if (confidence >= 0.5) return ConfidenceLevel.MEDIUM;
  return ConfidenceLevel.LOW;
}

/**
 * 获取置信度等级的颜色类名
 */
export function getConfidenceLevelColor(level: ConfidenceLevel): string {
  switch (level) {
    case ConfidenceLevel.VERY_HIGH:
      return "text-green-600 dark:text-green-400";
    case ConfidenceLevel.HIGH:
      return "text-blue-600 dark:text-blue-400";
    case ConfidenceLevel.MEDIUM:
      return "text-orange-600 dark:text-orange-400";
    case ConfidenceLevel.LOW:
      return "text-red-600 dark:text-red-400";
  }
}

/**
 * 获取置信度等级的背景颜色类名
 */
export function getConfidenceLevelBgColor(level: ConfidenceLevel): string {
  switch (level) {
    case ConfidenceLevel.VERY_HIGH:
      return "bg-green-50 dark:bg-green-900/10 border-green-200 dark:border-green-800";
    case ConfidenceLevel.HIGH:
      return "bg-blue-50 dark:bg-blue-900/10 border-blue-200 dark:border-blue-800";
    case ConfidenceLevel.MEDIUM:
      return "bg-orange-50 dark:bg-orange-900/10 border-orange-200 dark:border-orange-800";
    case ConfidenceLevel.LOW:
      return "bg-red-50 dark:bg-red-900/10 border-red-200 dark:border-red-800";
  }
}

/**
 * 判断意图识别是否成功
 */
export function isIntentSuccessful(result: IntentClassificationResult): boolean {
  return result.intent !== null && result.confidence >= 0.5;
}

/**
 * 判断置信度是否足够高（默认阈值0.7）
 */
export function isHighConfidence(confidence: number, threshold = 0.7): boolean {
  return confidence >= threshold;
}
