/**
 * G3 规划文件类型定义
 *
 * 说明：
 * - 对齐后端 `G3PlanningFileEntity` / `G3PlanningController` 的返回结构
 * - 用于前端“G3 控制台”展示 task_plan/notes/context 三文件内容
 */

/** 规划文件类型（Manus 三文件模式） */
export type G3PlanningFileType = 'task_plan' | 'notes' | 'context';

/** 单个规划文件实体（数据库持久化） */
export interface G3PlanningFileEntity {
  /** 文件ID */
  id: string;
  /** 所属任务ID */
  jobId: string;
  /** 文件类型 */
  fileType: G3PlanningFileType;
  /** Markdown 内容 */
  content: string;
  /** 版本号（由后端触发器自动递增） */
  version: number;
  /** 最后更新者：system/architect/coder/coach/user */
  lastUpdatedBy: string | null;
  /** 创建时间（ISO 字符串） */
  createdAt: string;
  /** 更新时间（ISO 字符串） */
  updatedAt: string;
}

/** 规划文件 Map：key 为文件类型 */
export type G3PlanningFilesMap = Partial<Record<G3PlanningFileType, G3PlanningFileEntity>> &
  Record<string, G3PlanningFileEntity | undefined>;

