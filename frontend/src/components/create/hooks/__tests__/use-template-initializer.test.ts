/**
 * useTemplateInitializer Hook单元测试
 *
 * 测试场景：
 * - URL参数templateId解析和初始化
 * - URL参数template解析和初始化
 * - sessionStorage解析和初始化
 * - 优先级验证（templateId > template > sessionStorage）
 * - Toast通知显示
 * - 输入框聚焦
 *
 * @author Ingenio Team
 * @since V2.0 Day 6 Phase 6.4
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { renderHook } from '@testing-library/react';
import { useTemplateInitializer } from '../use-template-initializer';

// Mock依赖
const mockToast = vi.fn();
vi.mock('@/hooks/use-toast', () => ({
  useToast: () => ({ toast: mockToast }),
}));

vi.mock('@/constants/templates', () => ({
  getTemplateRequirement: (id: string, name?: string) =>
    `模板需求：${name || id}的详细描述`,
}));

describe('useTemplateInitializer', () => {
  let originalLocation: Location;
  let originalSessionStorage: Storage;

  beforeEach(() => {
    // 保存原始对象
    originalLocation = window.location;
    originalSessionStorage = window.sessionStorage;

    // Mock window.location
    // delete (window as { location?: unknown }).location;
    Object.defineProperty(window, 'location', {
      value: {
        ...originalLocation,
        search: '',
      },
      writable: true,
    });

    // Mock sessionStorage
    const storage = new Map<string, string>();
    Object.defineProperty(window, 'sessionStorage', {
      value: {
        getItem: (key: string) => storage.get(key) || null,
        setItem: (key: string, value: string) => storage.set(key, value),
        removeItem: (key: string) => storage.delete(key),
        clear: () => storage.clear(),
      },
      writable: true,
    });

    // 清空mock
    mockToast.mockClear();
  });

  afterEach(() => {
    // 恢复原始对象
    Object.defineProperty(window, 'location', {
      value: originalLocation,
      writable: true,
    });
    Object.defineProperty(window, 'sessionStorage', {
      value: originalSessionStorage,
      writable: true,
    });
  });

  /**
   * 测试1: URL参数templateId优先级最高
   * 验证templateId参数被正确解析并初始化
   */
  it('应该从URL参数templateId初始化模板', () => {
    window.location.search = '?templateId=ecommerce&templateName=电商平台';

    const onRequirementChange = vi.fn();
    const onTemplateLoad = vi.fn();

    renderHook(() =>
      useTemplateInitializer({
        onRequirementChange,
        onTemplateLoad,
      })
    );

    expect(onRequirementChange).toHaveBeenCalledWith('模板需求：电商平台的详细描述');
    expect(onTemplateLoad).toHaveBeenCalledWith({
      id: 'ecommerce',
      name: '电商平台',
    });
    expect(mockToast).toHaveBeenCalledWith({
      title: '模板已加载',
      description: '已加载模板: 电商平台',
    });
  });

  /**
   * 测试2: URL参数template解析
   * 验证template参数被正确解析（优先级低于templateId）
   */
  it('应该从URL参数template初始化模板', () => {
    window.location.search = '?template=social';

    const onRequirementChange = vi.fn();
    const onTemplateLoad = vi.fn();
    const templates = [
      { id: 'social', title: '社交平台', description: '社交应用模板' },
    ];

    renderHook(() =>
      useTemplateInitializer({
        onRequirementChange,
        onTemplateLoad,
        templates,
      })
    );

    expect(onRequirementChange).toHaveBeenCalledWith('模板需求：social的详细描述');
    expect(onTemplateLoad).toHaveBeenCalledWith({
      id: 'social',
      name: '社交平台',
    });
    expect(mockToast).toHaveBeenCalledWith({
      title: '模板已应用',
      description: '已使用"社交平台"模板',
    });
  });

  /**
   * 测试3: sessionStorage解析
   * 验证sessionStorage中的需求被正确读取
   */
  it('应该从sessionStorage读取需求描述', () => {
    window.sessionStorage.setItem('requirement', '从首页输入的需求');

    const onRequirementChange = vi.fn();
    const onTemplateLoad = vi.fn();

    renderHook(() =>
      useTemplateInitializer({
        onRequirementChange,
        onTemplateLoad,
      })
    );

    expect(onRequirementChange).toHaveBeenCalledWith('从首页输入的需求');
    expect(onTemplateLoad).not.toHaveBeenCalled(); // 没有模板信息
    expect(mockToast).not.toHaveBeenCalled(); // 没有Toast
  });

  /**
   * 测试4: 优先级验证（templateId > template）
   * 验证templateId优先于template参数
   */
  it('应该优先使用templateId而不是template', () => {
    window.location.search = '?templateId=ecommerce&templateName=电商&template=social';

    const onRequirementChange = vi.fn();
    const onTemplateLoad = vi.fn();
    const templates = [
      { id: 'social', title: '社交平台', description: '社交应用模板' },
    ];

    renderHook(() =>
      useTemplateInitializer({
        onRequirementChange,
        onTemplateLoad,
        templates,
      })
    );

    // 使用templateId，而不是template
    expect(onTemplateLoad).toHaveBeenCalledWith({
      id: 'ecommerce',
      name: '电商',
    });
    expect(onTemplateLoad).not.toHaveBeenCalledWith({
      id: 'social',
      name: '社交平台',
    });
  });

  /**
   * 测试5: 优先级验证（template > sessionStorage）
   * 验证template参数优先于sessionStorage
   */
  it('应该优先使用template参数而不是sessionStorage', () => {
    window.location.search = '?template=social';
    window.sessionStorage.setItem('requirement', '从首页输入的需求');

    const onRequirementChange = vi.fn();
    const onTemplateLoad = vi.fn();
    const templates = [
      { id: 'social', title: '社交平台', description: '社交应用模板' },
    ];

    renderHook(() =>
      useTemplateInitializer({
        onRequirementChange,
        onTemplateLoad,
        templates,
      })
    );

    // 使用template参数，而不是sessionStorage
    expect(onRequirementChange).toHaveBeenCalledWith('模板需求：social的详细描述');
    expect(onTemplateLoad).toHaveBeenCalledWith({
      id: 'social',
      name: '社交平台',
    });
  });

  /**
   * 测试6: sessionStorage清除
   * 验证从sessionStorage读取后会清除该值
   */
  it('应该在读取后清除sessionStorage', () => {
    window.sessionStorage.setItem('requirement', '从首页输入的需求');

    const onRequirementChange = vi.fn();
    const onTemplateLoad = vi.fn();

    renderHook(() =>
      useTemplateInitializer({
        onRequirementChange,
        onTemplateLoad,
      })
    );

    // 验证已清除
    expect(window.sessionStorage.getItem('requirement')).toBeNull();
  });

  /**
   * 测试7: 空sessionStorage处理
   * 验证空sessionStorage时不触发初始化
   */
  it('应该忽略空的sessionStorage值', () => {
    window.sessionStorage.setItem('requirement', '   '); // 仅空格

    const onRequirementChange = vi.fn();
    const onTemplateLoad = vi.fn();

    renderHook(() =>
      useTemplateInitializer({
        onRequirementChange,
        onTemplateLoad,
      })
    );

    // 空值不触发初始化
    expect(onRequirementChange).not.toHaveBeenCalled();
  });

  /**
   * 测试8: 无初始化数据时的行为
   * 验证没有URL参数和sessionStorage时不触发回调
   */
  it('应该在没有初始化数据时不触发回调', () => {
    const onRequirementChange = vi.fn();
    const onTemplateLoad = vi.fn();

    renderHook(() =>
      useTemplateInitializer({
        onRequirementChange,
        onTemplateLoad,
      })
    );

    expect(onRequirementChange).not.toHaveBeenCalled();
    expect(onTemplateLoad).not.toHaveBeenCalled();
    expect(mockToast).not.toHaveBeenCalled();
  });
});
