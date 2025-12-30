// Phase 1: 本地 Mock 数据源
// 用于前端开发和测试，解耦后端依赖

import { Template, Capability } from '@/types/assets';

// 通用能力定义 (可复用)
const COMMON_CAPABILITIES: Record<string, Capability> = {
  NLU_BASIC: {
    id: 'cap-nlu-basic',
    name: '基础意图理解',
    description: '识别用户输入的核心意图',
    type: 'JEECG_AI_NATIVE',
    isRequired: true,
    defaultEnabled: true,
  },
  RAG_KNOWLEDGE: {
    id: 'cap-rag-knowledge',
    name: '知识库检索 (RAG)',
    description: '基于上传的文档回答问题',
    type: 'JEECG_AI_NATIVE',
    isRequired: false,
    defaultEnabled: true,
  },
  MULTI_TURN: {
    id: 'cap-multi-turn',
    name: '多轮对话记忆',
    description: '保持对话上下文连贯性',
    type: 'JEECG_AI_NATIVE',
    isRequired: false,
    defaultEnabled: true,
  },
  CHART_UI: {
    id: 'cap-chart-ui',
    name: '数据图表组件',
    description: '自动生成可视化图表',
    type: 'JEECG_AI_NATIVE',
    isRequired: false,
    defaultEnabled: true,
  },
  VOICE_INPUT: {
    id: 'cap-voice-input',
    name: '语音输入',
    description: '支持语音转文字输入',
    type: 'PYTHON_AGENT',
    isRequired: false,
    defaultEnabled: false,
  }
};

export const MOCK_TEMPLATES: Template[] = [
  {
    id: 'tpl-customer-service',
    name: '电商智能客服',
    description: '专为电商场景设计的 AI 客服，支持订单查询、售后处理和商品推荐。内置多轮对话和意图识别能力。',
    previewImage: '/images/templates/customer-service.png',
    scene: 'customer_service',
    tags: ['电商', '客服', '自动化'],
    status: 'PUBLISHED',
    version: '1.0.0',
    usageCount: 1250,
    createdBy: 'system',
    createdAt: '2025-01-01T00:00:00Z',
    publishedAt: '2025-01-01T00:00:00Z',
    capabilities: [
      COMMON_CAPABILITIES.NLU_BASIC,
      COMMON_CAPABILITIES.MULTI_TURN,
      {
        id: 'cap-order-api',
        name: '订单系统集成',
        description: 'Mock 订单查询 API 集成',
        type: 'JAVA_SERVICE',
        isRequired: false,
        defaultEnabled: true,
      }
    ]
  },
  {
    id: 'tpl-data-dashboard',
    name: '数据分析仪表盘',
    description: '快速生成业务数据分析看板，支持 CSV 上传、自动生成图表和关键指标分析。',
    previewImage: '/images/templates/data-dashboard.png',
    scene: 'data_analysis',
    tags: ['数据', '可视化', '报表'],
    status: 'PUBLISHED',
    version: '1.2.0',
    usageCount: 890,
    createdBy: 'system',
    createdAt: '2025-01-10T00:00:00Z',
    publishedAt: '2025-01-12T00:00:00Z',
    capabilities: [
      COMMON_CAPABILITIES.NLU_BASIC,
      COMMON_CAPABILITIES.CHART_UI,
      {
        id: 'cap-data-processing',
        name: '数据清洗引擎',
        description: '自动处理缺失值和异常数据',
        type: 'JAVA_SERVICE',
        isRequired: true,
        defaultEnabled: true,
      }
    ]
  },
  {
    id: 'tpl-content-writer',
    name: 'SEO 内容创作助手',
    description: '辅助生成 SEO 友好的营销文章，支持关键词优化、多风格改写和自动配图建议。',
    previewImage: '/images/templates/content-writer.png',
    scene: 'content_creation',
    tags: ['营销', 'SEO', '写作'],
    status: 'PUBLISHED',
    version: '1.0.0',
    usageCount: 2100,
    createdBy: 'system',
    createdAt: '2025-01-05T00:00:00Z',
    publishedAt: '2025-01-05T00:00:00Z',
    capabilities: [
      COMMON_CAPABILITIES.NLU_BASIC,
      COMMON_CAPABILITIES.RAG_KNOWLEDGE,
      {
        id: 'cap-seo-analyzer',
        name: 'SEO 评分工具',
        description: '实时分析文章 SEO 得分',
        type: 'JAVA_SERVICE',
        isRequired: false,
        defaultEnabled: true,
      }
    ]
  }
];
