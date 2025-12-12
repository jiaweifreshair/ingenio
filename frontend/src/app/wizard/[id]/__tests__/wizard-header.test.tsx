/**
 * WizardHeader组件测试
 */
import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { WizardHeader } from '../components/wizard-header';

describe('WizardHeader', () => {
  it('应该正确渲染默认标题', () => {
    render(<WizardHeader status="idle" />);

    expect(screen.getByText('AppSpec 生成向导')).toBeInTheDocument();
  });

  it('应该正确渲染自定义标题', () => {
    render(<WizardHeader title="自定义标题" status="idle" />);

    expect(screen.getByText('自定义标题')).toBeInTheDocument();
  });

  it('应该正确显示待配置状态', () => {
    render(<WizardHeader status="idle" />);

    expect(screen.getByText('待配置')).toBeInTheDocument();
  });

  it('应该正确显示生成中状态', () => {
    render(<WizardHeader status="generating" />);

    expect(screen.getByText('生成中')).toBeInTheDocument();
  });

  it('应该正确显示生成完成状态', () => {
    render(<WizardHeader status="completed" />);

    expect(screen.getByText('生成完成')).toBeInTheDocument();
  });

  it('应该正确显示生成失败状态', () => {
    render(<WizardHeader status="failed" />);

    expect(screen.getByText('生成失败')).toBeInTheDocument();
  });

  it('应该在isLoading为true时显示加载指示器', () => {
    const { container } = render(
      <WizardHeader status="idle" isLoading={true} />
    );

    const loader = container.querySelector('.animate-spin');
    expect(loader).toBeInTheDocument();
  });

  it('应该在isLoading为false时不显示加载指示器', () => {
    const { container } = render(
      <WizardHeader status="idle" isLoading={false} />
    );

    const loader = container.querySelector('.animate-spin');
    expect(loader).not.toBeInTheDocument();
  });

  it('应该在isConnected为false时显示连接断开警告', () => {
    render(<WizardHeader status="idle" isConnected={false} />);

    expect(screen.getByText('连接断开，正在重试...')).toBeInTheDocument();
  });

  it('应该在isConnected为true时不显示连接断开警告', () => {
    render(<WizardHeader status="idle" isConnected={true} />);

    expect(
      screen.queryByText('连接断开，正在重试...')
    ).not.toBeInTheDocument();
  });

  it('应该在E2E测试模式下显示E2E测试模式Badge', () => {
    render(<WizardHeader status="idle" isE2ETestMode={true} />);

    expect(screen.getByText('E2E测试模式')).toBeInTheDocument();
  });
});
