/**
 * G3 规划文件解析工具
 *
 * 目的：
 * - 从 task_plan.md 中提取“当前阶段/进度/状态”等摘要信息，用于控制台卡片展示
 * - 解析阶段勾选情况，用于依赖图/里程碑可视化
 */

export interface G3TaskPlanSummary {
  /** 当前阶段文本（如：阶段1 - 架构设计） */
  currentPhase: string | null;
  /** 进度百分比（0-100） */
  progressPercent: number | null;
  /** 状态文本（如：进行中/已完成/失败） */
  statusText: string | null;
  /** 已完成阶段编号集合 */
  completedPhases: number[];
}

/**
 * 解析 task_plan.md 的摘要信息（容错：字段不存在则返回 null）
 */
export function parseTaskPlanSummary(taskPlanContent: string | null | undefined): G3TaskPlanSummary {
  const content = (taskPlanContent || '').replace(/\r/g, '');
  if (!content.trim()) {
    return {
      currentPhase: null,
      progressPercent: null,
      statusText: null,
      completedPhases: [],
    };
  }

  const currentPhase =
    matchFirstGroup(content, /\*\*当前阶段\*\*:\s*(.+)\s*$/m) ??
    matchFirstGroup(content, /^\*\*当前阶段\*\*:\s*(.+)$/m);

  const progressRaw = matchFirstGroup(content, /\*\*进度\*\*:\s*(\d{1,3})%\s*$/m);
  const progressPercent =
    progressRaw != null ? clampInt(parseInt(progressRaw, 10), 0, 100) : null;

  const statusText =
    matchFirstGroup(content, /\*\*状态\*\*:\s*(.+)\s*$/m) ??
    matchFirstGroup(content, /^\*\*状态\*\*:\s*(.+)$/m);

  const completedPhases = parseCompletedPhases(content);

  return {
    currentPhase: currentPhase?.trim() || null,
    progressPercent,
    statusText: statusText?.trim() || null,
    completedPhases,
  };
}

function matchFirstGroup(content: string, regex: RegExp): string | null {
  const match = content.match(regex);
  return match && match[1] ? match[1] : null;
}

function clampInt(value: number, min: number, max: number): number {
  if (Number.isNaN(value)) return min;
  return Math.max(min, Math.min(max, value));
}

/**
 * 解析 `- [x] 阶段N:` 勾选的阶段编号
 */
function parseCompletedPhases(content: string): number[] {
  const phases = new Set<number>();
  const regex = /-\s*\[x\]\s*阶段(\d+)\s*:/gi;
  let match: RegExpExecArray | null;
  while ((match = regex.exec(content)) != null) {
    const phase = parseInt(match[1] || '', 10);
    if (!Number.isNaN(phase)) phases.add(phase);
  }
  return Array.from(phases).sort((a, b) => a - b);
}

