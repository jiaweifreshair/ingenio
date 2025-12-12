/**
 * Plan Routing API 客户端
 * V2.0 核心API - 处理意图识别、模板匹配、风格选择、设计确认流程
 *
 * 与后端 PlanRoutingController 交互
 * 业务流程：routeRequirement → selectStyle → confirmDesign → Execute阶段
 *
 * @author Ingenio Team
 * @version 2.0.0
 * @since 2025-11-15
 */

import { post, get } from '@/lib/api/client';
import type { RequirementIntent } from '@/types/intent';
import type { DesignStyle } from '@/types/design-style';
import type { Template } from '@/types/template';

// ==================== 请求类型定义 ====================

/**
 * Plan路由请求
 */
export interface PlanRoutingRequest {
  /** 用户需求描述 */
  userRequirement: string;
  /** 租户ID（可选，默认使用当前用户的租户） */
  tenantId?: string;
  /** 用户ID（可选，默认使用当前登录用户） */
  userId?: string;
  /**
   * 用户预选的复杂度分类（可选）
   *
   * V2.0新增：用户从首页4种分类中选择后传入
   * - SIMPLE: 多端套壳应用 → H5+WebView
   * - MEDIUM: 纯Web应用 → React + Supabase
   * - COMPLEX: 企业级应用 → React + Spring Boot
   * - NEEDS_CONFIRMATION: 原生功能应用 → Kuikly
   */
  complexityHint?: string;
  /**
   * 用户预选的技术栈提示（可选）
   *
   * V2.0新增：用户从首页选择后传入的技术栈
   * - "H5+WebView"
   * - "React+Supabase"
   * - "React+SpringBoot"
   * - "Kuikly"
   */
  techStackHint?: string;
}

/**
 * 风格选择请求
 */
export interface SelectStyleRequest {
  /** AppSpec ID */
  appSpecId: string;
  /** 选择的风格ID（A-G对应的code，如 modern_minimal） */
  styleId: string;
}

// ==================== 响应类型定义 ====================

/**
 * 路由分支枚举
 */
export enum RoutingBranch {
  /** 克隆分支 - 直接爬取目标网站 */
  CLONE = 'CLONE',
  /** 设计分支 - SuperDesign 7风格选择 */
  DESIGN = 'DESIGN',
  /** 混合分支 - 爬取 + AI定制化 */
  HYBRID = 'HYBRID',
}

/**
 * 路由分支显示信息
 */
export const ROUTING_BRANCH_DISPLAY: Record<RoutingBranch, { name: string; description: string; icon: string }> = {
  [RoutingBranch.CLONE]: {
    name: '克隆模式',
    description: '直接爬取参考网站，快速生成原型',
    icon: '🔄',
  },
  [RoutingBranch.DESIGN]: {
    name: '设计模式',
    description: 'AI生成7种设计风格，用户选择最满意的方案',
    icon: '✨',
  },
  [RoutingBranch.HYBRID]: {
    name: '混合模式',
    description: '爬取参考网站后，根据需求定制化修改',
    icon: '🎨',
  },
};

/**
 * 模板匹配结果
 */
export interface TemplateMatchResult {
  /** 模板信息 */
  template: Template;
  /** 匹配分数 (0-1) */
  matchScore: number;
  /** 匹配的关键词 */
  matchedKeywords: string[];
}

/**
 * 风格变体信息
 */
export interface StyleVariant {
  /** 风格ID (A-G) */
  styleId: string;
  /** 风格名称 */
  styleName: string;
  /** 风格代码 (modern_minimal 等) */
  styleCode: DesignStyle;
  /** 预览HTML */
  previewHtml?: string;
  /** 预览缩略图URL */
  thumbnailUrl?: string;
  /** 颜色主题 */
  colorTheme?: string;
}

/**
 * Plan路由结果
 */
export interface PlanRoutingResult {
  /** AppSpec ID */
  appSpecId: string;
  /** 识别的意图类型 */
  intent: RequirementIntent;
  /** 意图识别置信度 (0-1) */
  confidence: number;
  /** 路由分支 */
  branch: RoutingBranch;
  /** 匹配的行业模板结果列表 */
  matchedTemplateResults?: TemplateMatchResult[];
  /** 7种风格变体（仅设计分支有值） */
  styleVariants?: StyleVariant[];
  /** 原型是否已生成 */
  prototypeGenerated: boolean;
  /** 原型预览URL */
  prototypeUrl?: string;
  /** 用户选择的风格ID */
  selectedStyleId?: string;
  /** 下一步操作指引 */
  nextAction: string;
  /** 是否需要用户确认 */
  requiresUserConfirmation: boolean;
  /** 附加元数据 */
  metadata?: Record<string, unknown>;
}

