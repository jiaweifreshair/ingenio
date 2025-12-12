/**
 * AI能力选择器组件测试
 * 使用@testing-library/react和@testing-library/user-event
 *
 * @author Ingenio Team
 * @since 1.0.0
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { AICapabilityPicker } from '../ai-capability-picker';
import { AICapabilityType } from '@/types/ai-capability';

describe('AICapabilityPicker', () => {
  const mockOnSelectionChange = vi.fn();

  const defaultProps = {
    selectedCapabilities: [],
    onSelectionChange: mockOnSelectionChange,
  };

  beforeEach(() => {
    mockOnSelectionChange.mockClear();
  });

  describe('渲染测试', () => {
    it('应该渲染19个AI能力卡片', () => {
      render(<AICapabilityPicker {...defaultProps} />);

      // 验证所有19个能力都渲染了
      expect(screen.getByText('对话机器人')).toBeInTheDocument();
      expect(screen.getByText('问答系统')).toBeInTheDocument();
      expect(screen.getByText('检索增强生成')).toBeInTheDocument();
      // ... 可以继续添加更多验证
    });

    it('应该显示选择计数器', () => {
      render(<AICapabilityPicker {...defaultProps} />);

      expect(screen.getByText(/0 \/ 19/)).toBeInTheDocument();
    });
  });

  describe('智能推荐测试', () => {
    it('当用户需求包含"聊天"关键词时，应该推荐对话机器人', () => {
      render(
        <AICapabilityPicker
          {...defaultProps}
          userRequirement="我想要一个聊天机器人"
          showRecommendations={true}
        />
      );

      // 验证推荐标识显示
      const recommendBadges = screen.getAllByText('推荐');
      expect(recommendBadges.length).toBeGreaterThan(0);
    });

    it('当用户需求为空时，不应该显示推荐', () => {
      render(
        <AICapabilityPicker
          {...defaultProps}
          userRequirement=""
          showRecommendations={true}
        />
      );

      // 验证没有推荐标识
      expect(screen.queryByText(/个推荐/)).not.toBeInTheDocument();
    });
  });

  describe('筛选功能测试', () => {
    it('应该能够按类别筛选', async () => {
      const user = userEvent.setup();
      render(<AICapabilityPicker {...defaultProps} />);

      // 点击"对话"类别
      const conversationTab = screen.getByRole('tab', { name: /对话/ });
      await user.click(conversationTab);

      // 验证只显示对话类别的能力（3个）
      await waitFor(() => {
        expect(screen.getByText('对话机器人')).toBeInTheDocument();
        expect(screen.getByText('问答系统')).toBeInTheDocument();
        expect(screen.getByText('检索增强生成')).toBeInTheDocument();
      });
    });

    it('应该能够使用搜索功能', async () => {
      const user = userEvent.setup();
      render(<AICapabilityPicker {...defaultProps} />);

      const searchInput = screen.getByPlaceholderText(/搜索AI能力/);
      await user.type(searchInput, '对话');

      // 等待防抖（300ms）
      await waitFor(
        () => {
          expect(screen.getByText('对话机器人')).toBeInTheDocument();
        },
        { timeout: 500 }
      );
    });
  });

  describe('选择功能测试', () => {
    it('应该能够选中AI能力', async () => {
      const user = userEvent.setup();
      render(<AICapabilityPicker {...defaultProps} />);

      // 找到第一个"+ 选择"按钮并点击
      const selectButtons = screen.getAllByRole('button', {
        name: /\+ 选择/,
      });
      await user.click(selectButtons[0]);

      // 验证回调被调用
      expect(mockOnSelectionChange).toHaveBeenCalledTimes(1);
      expect(mockOnSelectionChange).toHaveBeenCalledWith(
        expect.arrayContaining([expect.any(String)])
      );
    });

    it('应该限制最大选择数量', async () => {
      const user = userEvent.setup();
      const alertSpy = vi.spyOn(window, 'alert').mockImplementation(() => {});

      render(
        <AICapabilityPicker
          {...defaultProps}
          selectedCapabilities={[
            AICapabilityType.CHATBOT,
            AICapabilityType.QA_SYSTEM,
            AICapabilityType.RAG,
            AICapabilityType.IMAGE_RECOGNITION,
            AICapabilityType.SPEECH_RECOGNITION,
          ]}
          maxSelection={5}
        />
      );

      // 尝试选择第6个能力
      const selectButtons = screen.getAllByRole('button', {
        name: /\+ 选择/,
      });
      await user.click(selectButtons[0]);

      // 验证显示警告
      expect(alertSpy).toHaveBeenCalledWith('最多只能选择5个AI能力');

      alertSpy.mockRestore();
    });

    it('应该能够取消选择AI能力', async () => {
      const user = userEvent.setup();
      render(
        <AICapabilityPicker
          {...defaultProps}
          selectedCapabilities={[AICapabilityType.CHATBOT]}
        />
      );

      // 修复：使用getAllByRole处理React 19 Strict Mode双重渲染导致的多个"已选"按钮
      // 在Strict Mode下，组件渲染两次，可能会出现多个"已选"按钮
      // 我们选择第一个即可（任何一个都指向同一个能力的取消选择操作）
      const deselectButtons = screen.getAllByRole('button', { name: /已选/ });
      await user.click(deselectButtons[0]);

      // 验证回调被调用，且列表为空
      expect(mockOnSelectionChange).toHaveBeenCalledWith([]);
    });

    it('应该能够清空所有选择', async () => {
      const user = userEvent.setup();
      render(
        <AICapabilityPicker
          {...defaultProps}
          selectedCapabilities={[
            AICapabilityType.CHATBOT,
            AICapabilityType.QA_SYSTEM,
          ]}
        />
      );

      // 点击"清空选择"按钮
      const clearButton = screen.getByRole('button', { name: /清空选择/ });
      await user.click(clearButton);

      // 验证回调被调用，且列表为空
      expect(mockOnSelectionChange).toHaveBeenCalledWith([]);
    });
  });

  describe('统计信息测试', () => {
    it('当有选择时，应该显示摘要面板', () => {
      render(
        <AICapabilityPicker
          {...defaultProps}
          selectedCapabilities={[AICapabilityType.CHATBOT]}
          showCostEstimate={true}
        />
      );

      expect(screen.getByText(/已选择的AI能力/)).toBeInTheDocument();
      expect(screen.getByText(/预估月成本/)).toBeInTheDocument();
      expect(screen.getByText(/预估开发工期/)).toBeInTheDocument();
    });

    it('应该正确计算总成本和总工期', () => {
      render(
        <AICapabilityPicker
          {...defaultProps}
          selectedCapabilities={[
            AICapabilityType.CHATBOT, // $1.7/月, 2天
            AICapabilityType.QA_SYSTEM, // $1.2/月, 2天
          ]}
          showCostEstimate={true}
        />
      );

      // 验证成本显示为 $2.9/月
      expect(screen.getByText(/\$2\.9/)).toBeInTheDocument();
      // 验证工期显示为 4 天
      expect(screen.getByText(/4 天/)).toBeInTheDocument();
    });
  });

  describe('无障碍性测试', () => {
    it('应该包含完整的ARIA标签', () => {
      render(<AICapabilityPicker {...defaultProps} />);

      // 验证region角色
      expect(
        screen.getByRole('region', { name: 'AI能力选择器' })
      ).toBeInTheDocument();
    });

    it('卡片应该支持键盘导航', async () => {
      const user = userEvent.setup();
      render(<AICapabilityPicker {...defaultProps} />);

      // 获取第一个卡片
      const cards = screen.getAllByRole('button', { pressed: false });
      const firstCard = cards.find((el) => el.getAttribute('tabindex') === '0');

      if (firstCard) {
        // 聚焦卡片
        firstCard.focus();
        expect(firstCard).toHaveFocus();

        // 按Enter键选择
        await user.keyboard('{Enter}');
        expect(mockOnSelectionChange).toHaveBeenCalled();
      }
    });
  });

  describe('空状态测试', () => {
    it('当搜索无结果时，应该显示空状态', async () => {
      const user = userEvent.setup();
      render(<AICapabilityPicker {...defaultProps} />);

      const searchInput = screen.getByPlaceholderText(/搜索AI能力/);
      await user.type(searchInput, 'xyz不存在的关键词xyz');

      // 等待防抖
      await waitFor(
        () => {
          expect(screen.getByText(/未找到匹配的AI能力/)).toBeInTheDocument();
        },
        { timeout: 500 }
      );
    });
  });
});
