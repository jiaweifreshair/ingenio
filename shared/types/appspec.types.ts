/**
 * AppSpec核心类型定义
 * 共享给前端、后端和Workers使用
 */

/**
 * AppSpec应用规范 - 完整定义
 * 扩展支持Dyad的constraints和testCases、SuperDesign、AipexBase、AI Native
 */
export interface AppSpec {
  // 基础信息
  id: string;
  version: string;
  tenantId: string;
  createdAt: string;
  updatedAt: string;

  // 页面定义
  pages: Page[];

  // 数据模型定义
  dataModels: DataModel[];

  // 流程定义
  flows: Flow[];

  // 权限定义
  permissions: Permission[];

  // ===== Dyad扩展字段 =====
  // 约束规则（引用完整性、类型一致性等）
  constraints: Constraint[];

  // 测试用例（用于自动化验证）
  testCases: TestCase[];

  // ===== SuperDesign扩展字段 =====
  // 自定义组件（SuperDesign生成）
  customComponents?: CustomComponent[];

  // 主题变量
  themeVars?: ThemeVariables;

  // ===== AipexBase扩展字段 =====
  // AipexBase 配置（服务端 No-code 生成）
  aipexBaseConfig?: AipexBaseConfig;

  // ===== AI Native 扩展字段 =====
  // AI 功能规范（Agent 组件/MCP 调用）
  aiFeatures?: AIFeature[];
}

/**
 * 页面定义
 */
export interface Page {
  id: string;
  name: string;
  path: string;
  components: string[]; // 组件ID引用
  layout?: string;
  meta?: PageMeta;
}

export interface PageMeta {
  title?: string;
  description?: string;
  keywords?: string[];
  ogImage?: string;
}

/**
 * 数据模型定义
 */
export interface DataModel {
  id: string;
  name: string;
  description?: string;
  fields: Field[];
  indexes?: Index[];
  relations?: Relation[];
  // AipexBase 扩展（可选）
  aipexBase?: {
    tableName: string;
    autoSync: boolean;
    permissions: {
      read: string[];
      write: string[];
      delete?: string[];
    };
  };
}

export interface Field {
  name: string;
  type: FieldType;
  required: boolean;
  unique?: boolean;
  default?: any;
  validation?: ValidationRule;
}

export type FieldType = 'string' | 'number' | 'boolean' | 'date' | 'json' | 'reference' | 'array';

export interface ValidationRule {
  type: 'regex' | 'range' | 'length' | 'custom';
  value: string | number | [number, number];
  message?: string;
}

export interface Index {
  fields: string[];
  unique?: boolean;
  name?: string;
}

export interface Relation {
  type: 'oneToOne' | 'oneToMany' | 'manyToMany';
  target: string; // 目标DataModel ID
  foreignKey?: string;
  through?: string; // 中间表（仅manyToMany）
}

/**
 * 流程定义
 */
export interface Flow {
  id: string;
  name: string;
  trigger: Trigger;
  actions: Action[];
  conditions?: Condition[];
}

export interface Trigger {
  type: 'onSubmit' | 'onLoad' | 'onSchedule' | 'onEvent';
  target: string; // 触发目标（组件ID/事件名）
  schedule?: string; // cron表达式（仅onSchedule）
}

export interface Action {
  type: 'save' | 'update' | 'delete' | 'call' | 'navigate' | 'notify' | 'aipexBase.create' | 'aipexBase.update' | 'aipexBase.delete' | 'mcp.call';
  target: string;
  params?: Record<string, any>;
  // AipexBase 扩展
  aipexBase?: {
    table: string;
    data?: Record<string, any>;
  };
  // MCP 扩展
  mcp?: {
    server: string;
    tool: string;
    params?: Record<string, any>;
  };
}

export interface Condition {
  field: string;
  operator: 'eq' | 'ne' | 'gt' | 'gte' | 'lt' | 'lte' | 'in' | 'contains';
  value: any;
}

/**
 * 权限定义
 */
export interface Permission {
  id: string;
  resource: string; // 资源标识
  actions: PermissionAction[];
  roles: string[]; // 角色列表
}

export type PermissionAction = 'create' | 'read' | 'update' | 'delete' | 'execute';

/**
 * 约束规则（Dyad扩展）
 */
export interface Constraint {
  id: string;
  type: ConstraintType;
  rule: string; // 规则描述
  severity: 'error' | 'warning';
  validator?: string; // 验证器函数引用
}

export type ConstraintType =
  | 'RefIntegrity'      // 引用完整性
  | 'TypeConsistency'   // 类型一致性
  | 'PermissionValid'   // 权限合法性
  | 'FlowComplete'      // 流程完整性
  | 'UniqueConstraint'  // 唯一性约束
  | 'Custom';           // 自定义约束

/**
 * 测试用例（Dyad扩展）
 */
export interface TestCase {
  name: string;
  description?: string;
  steps: TestStep[];
  expect: TestExpectation;
}

