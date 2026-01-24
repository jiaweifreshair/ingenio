/**
 * 分析步骤结果归一化工具
 *
 * 是什么：将后端交互式分析（SSE）返回的 `result`（结构不稳定/可能为Map）转换为前端展示所需的稳定结构。
 * 做什么：把 Map/Object 形态的 entities/relationships/operations 等字段统一转换为数组，并为缺失字段填充可读的默认值。
 * 为什么：避免出现 `data.entities.map is not a function` 这类运行时崩溃，同时为后续前后端协议对齐预留收敛点。
 */

import type {
  Entity,
  EntityField,
  EntityRelationship,
  FunctionModule,
  Risk,
  Step1Result,
  Step2Result,
  Step3Result,
  Step4Result,
  Step5Result,
  StepResult
} from '@/types/analysis-step-results';

/**
 * 归一化选项
 *
 * 是什么：归一化过程中可用的辅助上下文。
 * 做什么：提供原始需求文本与历史步骤结果，用于生成更合理的默认值（如摘要、功能点数量）。
 * 为什么：后端各步骤返回结构与前端展示结构存在阶段性不一致，需要用上下文降低信息丢失。
 */
export interface NormalizeStepResultOptions {
  requirement?: string;
  previousStepResults?: Partial<Record<1 | 2 | 3 | 4, StepResult | undefined>>;
}

/**
 * 判断是否为普通对象（Record）
 *
 * 是什么：运行时类型守卫。
 * 做什么：在 `unknown` 上安全地进行字段访问。
 * 为什么：SSE result 可能为任意结构，必须避免直接断言导致崩溃。
 */
function isRecord(value: unknown): value is Record<string, unknown> {
  return !!value && typeof value === 'object' && !Array.isArray(value);
}

/**
 * 将未知值安全转换为字符串
 *
 * 是什么：字符串化工具。
 * 做什么：尽量把 `unknown` 转成可展示的短字符串。
 * 为什么：前端展示组件依赖 string 字段，避免出现 undefined/null。
 */
function toDisplayString(value: unknown, fallback = ''): string {
  if (typeof value === 'string') return value;
  if (typeof value === 'number') return String(value);
  if (typeof value === 'boolean') return value ? 'true' : 'false';
  if (value == null) return fallback;
  try {
    return JSON.stringify(value);
  } catch {
    return fallback;
  }
}

/**
 * 将未知值转换为字符串数组
 *
 * 是什么：数组归一化工具。
 * 做什么：支持 array/string/object-map 三类输入，将其转换为 `string[]`。
 * 为什么：前端展示经常使用 `.map` 渲染列表，必须确保返回数组。
 */
function toStringArray(value: unknown): string[] {
  if (!value) return [];

  if (Array.isArray(value)) {
    return value
      .map(v => toDisplayString(v, '').trim())
      .filter(Boolean);
  }

  if (typeof value === 'string') {
    const trimmed = value.trim();
    return trimmed ? [trimmed] : [];
  }

  if (isRecord(value)) {
    // 兼容后端 extractMap(List) 兜底：{ items: [...] }
    if (Array.isArray(value.items)) {
      return toStringArray(value.items);
    }
    return Object.keys(value).filter(Boolean);
  }

  return [];
}

/**
 * 从对象中安全读取字符串
 *
 * 是什么：字段读取工具。
 * 做什么：读取指定 key 的 string 值，否则返回 fallback。
 * 为什么：后端字段缺失/类型不一致时提供稳定兜底。
 */
function getString(record: Record<string, unknown>, key: string, fallback = ''): string {
  const value = record[key];
  return typeof value === 'string' ? value : fallback;
}

/**
 * 从对象中安全读取数字
 *
 * 是什么：字段读取工具。
 * 做什么：读取指定 key 的 number 值，必要时解析字符串，并进行区间裁剪。
 * 为什么：复杂度等字段需要稳定的数值，避免 NaN 传入 UI。
 */
function getNumber(
  record: Record<string, unknown>,
  key: string,
  fallback: number,
  options?: { min?: number; max?: number }
): number {
  const raw = record[key];
  const parsed =
    typeof raw === 'number'
      ? raw
      : typeof raw === 'string'
        ? Number(raw)
        : fallback;

  if (Number.isNaN(parsed)) return fallback;
  const min = options?.min ?? -Infinity;
  const max = options?.max ?? Infinity;
  return Math.min(max, Math.max(min, parsed));
}

