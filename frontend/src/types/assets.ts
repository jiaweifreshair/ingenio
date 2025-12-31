/**
 * 资产类型定义
 *
 * 定义模板、能力等资产的数据结构
 *
 * @module types/assets
 * @author Ingenio Team
 * @since Phase 1 - G3 Engine MVP
 */

/**
 * 能力定义
 */
export interface Capability {
  /** 能力ID */
  id: string;
  /** 能力名称 */
  name: string;
  /** 能力描述 */
  description: string;
  /** 能力类型 */
  type: 'JAVA_SERVICE' | 'PYTHON_AGENT' | 'JEECG_AI_NATIVE';
  /** 是否必需（不可禁用） */
  isRequired?: boolean;
  /** 默认是否启用 */
  defaultEnabled?: boolean;
}

/**
 * 模板定义
 */
export interface Template {
  /** 模板ID */
  id: string;
  /** 模板名称 */
  name: string;
  /** 模板描述 */
  description: string;
  /** 包含的能力列表 */
  capabilities: Capability[];
  /** 应用场景 */
  scene?: string;
  /** 模板图标 */
  icon?: string;
  /** 模板标签 */
  tags?: string[];
  /** 预览图片URL */
  previewImage?: string;
  /** 版本号 */
  version?: string;
  /** 使用次数 */
  usageCount?: number;
  /** 创建者 */
  createdBy?: string;
  /** 发布时间 */
  publishedAt?: string;
  /** 模板状态 */
  status?: 'DRAFT' | 'PUBLISHED' | 'DEPRECATED';
  /** 创建时间 */
  createdAt?: string;
  /** 更新时间 */
  updatedAt?: string;
}
