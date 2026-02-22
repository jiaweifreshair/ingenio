/**
 * AnalysisProgressPanel 已完成任务回看测试
 *
 * 覆盖点：
 * - 已完成步骤结果会持久化到 localStorage
 * - 点击左侧步骤卡片可打开“查看步骤结果”弹窗
 *
 * 为什么：
 * - 对齐“每个完成的任务需要存储并且点击可以查看”的产品诉求
 */

import React from 'react';
import { describe, it, expect } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { LanguageProvider } from '@/contexts/LanguageContext';
import { AnalysisProgressPanel } from '@/components/analysis/AnalysisProgressPanel';
import type { StepResult } from '@/types/analysis-step-results';
import type { AnalysisProgressMessage } from '@/hooks/use-analysis-sse';

describe('AnalysisProgressPanel', () => {
  it('点击完成步骤应打开回看弹窗', () => {
    const storageKey = 'test:analysis-step-results';

    const storedStep1: StepResult = {
      step: 1,
      data: {
        summary: '做一个博客系统，支持发布与评论',
        entities: ['Blog', 'Comment'],
        actions: ['创建博客', '发表评论'],
        businessScenario: '校园社团内部博客分享',
      },
    };

    localStorage.setItem(
      storageKey,
      JSON.stringify({
        version: 1,
        updatedAt: new Date().toISOString(),
        stepResults: { '1': storedStep1 },
      })
    );

    const messages: AnalysisProgressMessage[] = [
      {
        step: 1,
        stepName: '需求语义解析',
        description: 'AI已成功理解您的需求',
        status: 'COMPLETED',
        progress: 100,
        result: {
          summary: '做一个博客系统，支持发布与评论',
          entities: ['Blog', 'Comment'],
          actions: ['创建博客', '发表评论'],
          businessScenario: '校园社团内部博客分享',
        },
        timestamp: new Date().toISOString(),
      },
    ];

    render(
      <LanguageProvider>
        <div className="h-[600px]">
          <AnalysisProgressPanel
            requirement="做一个博客系统，支持发布与评论"
            messages={messages}
            isConnected={false}
            isCompleted={false}
            error={null}
            storageKey={storageKey}
          />
        </div>
      </LanguageProvider>
    );

    // Step 卡片标题使用“角色名”，而非后端 stepName
    fireEvent.click(screen.getByText('👩‍💼 产品经理 (PM)'));
    expect(screen.getByText('查看步骤结果：👩‍💼 产品经理 (PM)')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: '关闭' }));
    expect(screen.queryByText('查看步骤结果：👩‍💼 产品经理 (PM)')).not.toBeInTheDocument();
  });
});