/**
 * 解析字段字符串（如 "id: UUID" / "title (string)"）
 *
 * 是什么：字段字符串解析器。
 * 做什么：从 AI 返回的字段描述中尽量拆出 `name/type`。
 * 为什么：后端 entities.fields 可能只是字符串数组，前端展示需要 name/type。
 */
function parseEntityField(field: string): EntityField {
  const trimmed = field.trim();
  if (!trimmed) {
    return { name: '', type: 'string' };
  }

  const parenMatch = trimmed.match(/^(.+?)\s*\((.+)\)\s*$/);
  if (parenMatch) {
    return {
      name: parenMatch[1].trim(),
      type: parenMatch[2].trim() || 'string'
    };
  }

  const colonIndex = trimmed.indexOf(':');
  if (colonIndex > 0) {
    const name = trimmed.slice(0, colonIndex).trim();
    const type = trimmed.slice(colonIndex + 1).trim();
    return { name, type: type || 'string' };
  }

  return { name: trimmed, type: 'string' };
}

/**
 * 归一化实体列表
 *
 * 是什么：实体结构转换器。
 * 做什么：将后端 `entities`（Map/Object）转换为前端 `Entity[]`。
 * 为什么：后端以 Map 更适合存储与序列化，前端展示更适合数组。
 */
function normalizeEntities(value: unknown): Entity[] {
  if (!value) return [];

  if (Array.isArray(value)) {
    // 若后端未来直接返回数组，则尽量透传并做最小修补
    return value
      .filter(isRecord)
      .map((entity) => {
        const name = typeof entity.name === 'string' ? entity.name : '';
        const displayName = typeof entity.displayName === 'string' ? entity.displayName : name;
        const rawFields = Array.isArray(entity.fields) ? entity.fields : [];
        const fields: EntityField[] = rawFields
          .filter(isRecord)
          .map((f) => ({
            name: typeof f.name === 'string' ? f.name : '',
            type: typeof f.type === 'string' ? f.type : 'string',
            description: typeof f.description === 'string' ? f.description : undefined
          }))
          .filter(f => f.name);

        return { name, displayName, fields };
      })
      .filter(e => e.name);
  }

  if (isRecord(value)) {
    const entries = Object.entries(value);
    return entries
      .map(([entityName, entityValue]) => {
        const entityRecord = isRecord(entityValue) ? entityValue : {};
        const description = typeof entityRecord.description === 'string' ? entityRecord.description : undefined;
        const displayName =
          typeof entityRecord.displayName === 'string'
            ? entityRecord.displayName
            : (description && description.length <= 16 ? description : entityName);

        const rawFields = toStringArray(entityRecord.fields);
        const fields = rawFields
          .map(parseEntityField)
          .filter(f => f.name);

        return {
          name: entityName,
          displayName,
          fields
        };
      })
      .filter(e => e.name);
  }

  return [];
}

/**
 * 归一化实体关系
 *
 * 是什么：关系结构转换器。
 * 做什么：将后端 `relationships`（Map/Object）转换为前端 `EntityRelationship[]`。
 * 为什么：前端 UI 需要统一的 `ONE_TO_MANY` 等枚举值进行展示。
 */