/**
 * 设计确认结果
 */
export interface DesignConfirmResult {
  /** 是否成功 */
  success: boolean;
  /** 消息 */
  message: string;
  /** AppSpec ID */
  appSpecId?: string;
  /** 是否可以进入Execute阶段 */
  canProceedToExecute?: boolean;
}

// ==================== API 函数 ====================

/**
 * 路由用户需求
 * 执行意图识别、模板匹配、路由决策
 *
 * @param request - 路由请求
 * @returns 路由结果，包含匹配的模板、风格选项等
 */
export async function routeRequirement(
  request: PlanRoutingRequest
): Promise<PlanRoutingResult> {
  console.log('[PlanRouting API] 开始路由用户需求:', request.userRequirement);

  // 参数验证
  if (!request.userRequirement || request.userRequirement.trim().length === 0) {
    throw new Error('用户需求描述不能为空');
  }

  if (request.userRequirement.trim().length < 10) {
    throw new Error('需求描述过短，请至少输入10个字符');
  }

  try {
    const response = await post<PlanRoutingResult>(
      '/v2/plan-routing/route',
      {
        userRequirement: request.userRequirement.trim(),
        tenantId: request.tenantId,
        userId: request.userId,
        complexityHint: request.complexityHint,
        techStackHint: request.techStackHint,
      }
    );

    console.log('[PlanRouting API] 路由响应:', response);

    if (!response.success || !response.data) {
      throw new Error(response.error || response.message || '路由请求失败');
    }

    return response.data;
  } catch (error) {
    console.warn('[PlanRouting API] 路由请求失败:', error);
    throw error;
    
    /* ==================== MOCK DATA FALLBACK (DISABLED) ====================
    // 模拟意图识别逻辑
    const reqLower = request.userRequirement.toLowerCase();
    const isClone = reqLower.includes('clone') || reqLower.includes('copy') || reqLower.includes('仿') || reqLower.includes('克隆');
    
    // 模拟模板数据
    const mockTemplates: TemplateMatchResult[] = [
      {
        template: {
          id: 'tpl_edu_01',
          name: '在线教育平台',
          description: '包含课程展示、视频播放、学生管理等功能的完整教育平台',
          category: 'Education',
          tags: ['Education', 'Video', 'Course'],
          thumbnailUrl: 'https://placehold.co/600x400/e0e7ff/4f46e5?text=Education+Platform',
          previewUrl: '#',
          framework: 'react',
          complexity: 'medium'
        },
        matchScore: 0.95,
        matchedKeywords: ['教育', '课程', '学习']
      },
      {
        template: {
          id: 'tpl_ecommerce_01',
          name: '现代化电商商城',
          description: '响应式电商模板，包含购物车、支付流程和后台管理',
          category: 'E-commerce',
          tags: ['Shop', 'Cart', 'Payment'],
          thumbnailUrl: 'https://placehold.co/600x400/fce7f3/db2777?text=E-commerce+Store',
          previewUrl: '#',
          framework: 'react',
          complexity: 'high'
        },
        matchScore: 0.85,
        matchedKeywords: ['商城', '购买', '商品']
      }
    ];

    // 模拟风格变体
    const mockStyleVariants: StyleVariant[] = [
      { styleId: 'A', styleName: '现代极简', styleCode: 'modern_minimal' as any, colorTheme: '#000000', thumbnailUrl: 'https://placehold.co/300x200/black/white?text=Modern+Minimal' },
      { styleId: 'B', styleName: '科技未来', styleCode: 'tech_futuristic' as any, colorTheme: '#0ea5e9', thumbnailUrl: 'https://placehold.co/300x200/0f172a/0ea5e9?text=Tech+Future' },
      { styleId: 'C', styleName: '温馨治愈', styleCode: 'warm_healing' as any, colorTheme: '#f59e0b', thumbnailUrl: 'https://placehold.co/300x200/fffbeb/f59e0b?text=Warm+Healing' },
      { styleId: 'D', styleName: '极客黑客', styleCode: 'geek_hacker' as any, colorTheme: '#22c55e', thumbnailUrl: 'https://placehold.co/300x200/052e16/22c55e?text=Geek+Hacker' },
      { styleId: 'E', styleName: '商务专业', styleCode: 'business_pro' as any, colorTheme: '#1e293b', thumbnailUrl: 'https://placehold.co/300x200/f8fafc/1e293b?text=Business+Pro' },
      { styleId: 'F', styleName: '活力青春', styleCode: 'vibrant_youth' as any, colorTheme: '#f43f5e', thumbnailUrl: 'https://placehold.co/300x200/fff1f2/f43f5e?text=Vibrant+Youth' },
      { styleId: 'G', styleName: '自然生态', styleCode: 'nature_eco' as any, colorTheme: '#10b981', thumbnailUrl: 'https://placehold.co/300x200/ecfdf5/10b981?text=Nature+Eco' },
    ];

    const mockResult: PlanRoutingResult = {
      appSpecId: 'mock-app-spec-' + Date.now(),
      intent: isClone ? 'CLONE_EXISTING_WEBSITE' : 'DESIGN_FROM_SCRATCH' as any,
      confidence: 0.92,
      branch: isClone ? RoutingBranch.CLONE : RoutingBranch.DESIGN,
      matchedTemplateResults: isClone ? mockTemplates : [],
      styleVariants: isClone ? undefined : mockStyleVariants,
      prototypeGenerated: false,
      nextAction: isClone ? '请选择最匹配的参考模板' : '请选择您喜欢的设计风格',
      requiresUserConfirmation: true,
      metadata: { isMock: true }
    };

    return new Promise(resolve => setTimeout(() => resolve(mockResult), 1500));
    */
  }
}