export interface TestStep {
  action: 'render' | 'fill' | 'click' | 'submit' | 'wait' | 'navigate';
  target: string;
  value?: any;
  timeout?: number;
}

export interface TestExpectation {
  type: 'http' | 'dom' | 'data';
  condition: string; // 例如："http 200", "element visible"
}

/**
 * 自定义组件（SuperDesign集成）
 */
export interface CustomComponent {
  name: string;
  framework: 'react' | 'vue' | 'angular';
  source: string; // 代码路径或repo引用
  license: string;
  props?: ComponentProp[];
}

export interface ComponentProp {
  name: string;
  type: string;
  required: boolean;
  default?: any;
  description?: string;
}

/**
 * 主题变量
 */
export interface ThemeVariables {
  primary: string;
  secondary: string;
  accent?: string;
  neutral?: string;
  success?: string;
  warning?: string;
  error?: string;
  [key: string]: string | undefined;
}

/**
 * 版本信息
 */
export interface VersionInfo {
  id: string;
  appId: string;
  version: string;
  source: VersionSource;
  agentType?: AgentType;
  parentVersionId?: string;
  tenantId: string;
  createdBy?: string;
  createdAt: string;
  metadata?: VersionMetadata;
}

export type VersionSource = 'plan' | 'exec' | 'fix' | 'manual';
export type AgentType = 'plan' | 'execute' | 'validate';

export interface VersionMetadata {
  latencyMs?: number;
  modelProvider?: string;
  modelName?: string;
  tokenUsage?: TokenUsage;
  confidence?: number;
}

export interface TokenUsage {
  prompt: number;
  completion: number;
  total: number;
}

/**
 * 校验结果
 */
export interface ValidationResult {
  isValid: boolean;
  errors: ValidationError[];
  warnings: ValidationWarning[];
  suggestions: ValidationSuggestion[];
  score: number; // 质量评分 0-100
}

export interface ValidationError {
  type: string;
  severity: 'error';
  message: string;
  location: ValidationLocation;
  suggestion?: string;
}

export interface ValidationWarning {
  type: string;
  severity: 'warning';
  message: string;
  location: ValidationLocation;
  suggestion?: string;
}

export interface ValidationSuggestion {
  type: string;
  message: string;
  code?: string; // 建议的代码片段
}

export interface ValidationLocation {
  path: string; // JSON path
  line?: number;
  column?: number;
}

/**
 * AI 功能规范（AI Native 扩展）
 */
export interface AIFeature {
  id: string;
  type: AIFeatureType;
  name: string;
  description?: string;
  model: string; // AI 模型名称（如 gpt-4, dall-e-3）
  prompt?: string; // 系统提示词
  components: AIComponentConfig[]; // 前端组件配置
  mcp?: MCPConfig; // MCP 协议配置
  apiConfig?: APIConfig; // API 配置
}

export type AIFeatureType =
  | 'conversation'      // 对话
  | 'image_generation'  // 图像生成
  | 'code_generation'   // 代码生成
  | 'data_analysis'     // 数据分析
  | 'text_summarization' // 文本摘要
  | 'translation'       // 翻译
  | 'custom';           // 自定义

/**
 * AI 组件配置
 */
export interface AIComponentConfig {
  type: string; // 组件类型（如 ChatInterface, ImageGenerator）
  props: Record<string, any>; // 组件属性
  style?: Record<string, any>; // 样式配置
}

/**
 * MCP 协议配置
 */
export interface MCPConfig {
  server: string; // MCP 服务器名称（如 openai, anthropic）
  tools: string[]; // 可用的工具列表（如 web_search, code_execution）
  params?: Record<string, any>; // 额外参数
}

/**
 * API 配置
 */
export interface APIConfig {
  endpoint: string; // API 端点
  method: 'GET' | 'POST' | 'PUT' | 'DELETE';
  headers?: Record<string, string>;
  auth?: {
    type: 'bearer' | 'api_key' | 'oauth';
    token?: string;
  };
}

/**
 * AipexBase 配置（服务端 No-code 生成）
 */
export interface AipexBaseConfig {
  projectId: string;
  apiKey: string;
  endpoint: string;
  tables?: AipexBaseTableConfig[];
  auth?: AipexBaseAuthConfig;
  storage?: AipexBaseStorageConfig;
}

export interface AipexBaseTableConfig {
  tableName: string;
  dataModelId: string; // 关联的 DataModel ID
  autoSync: boolean;
  permissions: {
    read: string[]; // 角色列表
    write: string[];
    delete?: string[];
  };
}

export interface AipexBaseAuthConfig {
  providers: ('wechat' | 'email' | 'phone')[];
  sessionTimeout?: number; // 会话超时时间（秒）
}

export interface AipexBaseStorageConfig {
  bucket: string;
  maxFileSize?: number; // 最大文件大小（字节）
  allowedTypes?: string[]; // 允许的文件类型
}
