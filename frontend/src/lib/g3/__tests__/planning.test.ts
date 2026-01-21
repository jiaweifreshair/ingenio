/**
 * G3 规划文件解析工具测试
 */

import { describe, it, expect } from 'vitest';
import { parseTaskPlanSummary } from '@/lib/g3/planning';

describe('parseTaskPlanSummary', () => {
  it('应能解析当前阶段/进度/状态', () => {
    const content = [
      '# 任务计划: Demo',
      '',
      '## 当前状态',
      '**当前阶段**: 阶段2 - 代码生成',
      '**进度**: 55%',
      '**状态**: 进行中',
      '',
    ].join('\n');

    const summary = parseTaskPlanSummary(content);
    expect(summary.currentPhase).toBe('阶段2 - 代码生成');
    expect(summary.progressPercent).toBe(55);
    expect(summary.statusText).toBe('进行中');
  });

  it('应能解析已完成阶段勾选', () => {
    const content = [
      '## 阶段',
      '- [x] 阶段1: 架构设计 (Architect)',
      '- [ ] 阶段2: Entity生成',
      '- [x] 阶段3: Mapper生成',
      '- [x] 阶段7: 编译验证',
      '',
    ].join('\n');

    const summary = parseTaskPlanSummary(content);
    expect(summary.completedPhases).toEqual([1, 3, 7]);
  });

  it('空内容应返回全空摘要', () => {
    const summary = parseTaskPlanSummary('');
    expect(summary.currentPhase).toBeNull();
    expect(summary.progressPercent).toBeNull();
    expect(summary.statusText).toBeNull();
    expect(summary.completedPhases).toEqual([]);
  });
});