/**
 * 选择设计风格并生成原型
 * 仅设计分支有效，生成完整的前端原型代码
 *
 * @param appSpecId - AppSpec ID
 * @param styleId - 选择的风格ID（A-G对应的code）
 * @returns 更新后的路由结果，包含原型URL
 */
export async function selectStyleAndGeneratePrototype(
  appSpecId: string,
  styleId: string
): Promise<PlanRoutingResult> {
  console.log('[PlanRouting API] 选择风格并生成原型:', { appSpecId, styleId });

  // 参数验证
  if (!appSpecId) {
    throw new Error('AppSpec ID 不能为空');
  }

  if (!styleId) {
    throw new Error('风格ID不能为空');
  }

  try {
    const response = await post<PlanRoutingResult>(
      `/v2/plan-routing/${appSpecId}/select-style?styleId=${encodeURIComponent(styleId)}`,
      {}
    );

    console.log('[PlanRouting API] 风格选择响应:', response);

    if (!response.success || !response.data) {
      throw new Error(response.error || response.message || '风格选择失败');
    }

    return response.data;
  } catch (error) {
    console.warn('[PlanRouting API] 风格选择失败:', error);
    throw error;
    
    /* ==================== MOCK DATA FALLBACK (DISABLED) ====================
    const mockResult: PlanRoutingResult = {
      appSpecId: appSpecId,
      intent: 'DESIGN_FROM_SCRATCH' as any,
      confidence: 0.92,
      branch: RoutingBranch.DESIGN,
      styleVariants: [], // 不需要再返回所有变体
      prototypeGenerated: true,
      prototypeUrl: 'https://example.com', // 这里应该是一个真实的预览URL，或者由Frontend组件自行生成
      selectedStyleId: styleId,
      nextAction: '请确认原型设计方案',
      requiresUserConfirmation: true,
      metadata: { isMock: true }
    };

    return new Promise(resolve => setTimeout(() => resolve(mockResult), 2000));
    */
  }
}

/**
 * 确认设计方案
 * V2.0关键API：用户确认设计后，才能进入Execute阶段
 *
 * @param appSpecId - AppSpec ID
 * @returns 确认结果
 */
export async function confirmDesign(
  appSpecId: string
): Promise<DesignConfirmResult> {
  console.log('[PlanRouting API] 确认设计方案:', appSpecId);

  // 参数验证
  if (!appSpecId) {
    throw new Error('AppSpec ID 不能为空');
  }

  try {
    const response = await post<string>(
      `/v2/plan-routing/${appSpecId}/confirm-design`,
      {}
    );

    console.log('[PlanRouting API] 设计确认响应:', response);

    if (!response.success) {
      throw new Error(response.error || response.message || '设计确认失败');
    }

    return {
      success: true,
      message: response.data || '设计确认成功，可以进入Execute阶段',
      appSpecId,
      canProceedToExecute: true,
    };
  } catch (error) {
    console.warn('[PlanRouting API] 设计确认失败:', error);
    throw error;

    /* ==================== MOCK DATA FALLBACK (DISABLED) ====================
    return new Promise(resolve => setTimeout(() => resolve({
      success: true,
      message: '设计确认成功 (Mock)',
      appSpecId,
      canProceedToExecute: true
    }), 1000));
    */
  }
}

// ==================== Phase 2.2.4 新增API ====================

/**
 * 代码生成结果接口
 * Phase 2.2.4: ExecuteAgent V2 返回的代码生成结果
 */
