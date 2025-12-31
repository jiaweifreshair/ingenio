/**
 * G3 引擎内部类型定义
 *
 * 这些类型用于 G3 Orchestrator 的内部实现。
 * 与 @/types/g3.ts 中的 API 层类型分开维护。
 *
 * @module lib/g3/types
 * @author Ingenio Team
 */

export type AgentRole = 'ARCHITECT' | 'PLAYER' | 'COACH';

/**
 * 架构师输出的 JSON 结构
 */
export interface ArchitectOutput {
  modules: Array<{
    moduleName: string;
    description: string;
    entities: Array<{
      name: string;
      description: string;
      fields: Array<{ name: string; type: string; comment: string }>;
      apis: Array<{ method: string; path: string; summary: string }>;
    }>;
  }>;
  dependencies: string[];
}

/**
 * G3 任务状态
 */
export interface G3Task {
  id: string;
  requirement: string; // 用户原始需求
  status: 'PLANNING' | 'CODING' | 'TESTING' | 'COMPLETED' | 'FAILED';
  rounds: number; // 当前博弈轮次
  maxRounds: number; // 最大允许轮次 (防止死循环)
  artifacts: {
    designJson?: ArchitectOutput; // 架构师产出
    codeFiles: Record<string, string>; // 蓝方产出 (文件名 -> 内容)
    testFiles: Record<string, string>; // 红方产出
    logs: string[]; // 执行日志
  };
}

/**
 * G3 日志条目
 */
export interface G3LogEntry {
  timestamp: number;
  role: AgentRole | 'SYSTEM';
  step: string;
  content: string;
  level: 'INFO' | 'WARN' | 'ERROR' | 'SUCCESS';
}