function normalizeRelationships(value: unknown): EntityRelationship[] {
  if (!value) return [];

  const normalizeRelType = (rawType: string): EntityRelationship['type'] => {
    const normalized = rawType.trim().toLowerCase();
    if (normalized.includes('one-to-one') || normalized === '1:1' || normalized === 'one_to_one') return 'ONE_TO_ONE';
    if (normalized.includes('one-to-many') || normalized === '1:n' || normalized === 'one_to_many') return 'ONE_TO_MANY';
    if (normalized.includes('many-to-many') || normalized === 'n:n' || normalized === 'many_to_many') return 'MANY_TO_MANY';
    // 兜底：默认按 1:N 展示更常见
    return 'ONE_TO_MANY';
  };

  if (Array.isArray(value)) {
    return value
      .filter(isRecord)
      .map((rel) => ({
        from: toDisplayString(rel.from, '').trim(),
        to: toDisplayString(rel.to, '').trim(),
        type: normalizeRelType(toDisplayString(rel.type, '')),
        description: typeof rel.description === 'string' ? rel.description : undefined
      }))
      .filter(r => r.from && r.to);
  }

  if (isRecord(value)) {
    return Object.entries(value)
      .map(([relationName, relValue]): EntityRelationship | null => {
        if (isRecord(relValue)) {
          const from = toDisplayString(relValue.from, '').trim();
          const to = toDisplayString(relValue.to, '').trim();
          const type = normalizeRelType(toDisplayString(relValue.type, ''));
          const description =
            typeof relValue.description === 'string'
              ? relValue.description
              : relationName;

          return from && to ? { from, to, type, description } : null;
        }

        if (typeof relValue === 'string') {
          // 兼容极简返回：{ "Blog-Comment": "1:N" }
          const type = normalizeRelType(relValue);
          return { from: '', to: '', type, description: relationName };
        }

        return null;
      })
      .filter((r): r is EntityRelationship => r !== null && r.from.length > 0 && r.to.length > 0);
  }

  return [];
}

/**
 * 归一化功能模块（基于 operations）
 *
 * 是什么：功能模块转换器。
 * 做什么：将后端 `operations`（Map/Object）转换为 `FunctionModule[]`，作为 Step3 UI 的模块列表。
 * 为什么：当前后端 Step3 返回 operations/constraints，前端 Step3 期望 modules 列表；用该映射保证 UI 可用。
 */
function normalizeFunctionModules(value: unknown): FunctionModule[] {
  if (!value) return [];

  if (Array.isArray(value)) {
    return value
      .filter(isRecord)
      .map((m) => ({
        name: toDisplayString(m.name, '').trim(),
        displayName: toDisplayString(m.displayName, '').trim() || toDisplayString(m.name, '').trim(),
        description: toDisplayString(m.description, '').trim(),
        features: Array.isArray(m.features) ? toStringArray(m.features) : []
      }))
      .filter(m => m.name);
  }

  if (isRecord(value)) {
    return Object.entries(value)
      .map(([operationName, operationValue]) => {
        const op = isRecord(operationValue) ? operationValue : {};
        const description = typeof op.description === 'string' ? op.description : '';
        const type = typeof op.type === 'string' ? op.type : '';
        const displayName = operationName;

        const features: string[] = [];
        if (type) features.push(`类型：${type}`);
        if (description) features.push(description);

        return {
          name: operationName,
          displayName,
          description: description || '（未提供描述）',
          features
        };
      })
      .filter(m => m.name);
  }

  return [];
}

/**
 * 估算意图类型（CLONE/DESIGN/HYBRID）
 *
 * 是什么：前端兜底的意图估算逻辑。
 * 做什么：当后端暂未显式返回 intent 时，根据需求文本/URL 特征给出保守推断。
 * 为什么：Step3 UI 需要 `intent` 字段；此处仅为展示兜底，不应替代后端正式判定。
 */
function guessIntent(requirement?: string): Step3Result['intent'] {
  const text = (requirement || '').toLowerCase();
  if (!text) return 'DESIGN';

  const hasUrl = /https?:\/\/\S+/.test(text);
  const cloneKeywords = ['clone', '克隆', '复制', '仿', '参考', '对标', '照着', '模仿'];
  const designKeywords = ['从零', '设计', '新做', '新建', '全新', '自定义'];

  const looksLikeClone = hasUrl || cloneKeywords.some(k => text.includes(k));
  const looksLikeDesign = designKeywords.some(k => text.includes(k));

  if (looksLikeClone && looksLikeDesign) return 'HYBRID';
  if (looksLikeClone) return 'CLONE';
  return 'DESIGN';
}

/**
 * 归一化风险列表
 *
 * 是什么：风险结构转换器。
 * 做什么：将后端 riskFactors（string[]）等字段转换为前端 `Risk[]`。
 * 为什么：Step5 UI 需要按 level/category 展示风险点，并支持筛选。
 */
