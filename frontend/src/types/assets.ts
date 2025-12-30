/**
 * 能力类型定义
 * 定义系统支持的能力类型
 */
export type CapabilityType =
  | 'JAVA_SERVICE'      // 传统 Java 服务 (e.g. 支付, 短信)
  | 'PYTHON_AGENT'      // 外挂 AgentScope 智能体
  | 'JEECG_AI_NATIVE';  // JeecgBoot 原生 AI 组件 (e.g. 智能搜索, 表单填充)

/**
 * 能力定义接口
 */
export interface Capability {
  id: string;
  name: string;
  description: string;
  type: CapabilityType;
  isRequired?: boolean;
  defaultEnabled?: boolean;
  // 可选：记录 Java 接口签名或 Python 脚本路径
  meta?: Record<string, unknown>;
}

/**
 * 模板状态类型
 */
export type TemplateStatus = 'DRAFT' | 'PUBLISHED' | 'ARCHIVED';

/**
 * 模板定义接口
 */
export interface Template {
  id: string;
  name: string;
  description: string;
  previewImage?: string;
  scene: string;
  tags: string[];
  status: TemplateStatus;
  version: string;
  usageCount: number;
  createdBy: string;
  createdAt: string;
  publishedAt?: string;
  capabilities: Capability[];
}
