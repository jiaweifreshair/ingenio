/**
 * AI能力数据（19种完整数据）
 * 包含每种AI能力的详细信息、成本估算、技术栈等
 *
 * @author Ingenio Team
 * @since 1.0.0
 */

import {
  AICapability,
  AICapabilityType,
  ComplexityLevel,
  AICapabilityCategory,
} from '@/types/ai-capability';

/**
 * 19种AI能力完整数据
 */
export const AI_CAPABILITIES: AICapability[] = [
  // ===== 对话交互类 (3种) =====
  {
    type: AICapabilityType.CHATBOT,
    name: '对话机器人',
    nameEn: 'Chatbot',
    description: '智能对话系统，支持多轮对话和上下文理解',
    detailedDescription: '基于大语言模型的智能对话系统，支持多轮对话、上下文记忆、意图识别和情感分析。适用于客服机器人、虚拟助手等场景。',
    icon: 'MessageSquare',
    complexity: ComplexityLevel.SIMPLE,
    estimatedCost: 1.7,
    category: AICapabilityCategory.CONVERSATION,
    useCases: ['客服机器人', 'AI助手', '虚拟顾问', '智能问答'],
    techStack: ['Qwen-Max', 'Ktor HTTP', 'WebSocket', 'Redis'],
    estimatedDays: 2,
    isPopular: true,
  },
  {
    type: AICapabilityType.QA_SYSTEM,
    name: '问答系统',
    nameEn: 'Q&A System',
    description: '精准问答系统，快速响应用户提问',
    detailedDescription: '基于知识库的智能问答系统，支持问题理解、答案检索、相似问题推荐。适用于FAQ、知识库查询等场景。',
    icon: 'HelpCircle',
    complexity: ComplexityLevel.SIMPLE,
    estimatedCost: 1.2,
    category: AICapabilityCategory.CONVERSATION,
    useCases: ['FAQ系统', '知识库问答', '技术支持', '产品咨询'],
    techStack: ['Qwen-Max', 'Embedding', 'Vector DB', 'Ktor'],
    estimatedDays: 2,
  },
  {
    type: AICapabilityType.RAG,
    name: '检索增强生成',
    nameEn: 'RAG (Retrieval Augmented Generation)',
    description: '结合知识库检索和AI生成的智能系统',
    detailedDescription: '检索增强生成（RAG）技术，结合向量数据库检索和大语言模型生成，提供准确且可追溯的答案。适用于企业知识库、文档问答等场景。',
    icon: 'Database',
    complexity: ComplexityLevel.MEDIUM,
    estimatedCost: 3.5,
    category: AICapabilityCategory.CONVERSATION,
    useCases: ['企业知识库', '文档问答', '智能搜索', '法律咨询'],
    techStack: ['Qwen-Max', 'Embedding API', 'Supabase pgvector', 'Ktor'],
    estimatedDays: 4,
    isNew: true,
  },

  // ===== 视觉识别类 (2种) =====
  {
    type: AICapabilityType.IMAGE_RECOGNITION,
    name: '图像识别',
    nameEn: 'Image Recognition',
    description: '识别图片中的物体、场景、人脸等',
    detailedDescription: '基于深度学习的图像识别系统，支持物体检测、场景识别、人脸识别、图像分类等功能。适用于智能相册、商品识别等场景。',
    icon: 'Image',
    complexity: ComplexityLevel.MEDIUM,
    estimatedCost: 15.0,
    category: AICapabilityCategory.VISION,
    useCases: ['智能相册', '商品识别', '质量检测', '人脸识别'],
    techStack: ['Qwen-VL', 'Image API', 'Ktor', 'MinIO'],
    estimatedDays: 3,
    isPopular: true,
  },
  {
    type: AICapabilityType.VIDEO_ANALYSIS,
    name: '视频分析',
    nameEn: 'Video Analysis',
    description: '自动分析视频内容和行为',
    detailedDescription: '智能视频分析系统，支持视频内容理解、行为识别、目标跟踪、异常检测等功能。适用于视频监控、内容审核等场景。',
    icon: 'Video',
    complexity: ComplexityLevel.MEDIUM,
    estimatedCost: 50.0,
    category: AICapabilityCategory.VISION,
    useCases: ['视频监控', '内容审核', '行为分析', '智能剪辑'],
    techStack: ['Qwen-VL', 'FFmpeg', 'Object Detection', 'Ktor'],
    estimatedDays: 5,
    isNew: true,
  },

  // ===== 文档处理类 (2种) =====
  {
    type: AICapabilityType.OCR_DOCUMENT,
    name: 'OCR文档识别',
    nameEn: 'OCR Document Recognition',
    description: '智能识别文档、票据、身份证等',
    detailedDescription: '光学字符识别（OCR）系统，支持文档识别、表格提取、票据解析、身份证识别等功能。适用于财务系统、文档管理等场景。',
    icon: 'FileText',
    complexity: ComplexityLevel.MEDIUM,
    estimatedCost: 12.0,
    category: AICapabilityCategory.DOCUMENT,
    useCases: ['发票识别', '合同解析', '身份证识别', '名片扫描'],
    techStack: ['Aliyun OCR', 'PDF Parser', 'Ktor', 'MinIO'],
    estimatedDays: 4,
    isNew: true,
  },
  {
    type: AICapabilityType.TRANSLATION,
    name: '智能翻译',
    nameEn: 'Translation',
    description: '高质量多语言翻译服务',
    detailedDescription: '基于神经网络的机器翻译系统，支持100+语言互译，保持语义准确和流畅。适用于跨境电商、多语言应用等场景。',
    icon: 'Languages',
    complexity: ComplexityLevel.SIMPLE,
    estimatedCost: 2.0,
    category: AICapabilityCategory.DOCUMENT,
    useCases: ['多语言应用', '跨境电商', '国际化文档', '实时翻译'],
    techStack: ['Qwen Translation', 'i18n', 'Ktor', 'Redis Cache'],
    estimatedDays: 2,
  },

  // ===== 数据分析类 (3种) =====
  {
    type: AICapabilityType.SENTIMENT_ANALYSIS,
    name: '情感分析',
    nameEn: 'Sentiment Analysis',
    description: '分析文本情感倾向和情绪',
    detailedDescription: '自然语言处理技术，分析文本的情感倾向（正面、负面、中性）和情绪强度。适用于舆情监测、用户反馈分析等场景。',
    icon: 'Heart',
    complexity: ComplexityLevel.SIMPLE,
    estimatedCost: 1.0,
    category: AICapabilityCategory.ANALYTICS,
    useCases: ['舆情监测', '用户反馈', '评论分析', '品牌监控'],
    techStack: ['Qwen-Max', 'NLP', 'Ktor', 'PostgreSQL'],
    estimatedDays: 2,
  },
  {
    type: AICapabilityType.PREDICTIVE_ANALYTICS,
    name: '预测分析',
    nameEn: 'Predictive Analytics',
    description: '基于历史数据预测未来趋势',
    detailedDescription: '机器学习预测系统，分析历史数据模式，预测未来趋势和风险。适用于销售预测、风险评估等场景。',
    icon: 'TrendingUp',
    complexity: ComplexityLevel.COMPLEX,
    estimatedCost: 35.0,
    category: AICapabilityCategory.ANALYTICS,
    useCases: ['销售预测', '风险评估', '需求预测', '趋势分析'],
    techStack: ['Python ML', 'Time Series', 'PostgreSQL', 'Jupyter'],
    estimatedDays: 7,
    isNew: true,
  },
  {
    type: AICapabilityType.KNOWLEDGE_GRAPH,
    name: '知识图谱',
    nameEn: 'Knowledge Graph',
    description: '构建知识网络，实现实体关系提取',
    detailedDescription: '知识图谱系统，自动提取实体、构建关系网络、推理知识。适用于企业知识管理、智能推荐等场景。',
    icon: 'Network',
    complexity: ComplexityLevel.COMPLEX,
    estimatedCost: 28.0,
    category: AICapabilityCategory.ANALYTICS,
    useCases: ['企业知识管理', '关系挖掘', '智能推荐', '风控系统'],
    techStack: ['Neo4j', 'NLP', 'Entity Extraction', 'Ktor'],
    estimatedDays: 8,
    isNew: true,
  },

  // ===== 内容生成类 (3种) =====
  {
    type: AICapabilityType.TEXT_GENERATION,
    name: '文本生成',
    nameEn: 'Text Generation',
    description: 'AI驱动的内容创作和文本生成',
    detailedDescription: '基于大语言模型的文本生成系统，支持文章撰写、摘要提取、文案创作等功能。适用于内容营销、自动化写作等场景。',
    icon: 'FileText',
    complexity: ComplexityLevel.SIMPLE,
    estimatedCost: 2.5,
    category: AICapabilityCategory.GENERATION,
    useCases: ['内容营销', '文案生成', '自动摘要', '邮件撰写'],
    techStack: ['Qwen-Max', 'Template Engine', 'Ktor', 'Redis'],
    estimatedDays: 2,
    isPopular: true,
  },
  {
    type: AICapabilityType.CODE_GENERATION,
    name: '代码生成',
    nameEn: 'Code Generation',
    description: 'AI辅助编程和代码自动生成',
    detailedDescription: '智能代码生成系统，支持代码补全、代码解释、Bug修复、代码重构等功能。适用于开发工具、代码助手等场景。',
    icon: 'Code',
    complexity: ComplexityLevel.MEDIUM,
    estimatedCost: 8.0,
    category: AICapabilityCategory.GENERATION,
    useCases: ['代码助手', 'IDE插件', '自动化测试', '代码审查'],
    techStack: ['Qwen-Coder', 'AST Parser', 'Ktor', 'Git'],
    estimatedDays: 4,
  },
  {
    type: AICapabilityType.MULTIMODAL_FUSION,
    name: '多模态融合',
    nameEn: 'Multimodal Fusion',
    description: '融合文本、图像、音频等多种模态',
    detailedDescription: '多模态AI系统，融合文本、图像、音频、视频等多种数据源，实现更智能的理解和生成。适用于内容创作、智能助手等场景。',
    icon: 'Layers',
    complexity: ComplexityLevel.COMPLEX,
    estimatedCost: 45.0,
    category: AICapabilityCategory.GENERATION,
    useCases: ['内容创作', '智能助手', '教育应用', '娱乐互动'],
    techStack: ['Qwen-VL', 'Qwen-Audio', 'Fusion Model', 'Ktor'],
    estimatedDays: 9,
    isNew: true,
  },

  // ===== 推荐系统类 (2种) =====
  {
    type: AICapabilityType.RECOMMENDATION,
    name: '推荐系统',
    nameEn: 'Recommendation System',
    description: '个性化内容推荐和商品推荐',
    detailedDescription: '协同过滤推荐系统，基于用户行为和偏好提供个性化推荐。适用于电商、内容平台等场景。',
    icon: 'ThumbsUp',
    complexity: ComplexityLevel.MEDIUM,
    estimatedCost: 5.0,
    category: AICapabilityCategory.ANALYTICS,
    useCases: ['电商推荐', '内容推荐', '个性化首页', '精准营销'],
    techStack: ['Collaborative Filtering', 'Redis', 'PostgreSQL', 'Ktor'],
    estimatedDays: 4,
    isPopular: true,
  },
  {
    type: AICapabilityType.HYPER_PERSONALIZATION,
    name: '超个性化推荐',
    nameEn: 'Hyper Personalization',
    description: '极致个性化的用户体验',
    detailedDescription: '深度学习推荐系统，结合用户画像、行为分析、实时反馈，提供极致个性化体验。适用于高端电商、内容平台等场景。',
    icon: 'User',
    complexity: ComplexityLevel.COMPLEX,
    estimatedCost: 38.0,
    category: AICapabilityCategory.ANALYTICS,
    useCases: ['高端电商', '内容平台', '精准广告', '会员运营'],
    techStack: ['Deep Learning', 'Real-time ML', 'Redis', 'Kafka'],
    estimatedDays: 8,
    isNew: true,
  },

  // ===== 音频处理类 (1种) =====
  {
    type: AICapabilityType.SPEECH_RECOGNITION,
    name: '语音识别',
    nameEn: 'Speech Recognition',
    description: '将语音转换为文字',
    detailedDescription: '自动语音识别（ASR）系统，支持多语言识别、实时转写、说话人分离等功能。适用于语音输入、会议记录等场景。',
    icon: 'Mic',
    complexity: ComplexityLevel.MEDIUM,
    estimatedCost: 3.0,
    category: AICapabilityCategory.AUDIO,
    useCases: ['语音输入', '会议记录', '语音搜索', '智能客服'],
    techStack: ['Aliyun ASR', 'WebRTC', 'Ktor', 'MinIO'],
    estimatedDays: 3,
  },

  // ===== 实时流处理类 (1种) =====
  {
    type: AICapabilityType.REALTIME_STREAM,
    name: '实时流处理',
    nameEn: 'Real-time Stream Processing',
    description: '实时处理和分析数据流',
    detailedDescription: '实时流处理系统，支持数据实时采集、处理、分析、告警。适用于监控系统、实时推荐等场景。',
    icon: 'Radio',
    complexity: ComplexityLevel.COMPLEX,
    estimatedCost: 42.0,
    category: AICapabilityCategory.REALTIME,
    useCases: ['实时监控', '实时推荐', '风控系统', 'IoT平台'],
    techStack: ['Kafka', 'Flink', 'Redis', 'WebSocket'],
    estimatedDays: 9,
    isNew: true,
  },

  // ===== 内容审核类 (1种) =====
  {
    type: AICapabilityType.CONTENT_MODERATION,
    name: '内容审核',
    nameEn: 'Content Moderation',
    description: '自动审核文本、图片、视频内容',
    detailedDescription: '智能内容审核系统，检测违规内容、敏感信息、垃圾广告等。适用于社交平台、UGC社区等场景。',
    icon: 'Shield',
    complexity: ComplexityLevel.MEDIUM,
    estimatedCost: 6.0,
    category: AICapabilityCategory.VISION,
    useCases: ['社交平台', 'UGC社区', '内容平台', '评论审核'],
    techStack: ['Aliyun Content Security', 'Qwen-Max', 'Ktor', 'Redis'],
    estimatedDays: 3,
  },

  // ===== 智能搜索类 (1种) =====
  {
    type: AICapabilityType.SMART_SEARCH,
    name: '智能搜索',
    nameEn: 'Smart Search',
    description: '语义理解的智能搜索引擎',
    detailedDescription: '智能搜索系统，支持语义理解、模糊匹配、个性化排序、搜索建议等功能。适用于电商、知识库等场景。',
    icon: 'Search',
    complexity: ComplexityLevel.MEDIUM,
    estimatedCost: 4.5,
    category: AICapabilityCategory.ANALYTICS,
    useCases: ['电商搜索', '知识库搜索', '站内搜索', '智能推荐'],
    techStack: ['Elasticsearch', 'Embedding', 'Supabase', 'Ktor'],
    estimatedDays: 4,
    isNew: true,
  },
];

