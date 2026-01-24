/**
 * InteractionPanel 单元测试
 *
 * 覆盖点：
 * - 历史记录与对话内容可正常渲染
 * - 发送消息会触发回调
 */

import React from 'react';
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { InteractionPanel, type ChatHistoryItem } from '../interaction-panel';
import { LanguageProvider } from '@/contexts/LanguageContext';

/**
 * 默认历史记录：覆盖基础需求与迭代修改
 */
const historyItems: ChatHistoryItem[] = [
  {
    id: 'req-1',
    content: '创建一个面向校园的活动管理系统',
    timestamp: Date.now() - 60_000,
    kind: 'requirement',
  },
  {
    id: 'iter-1',
    content: '把首页按钮改为蓝色，并增加日程筛选',
    timestamp: Date.now(),
    kind: 'iteration',
  },
];

describe('InteractionPanel', () => {
  it('应展示历史记录与对话内容', () => {
    render(
      <LanguageProvider>
        <InteractionPanel
          historyItems={historyItems}
          logs={[]}
          onSendMessage={vi.fn()}
          isGenerating={false}
        />
      </LanguageProvider>
    );

    // 历史记录默认收起：先展开再断言内容
    fireEvent.click(screen.getByTitle('展开历史记录'));

    expect(screen.getByText('历史记录')).toBeInTheDocument();
    expect(screen.getByText('基础需求')).toBeInTheDocument();
    expect(screen.getByText('修改记录')).toBeInTheDocument();
    expect(screen.getAllByText('创建一个面向校园的活动管理系统').length).toBeGreaterThanOrEqual(2);
    expect(screen.getAllByText('把首页按钮改为蓝色，并增加日程筛选').length).toBeGreaterThanOrEqual(2);
  });

  it('发送消息应触发回调', () => {
    const onSendMessage = vi.fn();

    render(
      <LanguageProvider>
        <InteractionPanel
          historyItems={historyItems}
          logs={[]}
          onSendMessage={onSendMessage}
          isGenerating={false}
        />
      </LanguageProvider>
    );

    const input = screen.getByRole('textbox');
    fireEvent.change(input, { target: { value: '新增导出功能' } });
    fireEvent.click(screen.getByLabelText('发送消息'));

    expect(onSendMessage).toHaveBeenCalledTimes(1);
    expect(onSendMessage).toHaveBeenCalledWith('新增导出功能');
  });
});
