/**
 * AnalysisProgressPanel 步骤修改交互测试
 *
 * 覆盖点：
 * - 点击“修改”会弹出输入框，阻止空反馈提交
 * - 输入修改建议后，会携带(step, feedback)调用 onModifyStep
 *
 * 为什么：
 * - 修复“修改时报参数错误”：此前 SmartWizard 传空 feedback，后端 @NotBlank 校验失败只返回“参数错误”
 */

import React from 'react';
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent, waitFor, within } from '@testing-library/react';
import { LanguageProvider } from '@/contexts/LanguageContext';
import { AnalysisProgressPanel } from '@/components/analysis/AnalysisProgressPanel';
import type { AnalysisProgressMessage } from '@/hooks/use-analysis-sse';

describe('AnalysisProgressPanel', () => {
  it('步骤修改应收集反馈并调用 onModifyStep', async () => {
    const onModifyStep = vi.fn().mockResolvedValue(undefined);

    const messages: AnalysisProgressMessage[] = [
      {
        step: 1,
        stepName: '需求语义解析',
        description: '已完成',
        status: 'COMPLETED',
        progress: 20,
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
            onModifyStep={onModifyStep}
          />
        </div>
      </LanguageProvider>
    );

    // 等待进入“等待确认”UI，展示 StepResultDisplay
    await waitFor(() => {
      expect(screen.getByText('需求语义解析结果')).toBeInTheDocument();
    });

    fireEvent.click(screen.getByRole('button', { name: '修改此步骤' }));

    const dialog = screen.getByRole('dialog');
    expect(within(dialog).getByText(/修改步骤：/)).toBeInTheDocument();

    const submitButton = screen.getByRole('button', { name: '提交修改' });
    expect(submitButton).toBeDisabled();

    const textarea = screen.getByPlaceholderText(/补充\/修正「.+」/);
    fireEvent.change(textarea, { target: { value: '把实体补充为 User，并增加动作“评论审核”' } });

    expect(submitButton).not.toBeDisabled();
    fireEvent.click(submitButton);

    await waitFor(() => {
      expect(onModifyStep).toHaveBeenCalledTimes(1);
      expect(onModifyStep).toHaveBeenCalledWith(1, '把实体补充为 User，并增加动作“评论审核”');
    });
  });
});
