/**
 * 分析步骤结果类型定义
 *
 * 定义每个分析步骤的结果数据结构
 */

/**
 * Step 1: 需求语义解析结果
 */
export interface Step1Result {
  /** 核心需求摘要（1-2句话） */
  summary: string;
  /** 提取的关键实体 */
  entities: string[];
  /** 提取的关键动作 */
  actions: string[];
  /** 业务场景描述 */
  businessScenario: string;
}

/**
 * 实体定义
 */
export interface Entity {
  /** 实体名称（英文） */
  name: string;
  /** 实体中文名 */
  displayName: string;
  /** 实体字段列表 */
  fields: EntityField[];
}

/**
 * 实体字段
 */
export interface EntityField {
  /** 字段名称 */
  name: string;
  /** 字段类型 */
  type: string;
  /** 字段描述 */
  description?: string;
}

/**
 * 实体关系
 */
export interface EntityRelationship {
  /** 源实体 */
  from: string;
  /** 目标实体 */
  to: string;
  /** 关系类型 */
  type: 'ONE_TO_ONE' | 'ONE_TO_MANY' | 'MANY_TO_MANY';
  /** 关系描述 */
  description?: string;
}

/**
 * Step 2: 实体关系建模结果
 */
export interface Step2Result {
  /** 识别的核心实体列表 */
  entities: Entity[];
  /** 实体间的关系 */
  relationships: EntityRelationship[];
}

/**
 * 功能模块
 */
export interface FunctionModule {
  /** 模块名称（英文） */
  name: string;
  /** 模块中文名 */
  displayName: string;
  /** 模块描述 */
  description: string;
  /** 子功能列表 */
  features: string[];
}

/**
 * Step 3: 功能意图识别结果
 */
export interface Step3Result {
  /** 识别的意图类型 */
  intent: 'CLONE' | 'DESIGN' | 'HYBRID';
  /** 置信度分数 (0-1) */
  confidence: number;
  /** 提取的关键词 */
  keywords: string[];
  /** 参考URL（如果有） */
  referenceUrls?: string[];
  /** 定制化需求 */
  customizationRequirement?: string;
  /** 核心功能模块列表 */
  modules: FunctionModule[];
}

/**
 * 技术栈
 */
export interface TechStack {
  /** 技术名称 */
  name: string;
  /** 技术版本 */
  version?: string;
  /** 技术描述 */
  description?: string;
}

/**
 * 第三方服务
 */
export interface ThirdPartyService {
  /** 服务名称 */
  name: string;
  /** 服务用途 */
  purpose: string;
}

/**
 * Step 4: 技术架构选型结果
 */
export interface Step4Result {
  /** 前端技术栈 */
  frontend: TechStack[];
  /** 后端技术栈 */
  backend: TechStack[];
  /** 架构模式 */
  architecturePatterns: string[];
  /** 第三方服务 */
  thirdPartyServices: ThirdPartyService[];
  /** 技术选型理由 */
  reasoning: string;
}

/**
 * 风险点
 */
export interface Risk {
  /** 风险级别 */
  level: 'HIGH' | 'MEDIUM' | 'LOW';
  /** 风险描述 */
  description: string;
  /** 风险类别 */
  category: 'PERFORMANCE' | 'SECURITY' | 'SCALABILITY' | 'COMPLEXITY' | 'OTHER';
}

/**
 * 预估工作量
 */
export interface EstimatedWorkload {
  /** 功能点数量 */
  featureCount: number;
  /** 预估开发周期 */
  estimatedWeeks: string;
  /** 团队规模建议 */
  teamSize: string;
}

/**
 * Step 5: 复杂度与风险评估结果
 */
export interface Step5Result {
  /** 开发复杂度评分 (1-10) */
  complexityScore: number;
  /** 复杂度细分 */
  complexityBreakdown: {
    frontend: number;
    backend: number;
    database: number;
    integration: number;
  };
  /** 技术风险点 */
  risks: Risk[];
  /** 预估工作量 */
  estimatedWorkload: EstimatedWorkload;
  /** 风险缓解措施 */
  mitigations: string[];
}

/**
 * 步骤结果联合类型
 */
export type StepResult =
  | { step: 1; data: Step1Result }
  | { step: 2; data: Step2Result }
  | { step: 3; data: Step3Result }
  | { step: 4; data: Step4Result }
  | { step: 5; data: Step5Result };

/**
 * 步骤确认状态
 */
export interface StepConfirmationState {
  /** 当前步骤 (1-6) */
  currentStep: number;
  /** 是否等待用户确认 */
  isWaitingConfirmation: boolean;
  /** 每个步骤的结果 */
  stepResults: Record<number, StepResult>;
  /** 已确认的步骤集合 */
  confirmedSteps: Set<number>;
}