/**
 * 按类别分组的AI能力
 */
export const AI_CAPABILITIES_BY_CATEGORY = {
  [AICapabilityCategory.CONVERSATION]: AI_CAPABILITIES.filter(
    (c) => c.category === AICapabilityCategory.CONVERSATION
  ),
  [AICapabilityCategory.VISION]: AI_CAPABILITIES.filter(
    (c) => c.category === AICapabilityCategory.VISION
  ),
  [AICapabilityCategory.DOCUMENT]: AI_CAPABILITIES.filter(
    (c) => c.category === AICapabilityCategory.DOCUMENT
  ),
  [AICapabilityCategory.ANALYTICS]: AI_CAPABILITIES.filter(
    (c) => c.category === AICapabilityCategory.ANALYTICS
  ),
  [AICapabilityCategory.GENERATION]: AI_CAPABILITIES.filter(
    (c) => c.category === AICapabilityCategory.GENERATION
  ),
  [AICapabilityCategory.AUDIO]: AI_CAPABILITIES.filter(
    (c) => c.category === AICapabilityCategory.AUDIO
  ),
  [AICapabilityCategory.REALTIME]: AI_CAPABILITIES.filter(
    (c) => c.category === AICapabilityCategory.REALTIME
  ),
};

/**
 * 按复杂度分组的AI能力
 */
