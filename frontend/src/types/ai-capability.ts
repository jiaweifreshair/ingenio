/**
 * AI能力选择器类型定义
 * 定义19种AI能力类型及相关接口
 *
 * @author Ingenio Team
 * @since 1.0.0
 */

/**
 * AI能力类型枚举（19种）
 */
export enum AICapabilityType {
  // ===== 基础11种 =====
  /** 对话机器人 */
  CHATBOT = 'chatbot',
  /** 问答系统 */
  QA_SYSTEM = 'qa_system',
  /** 检索增强生成 */
  RAG = 'rag',
  /** 图像识别 */
  IMAGE_RECOGNITION = 'image_recognition',
  /** 语音识别 */
  SPEECH_RECOGNITION = 'speech_recognition',
  /** 文本生成 */
  TEXT_GENERATION = 'text_generation',
  /** 情感分析 */
  SENTIMENT_ANALYSIS = 'sentiment_analysis',
  /** 推荐系统 */
  RECOMMENDATION = 'recommendation',
  /** 内容审核 */
  CONTENT_MODERATION = 'content_moderation',
  /** 智能翻译 */
  TRANSLATION = 'translation',
  /** 代码生成 */
  CODE_GENERATION = 'code_generation',

  // ===== 新增8种 =====
  /** 视频分析 */
  VIDEO_ANALYSIS = 'video_analysis',
  /** 知识图谱 */
  KNOWLEDGE_GRAPH = 'knowledge_graph',
  /** OCR文档识别 */
  OCR_DOCUMENT = 'ocr_document',
  /** 实时流处理 */
  REALTIME_STREAM = 'realtime_stream',
  /** 超个性化推荐 */
  HYPER_PERSONALIZATION = 'hyper_personalization',
  /** 预测分析 */
  PREDICTIVE_ANALYTICS = 'predictive_analytics',
  /** 智能搜索 */
  SMART_SEARCH = 'smart_search',
  /** 多模态融合 */
  MULTIMODAL_FUSION = 'multimodal_fusion',
}

/**
 * 复杂度级别
 */
export enum ComplexityLevel {
  /** 简单 - 1-2天实现 */
  SIMPLE = 'SIMPLE',
  /** 中等 - 3-5天实现 */
  MEDIUM = 'MEDIUM',
  /** 复杂 - 5-10天实现 */
  COMPLEX = 'COMPLEX',
}

/**
 * AI能力类别
 */
export enum AICapabilityCategory {
  /** 对话交互 */
  CONVERSATION = 'CONVERSATION',
  /** 视觉识别 */
  VISION = 'VISION',
  /** 文档处理 */
  DOCUMENT = 'DOCUMENT',
  /** 数据分析 */
  ANALYTICS = 'ANALYTICS',
  /** 内容生成 */
  GENERATION = 'GENERATION',
  /** 音频处理 */
  AUDIO = 'AUDIO',
  /** 实时流 */
  REALTIME = 'REALTIME',
}

/**
 * AI能力详情接口
 */
export interface AICapability {
  /** 标识符 */
  type: AICapabilityType;
  /** 中文名称 */
  name: string;
  /** 英文名称 */
  nameEn: string;
  /** 简短描述（一句话） */
  description: string;
  /** 详细描述（用于详情弹窗） */
  detailedDescription?: string;
  /** 图标名称（lucide-react） */
  icon: string;
  /** 复杂度级别 */
  complexity: ComplexityLevel;
  /** 预估月成本（美元） */
  estimatedCost: number;
  /** 类别 */
  category: AICapabilityCategory;
  /** 使用场景列表 */
  useCases: string[];
  /** 技术栈列表 */
  techStack: string[];
  /** 预估开发天数 */
  estimatedDays: number;
  /** 是否为新功能 */
  isNew?: boolean;
  /** 是否为热门功能 */
  isPopular?: boolean;
}

/**
 * AI能力选择器Props
 */
export interface AICapabilityPickerProps {
  /** 已选择的AI能力类型列表 */
  selectedCapabilities: AICapabilityType[];
  /** 选择变化回调 */
  onSelectionChange: (capabilities: AICapabilityType[]) => void;
  /** 用户需求文本（用于智能推荐） */
  userRequirement?: string;
  /** 最大可选数量 */
  maxSelection?: number;
  /** 是否禁用 */
  disabled?: boolean;
  /** 是否显示成本估算 */
  showCostEstimate?: boolean;
  /** 是否显示推荐标识 */
  showRecommendations?: boolean;
  /** 类名 */
  className?: string;
}

/**
 * AI能力卡片Props
 */
export interface AICapabilityCardProps {
  /** AI能力详情 */
  capability: AICapability;
  /** 是否已选中 */
  isSelected: boolean;
  /** 是否为推荐项 */
  isRecommended: boolean;
  /** 点击切换回调 */
  onToggle: () => void;
  /** 是否禁用 */
  disabled?: boolean;
  /** 点击详情回调 */
  onShowDetail?: (capability: AICapability) => void;
  /** 类名 */
  className?: string;
}

/**
 * AI能力详情弹窗Props
 */
export interface AICapabilityDetailProps {
  /** AI能力详情 */
  capability: AICapability | null;
  /** 是否打开 */
  open: boolean;
  /** 关闭回调 */
  onClose: () => void;
  /** 是否已选中 */
  isSelected: boolean;
  /** 选择/取消选择回调 */
  onToggleSelection: () => void;
}

/**
 * AI能力摘要Props
 */
export interface AICapabilitySummaryProps {
  /** 已选择的AI能力列表 */
  selectedCapabilities: AICapability[];
  /** 移除AI能力回调 */
  onRemoveCapability: (type: AICapabilityType) => void;
  /** 清空所有选择回调 */
  onClearAll: () => void;
  /** 确认选择回调 */
  onConfirm: () => void;
  /** 是否显示确认按钮 */
  showConfirmButton?: boolean;
  /** 类名 */
  className?: string;
}

/**
 * 智能推荐关键词映射
 */
export interface RecommendationKeywordMap {
  /** 关键词 */
  keywords: string[];
  /** 推荐的AI能力类型 */
  capabilityType: AICapabilityType;
  /** 推荐权重（0-1） */
  weight: number;
}

/**
 * 筛选选项
 */
export interface FilterOptions {
  /** 选中的类别（ALL表示全部） */
  selectedCategory: AICapabilityCategory | 'ALL';
  /** 搜索关键词 */
  searchQuery: string;
  /** 复杂度筛选 */
  complexityFilter?: ComplexityLevel[];
  /** 成本范围筛选 */
  costRangeFilter?: {
    min: number;
    max: number;
  };
  /** 仅显示推荐项 */
  showRecommendedOnly?: boolean;
}

/**
 * AI能力统计信息
 */
export interface AICapabilityStats {
  /** 总数量 */
  total: number;
  /** 已选数量 */
  selected: number;
  /** 推荐数量 */
  recommended: number;
  /** 总预估成本 */
  totalCost: number;
  /** 总预估开发天数 */
  totalDays: number;
  /** 平均复杂度 */
  avgComplexity: number;
}