export interface CodeGenerationResult {
  /** 是否成功 */
  success: boolean;
  /** 版本标识 */
  version?: string;
  /** 数据库Schema */
  database?: {
    entityCount: number;
    schema: string;
  };
  /** 后端代码 */
  backend?: {
    framework: string;
    language: string;
    files: Record<string, string>;
  };
  /** 前端代码 */
  frontend?: {
    framework: string;
    files: Record<string, string>;
  };
  /** 错误信息 */
  error?: string;
}

/**
 * 执行代码生成
 * Phase 2.2.4新增: 用户确认设计后，调用ExecuteAgent生成完整代码
 *
 * 完整V2流程最后一步：
 * 1. 从AppSpec.metadata提取designSpec
 * 2. 构建PlanResult并填充designSpec
 * 3. 调用ExecuteAgent生成完整的全栈代码（PostgreSQL + Spring Boot + React）
 *
 * @param appSpecId - AppSpec ID
 * @returns 代码生成结果
 *
 * @example
 * ```typescript
 * // 在confirmDesign成功后调用
 * const confirmResult = await confirmDesign(appSpecId);
 * if (confirmResult.success) {
 *   const codeResult = await executeCodeGeneration(appSpecId);
 *   console.log('代码生成完成:', codeResult.version);
 * }
 * ```
 */
export async function executeCodeGeneration(
  appSpecId: string
): Promise<CodeGenerationResult> {
  console.log('[PlanRouting API] 执行代码生成:', appSpecId);

  // 参数验证
  if (!appSpecId) {
    throw new Error('AppSpec ID 不能为空');
  }

  try {
    const response = await post<CodeGenerationResult>(
      `/v2/plan-routing/${appSpecId}/execute-code-generation`,
      {}
    );

    console.log('[PlanRouting API] 代码生成响应:', response);

    if (!response.success || !response.data) {
      throw new Error(response.error || response.message || '代码生成失败');
    }

    return response.data;
  } catch (error) {
    console.warn('[PlanRouting API] 代码生成失败:', error);
    throw error;
  }
}

/**
 * 获取路由状态
 * 查询当前AppSpec的路由状态和进度
 *
 * @param appSpecId - AppSpec ID
 * @returns 当前路由状态
 *
 * @example
 * ```typescript
 * const status = await getRoutingStatus('uuid-xxx');
 * console.log(status.branch); // 当前分支
 * console.log(status.prototypeGenerated); // 原型是否已生成
 * ```
 */
export async function getRoutingStatus(
  appSpecId: string
): Promise<PlanRoutingResult> {
  console.log('[PlanRouting API] 查询路由状态:', appSpecId);

  // 参数验证
  if (!appSpecId) {
    throw new Error('AppSpec ID 不能为空');
  }

  try {
    const response = await get<PlanRoutingResult>(
      `/v2/plan-routing/${appSpecId}/status`
    );

    console.log('[PlanRouting API] 状态查询响应:', response);

    if (!response.success || !response.data) {
      throw new Error(response.error || response.message || '状态查询失败');
    }

    return response.data;
  } catch (error) {
    console.error('[PlanRouting API] 状态查询失败:', error);
    throw error;
  }
}

// ==================== 辅助函数 ====================

/**
 * 判断是否为设计分支
 */
export function isDesignBranch(result: PlanRoutingResult): boolean {
  return result.branch === RoutingBranch.DESIGN;
}

/**
 * 判断是否为克隆分支
 */
export function isCloneBranch(result: PlanRoutingResult): boolean {
  return result.branch === RoutingBranch.CLONE;
}

/**
 * 判断是否为混合分支
 */
export function isHybridBranch(result: PlanRoutingResult): boolean {
  return result.branch === RoutingBranch.HYBRID;
}

/**
 * 获取路由分支显示信息
 */
export function getBranchDisplayInfo(branch: RoutingBranch) {
  return ROUTING_BRANCH_DISPLAY[branch];
}

/**
 * 判断路由结果是否需要模板选择步骤
 */
export function requiresTemplateSelection(result: PlanRoutingResult): boolean {
  return (
    result.matchedTemplateResults !== undefined &&
    result.matchedTemplateResults.length > 0
  );
}

/**
 * 判断路由结果是否需要风格选择步骤
 */
export function requiresStyleSelection(result: PlanRoutingResult): boolean {
  return isDesignBranch(result) && !result.selectedStyleId;
}

/**
 * 判断路由结果是否可以确认设计
 */
export function canConfirmDesign(result: PlanRoutingResult): boolean {
  return result.prototypeGenerated && result.requiresUserConfirmation;
}