export const AI_CAPABILITIES_BY_COMPLEXITY = {
  [ComplexityLevel.SIMPLE]: AI_CAPABILITIES.filter(
    (c) => c.complexity === ComplexityLevel.SIMPLE
  ),
  [ComplexityLevel.MEDIUM]: AI_CAPABILITIES.filter(
    (c) => c.complexity === ComplexityLevel.MEDIUM
  ),
  [ComplexityLevel.COMPLEX]: AI_CAPABILITIES.filter(
    (c) => c.complexity === ComplexityLevel.COMPLEX
  ),
};

/**
 * 热门AI能力
 */
export const POPULAR_AI_CAPABILITIES = AI_CAPABILITIES.filter(
  (c) => c.isPopular
);

/**
 * 新增AI能力
 */
export const NEW_AI_CAPABILITIES = AI_CAPABILITIES.filter((c) => c.isNew);

/**
 * 获取AI能力详情
 */
export function getAICapability(
  type: AICapabilityType
): AICapability | undefined {
  return AI_CAPABILITIES.find((c) => c.type === type);
}

/**
 * 计算选中AI能力的统计信息
 */
export function calculateStats(selectedTypes: AICapabilityType[]) {
  const selectedCapabilities = selectedTypes
    .map((type) => getAICapability(type))
    .filter((c): c is AICapability => c !== undefined);

  const totalCost = selectedCapabilities.reduce(
    (sum, c) => sum + c.estimatedCost,
    0
  );
  const totalDays = selectedCapabilities.reduce(
    (sum, c) => sum + c.estimatedDays,
    0
  );

  const complexityScores = {
    [ComplexityLevel.SIMPLE]: 1,
    [ComplexityLevel.MEDIUM]: 2,
    [ComplexityLevel.COMPLEX]: 3,
  };

  const avgComplexity =
    selectedCapabilities.length > 0
      ? selectedCapabilities.reduce(
          (sum, c) => sum + complexityScores[c.complexity],
          0
        ) / selectedCapabilities.length
      : 0;

  return {
    total: AI_CAPABILITIES.length,
    selected: selectedCapabilities.length,
    totalCost,
    totalDays,
    avgComplexity,
  };
}
