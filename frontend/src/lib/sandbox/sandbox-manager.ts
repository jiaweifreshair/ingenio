/**
 * 沙箱管理器服务
 *
 * 负责管理代码预览沙箱的生命周期，包括：
 * - 沙箱实例的创建和销毁
 * - 代码文件的实时同步
 * - 沙箱状态的监控和重连
 * - 预览URL的管理
 *
 * 当前实现基于后端OpenLovable-CN服务，
 * 未来可扩展为E2B或其他沙箱提供商。
 *
 * @author Ingenio Team
 * @since 2.0.0
 */

import { getToken } from '@/lib/auth/token';
import type { GeneratedFile } from '@/hooks/use-code-generation-stream';
import { getApiBaseUrl } from '@/lib/api/base-url';

/**
 * 沙箱状态
 */
export type SandboxStatus =
  | 'idle'           // 空闲
  | 'creating'       // 创建中
  | 'ready'          // 就绪
  | 'syncing'        // 同步代码中
  | 'error'          // 错误
  | 'destroyed';     // 已销毁

/**
 * 沙箱实例信息
 */
export interface SandboxInstance {
  /** 沙箱ID */
  id: string;
  /** 预览URL */
  previewUrl: string;
  /** 状态 */
  status: SandboxStatus;
  /** 创建时间 */
  createdAt: number;
  /** 最后活跃时间 */
  lastActiveAt: number;
  /** 关联的AppSpec ID */
  appSpecId?: string;
  /** 错误信息 */
  error?: string;
}

/**
 * 沙箱创建选项
 */
export interface CreateSandboxOptions {
  /** 关联的AppSpec ID */
  appSpecId?: string;
  /** 初始代码文件 */
  initialFiles?: GeneratedFile[];
  /** 模板类型 */
  template?: 'react' | 'vue' | 'next' | 'vite' | 'custom';
  /** 超时时间（毫秒） */
  timeout?: number;
}

/**
 * 文件同步结果
 */
export interface SyncResult {
  success: boolean;
  syncedFiles: string[];
  failedFiles: string[];
  error?: string;
}

/**
 * 沙箱事件监听器
 */
export interface SandboxEventListeners {
  onStatusChange?: (status: SandboxStatus) => void;
  onPreviewReady?: (url: string) => void;
  onSyncComplete?: (result: SyncResult) => void;
  onError?: (error: string) => void;
}

/**
 * 沙箱管理器类
 */
class SandboxManagerClass {
  private instance: SandboxInstance | null = null;
  private listeners: SandboxEventListeners = {};
  private syncQueue: GeneratedFile[] = [];
  private isSyncing = false;
  private baseUrl: string;

  constructor() {
    this.baseUrl = getApiBaseUrl();
  }

  /**
   * 设置事件监听器
   */
  setListeners(listeners: SandboxEventListeners): void {
    this.listeners = { ...this.listeners, ...listeners };
  }

  /**
   * 获取当前沙箱实例
   */
  getInstance(): SandboxInstance | null {
    return this.instance;
  }

  /**
   * 获取当前状态
   */
  getStatus(): SandboxStatus {
    return this.instance?.status || 'idle';
  }

  /**
   * 获取预览URL
   */
  getPreviewUrl(): string | null {
    return this.instance?.previewUrl || null;
  }

  /**
   * 创建新的沙箱实例
   */
  async createSandbox(options: CreateSandboxOptions = {}): Promise<SandboxInstance> {
    const { appSpecId, initialFiles, template = 'react', timeout = 60000 } = options;

    this.updateStatus('creating');

    try {
      const token = getToken();
      const response = await fetch(`${this.baseUrl}/v1/sandbox/create`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          ...(token && { 'Authorization': `Bearer ${token}` }),
        },
        body: JSON.stringify({
          appSpecId,
          template,
          timeout,
        }),
        signal: AbortSignal.timeout(timeout),
      });

      if (!response.ok) {
        throw new Error(`创建沙箱失败: ${response.status}`);
      }

      const data = await response.json();

      if (!data.success || !data.data) {
        throw new Error(data.message || '创建沙箱失败');
      }

      this.instance = {
        id: data.data.sandboxId,
        previewUrl: data.data.previewUrl,
        status: 'ready',
        createdAt: Date.now(),
        lastActiveAt: Date.now(),
        appSpecId,
      };

      this.updateStatus('ready');
      this.listeners.onPreviewReady?.(this.instance.previewUrl);

      // 如果有初始文件，同步到沙箱
      if (initialFiles && initialFiles.length > 0) {
        await this.syncFiles(initialFiles);
      }