function normalizeRisks(value: unknown): Risk[] {
  const factors = toStringArray(value);
  if (factors.length === 0) return [];

  const guessCategory = (text: string): Risk['category'] => {
    const normalized = text.toLowerCase();
    if (normalized.includes('security') || text.includes('安全') || text.includes('鉴权')) return 'SECURITY';
    if (normalized.includes('performance') || text.includes('性能')) return 'PERFORMANCE';
    if (normalized.includes('scale') || text.includes('扩展') || text.includes('并发')) return 'SCALABILITY';
    if (normalized.includes('complex') || text.includes('复杂')) return 'COMPLEXITY';
    return 'OTHER';
  };

  const guessLevel = (text: string): Risk['level'] => {
    const normalized = text.toLowerCase();
    if (normalized.includes('high') || text.includes('高')) return 'HIGH';
    if (normalized.includes('low') || text.includes('低')) return 'LOW';
    return 'MEDIUM';
  };

  return factors.map((f) => ({
    level: guessLevel(f),
    category: guessCategory(f),
    description: f
  }));
}

/**
 * 生成一个稳定的 Step5 复杂度拆分
 *
 * 是什么：复杂度拆分生成器。
 * 做什么：从整体复杂度分数推导前端/后端/数据库/集成的拆分值。
 * 为什么：后端当前返回维度较少，但 UI 需要展示细分条形图。
 */
function buildComplexityBreakdown(score: number): Step5Result['complexityBreakdown'] {
  const clamp = (value: number) => Math.min(10, Math.max(1, value));
  return {
    frontend: clamp(Math.round(score * 0.35)),
    backend: clamp(Math.round(score * 0.35)),
    database: clamp(Math.round(score * 0.15)),
    integration: clamp(Math.round(score * 0.25))
  };
}

/**
 * 将后端返回的 step result 归一化为前端 `StepResult`
 *
 * 是什么：交互式分析步骤结果的主入口归一化函数。
 * 做什么：按 step(1-5) 将 `unknown` 的 result 转为前端稳定展示结构，并提供默认值。
 * 为什么：后端与前端在演进期会存在结构差异，归一化可避免 UI 因结构漂移而崩溃。
 */
