/**
 * useFormState Hook单元测试
 *
 * 测试场景：
 * - 初始状态正确
 * - setRequirement更新状态
 * - setSelectedModel更新状态
 * - setSelectedStyle更新状态
 * - clearTemplate清除模板
 * - resetAll重置所有状态
 * - 状态独立性
 *
 * @author Ingenio Team
 * @since V2.0 Day 6 Phase 6.4
 */

import { describe, it, expect } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { useFormState } from '../use-form-state';
import { UNIAIX_MODELS } from '@/lib/api/uniaix';

describe('useFormState', () => {
  /**
   * 测试1: 初始状态正确
   * 验证Hook初始化时所有状态的默认值正确
   */
  it('应该返回正确的初始状态', () => {
    const { result } = renderHook(() => useFormState());

    expect(result.current.requirement).toBe('');
    expect(result.current.selectedModel).toBe(UNIAIX_MODELS.QWEN_MAX);
    expect(result.current.selectedStyle).toBeNull();
    expect(result.current.loading).toBe(false);
    expect(result.current.showSuccess).toBe(false);
    expect(result.current.showAnalysis).toBe(false);
    expect(result.current.currentPhase).toBe('idle');
    expect(result.current.loadedTemplate).toBeNull();
  });

  /**
   * 测试2: setRequirement更新状态
   * 验证setRequirement正确更新需求描述
   */
  it('应该正确更新requirement状态', () => {
    const { result } = renderHook(() => useFormState());

    act(() => {
      result.current.setRequirement('创建一个电商平台');
    });

    expect(result.current.requirement).toBe('创建一个电商平台');
  });

  /**
   * 测试3: setSelectedModel更新状态
   * 验证setSelectedModel正确更新AI模型
   */
  it('应该正确更新selectedModel状态', () => {
    const { result } = renderHook(() => useFormState());

    act(() => {
      result.current.setSelectedModel(UNIAIX_MODELS.QWEN_TURBO);
    });

    expect(result.current.selectedModel).toBe(UNIAIX_MODELS.QWEN_TURBO);
  });

  /**
   * 测试4: setSelectedStyle更新状态
   * 验证setSelectedStyle正确更新设计风格
   */
  it('应该正确更新selectedStyle状态', () => {
    const { result } = renderHook(() => useFormState());

    act(() => {
      result.current.setSelectedStyle('现代极简');
    });

    expect(result.current.selectedStyle).toBe('现代极简');
  });

  /**
   * 测试5: setLoading更新状态
   * 验证setLoading正确更新加载状态
   */
  it('应该正确更新loading状态', () => {
    const { result } = renderHook(() => useFormState());

    act(() => {
      result.current.setLoading(true);
    });

    expect(result.current.loading).toBe(true);
  });

  /**
   * 测试6: clearTemplate清除模板
   * 验证clearTemplate清除需求和模板状态
   */
  it('应该在调用clearTemplate时清除需求和模板', () => {
    const { result } = renderHook(() => useFormState());

    // 先设置一些状态
    act(() => {
      result.current.setRequirement('测试需求');
      result.current.setLoadedTemplate({ id: 'test', name: '测试模板' });
    });

    expect(result.current.requirement).toBe('测试需求');
    expect(result.current.loadedTemplate).not.toBeNull();

    // 清除模板
    act(() => {
      result.current.clearTemplate();
    });

    expect(result.current.requirement).toBe('');
    expect(result.current.loadedTemplate).toBeNull();
  });

  /**
   * 测试7: resetAll重置所有状态
   * 验证resetAll将所有状态重置为初始值
   */
  it('应该在调用resetAll时重置所有状态', () => {
    const { result } = renderHook(() => useFormState());

    // 先修改所有状态
    act(() => {
      result.current.setRequirement('测试需求');
      result.current.setSelectedModel(UNIAIX_MODELS.QWEN_TURBO);
      result.current.setSelectedStyle('现代极简');
      result.current.setLoading(true);
      result.current.setShowSuccess(true);
      result.current.setShowAnalysis(true);
      result.current.setCurrentPhase('analyzing');
      result.current.setLoadedTemplate({ id: 'test', name: '测试模板' });
    });

    // 验证状态已修改
    expect(result.current.requirement).toBe('测试需求');
    expect(result.current.loading).toBe(true);

    // 重置所有状态
    act(() => {
      result.current.resetAll();
    });

    // 验证所有状态已重置
    expect(result.current.requirement).toBe('');
    expect(result.current.selectedModel).toBe(UNIAIX_MODELS.QWEN_MAX);
    expect(result.current.selectedStyle).toBeNull();
    expect(result.current.loading).toBe(false);
    expect(result.current.showSuccess).toBe(false);
    expect(result.current.showAnalysis).toBe(false);
    expect(result.current.currentPhase).toBe('idle');
    expect(result.current.loadedTemplate).toBeNull();
  });

  /**
   * 测试8: 状态独立性
   * 验证各个状态更新不会相互影响
   */
  it('应该保持状态独立性，互不影响', () => {
    const { result } = renderHook(() => useFormState());

    // 修改requirement
    act(() => {
      result.current.setRequirement('测试需求');
    });

    // 验证其他状态未受影响
    expect(result.current.requirement).toBe('测试需求');
    expect(result.current.selectedModel).toBe(UNIAIX_MODELS.QWEN_MAX);
    expect(result.current.loading).toBe(false);
    expect(result.current.currentPhase).toBe('idle');

    // 修改loading
    act(() => {
      result.current.setLoading(true);
    });

    // 验证其他状态未受影响
    expect(result.current.requirement).toBe('测试需求'); // 未变
    expect(result.current.loading).toBe(true);
    expect(result.current.currentPhase).toBe('idle'); // 未变
  });

  /**
   * 测试9: setCurrentPhase更新流程阶段
   * 验证setCurrentPhase正确更新当前阶段
   */
  it('应该正确更新currentPhase状态', () => {
    const { result } = renderHook(() => useFormState());

    act(() => {
      result.current.setCurrentPhase('analyzing');
    });

    expect(result.current.currentPhase).toBe('analyzing');

    act(() => {
      result.current.setCurrentPhase('style-selection');
    });

    expect(result.current.currentPhase).toBe('style-selection');
  });

  /**
   * 测试10: setLoadedTemplate更新模板
   * 验证setLoadedTemplate正确更新加载的模板
   */
  it('应该正确更新loadedTemplate状态', () => {
    const { result } = renderHook(() => useFormState());

    const template = { id: 'ecommerce', name: '电商平台模板' };

    act(() => {
      result.current.setLoadedTemplate(template);
    });

    expect(result.current.loadedTemplate).toEqual(template);
  });
});