      return this.instance;
    } catch (error) {
      const errorMsg = error instanceof Error ? error.message : '创建沙箱失败';
      this.updateStatus('error', errorMsg);
      throw error;
    }
  }

  /**
   * 同步文件到沙箱
   */
  async syncFiles(files: GeneratedFile[]): Promise<SyncResult> {
    if (!this.instance) {
      return {
        success: false,
        syncedFiles: [],
        failedFiles: files.map(f => f.path),
        error: '沙箱未初始化',
      };
    }

    // 添加到队列
    this.syncQueue.push(...files);

    // 如果已经在同步，返回（队列会自动处理）
    if (this.isSyncing) {
      return {
        success: true,
        syncedFiles: [],
        failedFiles: [],
      };
    }

    return this.processSyncQueue();
  }

  /**
   * 处理同步队列
   */
  private async processSyncQueue(): Promise<SyncResult> {
    if (this.syncQueue.length === 0 || !this.instance) {
      return { success: true, syncedFiles: [], failedFiles: [] };
    }

    this.isSyncing = true;
    this.updateStatus('syncing');

    const syncedFiles: string[] = [];
    const failedFiles: string[] = [];

    try {
      const token = getToken();

      // 批量同步文件
      while (this.syncQueue.length > 0) {
        const batch = this.syncQueue.splice(0, 10); // 每批最多10个文件

        const response = await fetch(`${this.baseUrl}/v1/sandbox/${this.instance.id}/sync`, {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            ...(token && { 'Authorization': `Bearer ${token}` }),
          },
          body: JSON.stringify({
            files: batch.map(f => ({
              path: f.path,
              content: f.content,
              type: f.type,
            })),
          }),
        });

        if (response.ok) {
          syncedFiles.push(...batch.map(f => f.path));
        } else {
          failedFiles.push(...batch.map(f => f.path));
        }
      }

      const result: SyncResult = {
        success: failedFiles.length === 0,
        syncedFiles,
        failedFiles,
      };

      this.instance.lastActiveAt = Date.now();
      this.updateStatus('ready');
      this.listeners.onSyncComplete?.(result);

      return result;
    } catch (error) {
      const errorMsg = error instanceof Error ? error.message : '同步失败';
      this.updateStatus('error', errorMsg);

      return {
        success: false,
        syncedFiles,
        failedFiles: [...failedFiles, ...this.syncQueue.map(f => f.path)],
        error: errorMsg,
      };
    } finally {
      this.isSyncing = false;
    }
  }

  /**
   * 添加单个文件（实时更新）
   */
  async addFile(file: GeneratedFile): Promise<boolean> {
    if (!this.instance) return false;

    try {
      const token = getToken();
      const response = await fetch(`${this.baseUrl}/v1/sandbox/${this.instance.id}/file`, {
        method: 'PUT',
        headers: {
          'Content-Type': 'application/json',
          ...(token && { 'Authorization': `Bearer ${token}` }),
        },
        body: JSON.stringify({
          path: file.path,
          content: file.content,
          type: file.type,
        }),
      });

      if (response.ok) {
        this.instance.lastActiveAt = Date.now();
        return true;
      }

      return false;
    } catch {
      return false;
    }
  }

  /**
   * 刷新预览
   */
  async refreshPreview(): Promise<boolean> {
    if (!this.instance) return false;

    try {
      const token = getToken();
      const response = await fetch(`${this.baseUrl}/v1/sandbox/${this.instance.id}/refresh`, {
        method: 'POST',
        headers: {
          ...(token && { 'Authorization': `Bearer ${token}` }),
        },
      });

      return response.ok;
    } catch {
      return false;
    }
  }

  /**
   * 销毁沙箱
   */
  async destroy(): Promise<void> {
    if (!this.instance) return;

    const sandboxId = this.instance.id;

    try {
      const token = getToken();
      await fetch(`${this.baseUrl}/v1/sandbox/${sandboxId}`, {
        method: 'DELETE',
        headers: {
          ...(token && { 'Authorization': `Bearer ${token}` }),
        },
      });
    } catch {
      console.warn('销毁沙箱失败');
    } finally {
      this.instance = null;
      this.syncQueue = [];
      this.isSyncing = false;
      this.updateStatus('destroyed');
    }
  }

  /**
   * 重置管理器状态
   */
  reset(): void {
    this.instance = null;
    this.syncQueue = [];
    this.isSyncing = false;
    this.listeners = {};
  }

  /**
   * 更新状态
   */
  private updateStatus(status: SandboxStatus, error?: string): void {
    if (this.instance) {
      this.instance.status = status;
      if (error) {
        this.instance.error = error;
      }
    }

    this.listeners.onStatusChange?.(status);

    if (error) {
      this.listeners.onError?.(error);
    }
  }
}

/**
 * 沙箱管理器单例
 */
export const SandboxManager = new SandboxManagerClass();

/**
 * React Hook: 使用沙箱管理器
 */
export function useSandboxManager() {
  return SandboxManager;
}

export default SandboxManager;