export function normalizeStepResult(
  step: 1 | 2 | 3 | 4 | 5,
  raw: unknown,
  options?: NormalizeStepResultOptions
): StepResult {
  const requirement = options?.requirement;
  const previous = options?.previousStepResults;
  const record = isRecord(raw) ? raw : {};

  if (step === 1) {
    const entities = toStringArray(record.entities);
    const actions = toStringArray(record.actions).length > 0 ? toStringArray(record.actions) : toStringArray(record.operations);

    const summary =
      getString(record, 'summary') ||
      (requirement ? (requirement.trim().length > 120 ? `${requirement.trim().slice(0, 120)}...` : requirement.trim()) : '') ||
      '需求解析完成（后端暂未返回摘要字段）';

    const businessScenario =
      getString(record, 'businessScenario') ||
      getString(record, 'scenario') ||
      (requirement ? requirement.trim() : '') ||
      '（未提供业务场景字段）';

    const result: Step1Result = {
      summary,
      entities,
      actions,
      businessScenario
    };

    return { step: 1, data: result };
  }

  if (step === 2) {
    const entities = normalizeEntities(record.entities);
    const relationships = normalizeRelationships(record.relationships);
    const result: Step2Result = { entities, relationships };
    return { step: 2, data: result };
  }

  if (step === 3) {
    const operations = isRecord(record.operations) ? record.operations : record;
    const modules = normalizeFunctionModules(operations);

    const inferredKeywords = Array.from(new Set([
      ...toStringArray(record.keywords),
      ...modules.map(m => m.name).filter(Boolean)
    ])).slice(0, 12);

    const intent = (typeof record.intent === 'string' && (record.intent === 'CLONE' || record.intent === 'DESIGN' || record.intent === 'HYBRID'))
      ? record.intent
      : guessIntent(requirement);

    const confidence =
      typeof record.confidence === 'number'
        ? Math.min(1, Math.max(0, record.confidence))
        : typeof record.confidenceScore === 'number'
          ? Math.min(1, Math.max(0, record.confidenceScore))
          : 0.75;

    const result: Step3Result = {
      intent,
      confidence,
      keywords: inferredKeywords.length > 0 ? inferredKeywords : ['功能', '模块', '业务逻辑'],
      referenceUrls: Array.isArray(record.referenceUrls) ? toStringArray(record.referenceUrls) : undefined,
      customizationRequirement: typeof record.customizationRequirement === 'string' ? record.customizationRequirement : undefined,
      modules
    };

    return { step: 3, data: result };
  }

  if (step === 4) {
    const techStack = isRecord(record.techStack) ? record.techStack : record;
    const frontendNames = toStringArray(techStack.frontend).length > 0
      ? toStringArray(techStack.frontend)
      : [toDisplayString(techStack.platform, ''), toDisplayString(techStack.uiFramework, '')].filter(Boolean);

    const backendNames = [
      ...toStringArray(techStack.backend),
      ...toStringArray(techStack.database),
      toDisplayString(techStack.auth, ''),
      toDisplayString(techStack.storage, '')
    ].filter(Boolean);

    const frontend = frontendNames.map((name) => ({ name } as const));
    const backend = backendNames.map((name) => ({ name } as const));

    const reasoning =
      toDisplayString(techStack.reason, '').trim() ||
      toDisplayString(record.reason, '').trim() ||
      '（未提供技术选型理由）';

    const result: Step4Result = {
      frontend: frontend.map(t => ({ name: t.name })),
      backend: backend.map(t => ({ name: t.name })),
      architecturePatterns: toStringArray(techStack.architecturePatterns),
      thirdPartyServices: [],
      reasoning
    };

    return { step: 4, data: result };
  }

  // step === 5
  const complexity = isRecord(record.complexity) ? record.complexity : record;

  const level = toDisplayString(record.complexityLevel ?? complexity.level, '').toUpperCase();
  const estimatedDays = getNumber(record, 'estimatedDays', getNumber(complexity, 'estimatedDays', 10), { min: 0 });
  getNumber(record, 'estimatedLines', getNumber(complexity, 'estimatedLines', 1000), { min: 0 });

  const scoreFromLevel = (value: string): number | null => {
    if (value === 'SIMPLE') return 3;
    if (value === 'MEDIUM') return 6;
    if (value === 'COMPLEX') return 9;
    return null;
  };

  const rawScore = record.complexityScore;
  const parsedScore =
    typeof rawScore === 'number'
      ? rawScore
      : typeof rawScore === 'string'
        ? Number(rawScore)
        : null;

  const complexityScore =
    parsedScore != null && !Number.isNaN(parsedScore)
      ? Math.min(10, Math.max(1, parsedScore))
      : (scoreFromLevel(level) ?? (estimatedDays >= 14 ? 8 : estimatedDays >= 7 ? 6 : 4));

  const risks = Array.isArray(record.risks)
    ? record.risks
        .filter(isRecord)
        .map((r) => {
          const normalizedLevel: Risk['level'] =
            typeof r.level === 'string' && (r.level === 'HIGH' || r.level === 'MEDIUM' || r.level === 'LOW')
              ? r.level
              : 'MEDIUM';

          const normalizedCategory: Risk['category'] =
            typeof r.category === 'string' && (
              r.category === 'PERFORMANCE' ||
              r.category === 'SECURITY' ||
              r.category === 'SCALABILITY' ||
              r.category === 'COMPLEXITY' ||
              r.category === 'OTHER'
            )
              ? r.category
              : 'OTHER';

          return {
            level: normalizedLevel,
            category: normalizedCategory,
            description: toDisplayString(r.description, '').trim()
          };
        })
        .filter(r => r.description)
    : normalizeRisks(record.riskFactors ?? complexity.riskFactors);

  const featureCountFromStep3 = (() => {
    const step3 = previous?.[3];
    if (step3 && step3.step === 3) return step3.data.modules.length;
    return 0;
  })();

  const estimatedWeeks = estimatedDays > 0 ? `${Math.max(1, Math.round(estimatedDays / 5))}周` : '1周';
  const teamSize = complexityScore >= 8 ? '3-5人' : complexityScore >= 5 ? '2-3人' : '1-2人';

  const mitigations = Array.isArray(record.mitigations) ? toStringArray(record.mitigations) : [];

  const result: Step5Result = {
    complexityScore,
    complexityBreakdown: buildComplexityBreakdown(complexityScore),
    risks,
    estimatedWorkload: {
      featureCount: featureCountFromStep3,
      estimatedWeeks,
      teamSize
    },
    mitigations
  };

  return { step: 5, data: result };
}
