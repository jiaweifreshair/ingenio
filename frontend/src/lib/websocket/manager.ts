/**
 * WebSocket连接管理器
 * 负责与后端的WebSocket连接，处理Agent状态的实时更新
 *
 * 修复记录 v1.1 (2025-11-10):
 * - 修复sendMessage类型错误：支持传入额外字段（如sessionId）
 * - sendMessage现在接受Record<string, unknown>类型以支持动态字段
 */
'use client';

export enum WSMessageType {
  // 连接管理
  CONNECT = 'connect',
  DISCONNECT = 'disconnect',
  PING = 'ping',
  PONG = 'pong',

  // Agent状态相关
  AGENT_STARTED = 'agent_started',
  AGENT_STATUS_CHANGED = 'agent_status_changed',
  AGENT_PROGRESS_UPDATED = 'agent_progress_updated',
  AGENT_COMPLETED = 'agent_completed',
  AGENT_FAILED = 'agent_failed',

  // 生成状态相关
  GENERATION_STARTED = 'generation_started',
  GENERATION_STEP_CHANGED = 'generation_step_changed',
  GENERATION_COMPLETED = 'generation_completed',
  GENERATION_FAILED = 'generation_failed',

  // 日志相关
  LOG_ADDED = 'log_added',
}

export interface WSMessage {
  type: WSMessageType;
  messageId: string;
  timestamp: string;
  data?: unknown;
  [key: string]: unknown; // 修复：允许任意额外字段（如sessionId）
}

export interface AgentStatusChangedMessage extends WSMessage {
  type: WSMessageType.AGENT_STATUS_CHANGED;
  data: {
    agentId: string;
    agentType: 'PlanAgent' | 'ExecuteAgent' | 'ValidateAgent';
    status: 'pending' | 'running' | 'completed' | 'failed' | 'paused';
    progress: number;
    message?: string;
    currentTask?: string;
  };
}

export interface AgentProgressUpdatedMessage extends WSMessage {
  type: WSMessageType.AGENT_PROGRESS_UPDATED;
  data: {
    agentId: string;
    progress: number;
    message?: string;
    metrics?: {
      tokenUsage: {
        input: number;
        output: number;
        total: number;
      };
      duration: number;
    };
  };
}

export interface GenerationStepChangedMessage extends WSMessage {
  type: WSMessageType.GENERATION_STEP_CHANGED;
  data: {
    step: 'planning' | 'executing' | 'validating' | 'completed';
    message: string;
    progress: number;
  };
}

export type WSMessageHandler = (message: WSMessage) => void;

export class WebSocketManager {
  private ws?: WebSocket;
  private url: string;
  private handlers = new Map<WSMessageType, Set<WSMessageHandler>>();
  private reconnectAttempts = 0;
  private maxReconnectAttempts = 5;
  private reconnectDelay = 1000;
  private heartbeatInterval?: NodeJS.Timeout;
  private isConnecting = false;
  private sessionId?: string;

  constructor(url: string) {
    this.url = url;
  }

  /**
   * 连接WebSocket
   */
  async connect(sessionId: string): Promise<void> {
    if (this.isConnecting || (this.ws && this.ws.readyState === WebSocket.OPEN)) {
      return;
    }

    this.isConnecting = true;
    this.sessionId = sessionId;

    try {
      console.log(`🔌 Connecting to WebSocket: ${this.url}?sessionId=${sessionId}`);

      const wsUrl = `${this.url}?sessionId=${sessionId}`;
      this.ws = new WebSocket(wsUrl);

      await new Promise<void>((resolve, reject) => {
        const timeout = setTimeout(() => {
          reject(new Error('WebSocket connection timeout'));
        }, 10000);

        this.ws!.onopen = () => {
          clearTimeout(timeout);
          console.log('✅ WebSocket connected successfully');
          this.isConnecting = false;
          this.reconnectAttempts = 0;
          this.startHeartbeat();

          // 发送连接确认 - 修复：sessionId可以作为额外字段传递
          this.sendMessage({
            type: WSMessageType.CONNECT,
            sessionId,
            timestamp: new Date().toISOString(),
          });

          resolve();
        };

        this.ws!.onclose = (event) => {
          clearTimeout(timeout);
          console.log(`🔌 WebSocket closed: ${event.code} - ${event.reason}`);
          this.isConnecting = false;
          this.stopHeartbeat();

          // 如果不是正常关闭，尝试重连
          if (event.code !== 1000) {
            this.attemptReconnect(sessionId);
          }
        };

        this.ws!.onerror = (error) => {
          clearTimeout(timeout);
          console.error('❌ WebSocket error:', error);
          this.isConnecting = false;
          reject(new Error('WebSocket connection failed'));
        };

        this.ws!.onmessage = (event) => {
          this.handleMessage(event.data);
        };
      });
    } catch (error) {
      this.isConnecting = false;
      throw error;
    }
  }

  /**
   * 断开连接
   */
  disconnect(): void {
    console.log('🔌 Disconnecting WebSocket');
    this.stopHeartbeat();

    if (this.ws) {
      this.ws.close(1000, 'Client disconnect');
      this.ws = undefined;
    }

    this.handlers.clear();
    this.sessionId = undefined;
  }

  /**
   * 检查连接状态
   */
  isConnected(): boolean {
    return this.ws?.readyState === WebSocket.OPEN;
  }

  /**
   * 发送消息
   * 修复：接受Record<string, unknown>以支持动态字段（如sessionId）
   */
  public sendMessage(message: Partial<WSMessage> & Record<string, unknown>): void {
    if (this.isConnected()) {
      const fullMessage: WSMessage = {
        type: message.type!,
        messageId: `msg_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`,
        timestamp: new Date().toISOString(),
        data: message.data,
        ...message,
      };

      this.ws!.send(JSON.stringify(fullMessage));
    } else {
      console.warn('⚠️ Cannot send message: WebSocket not connected');
    }
  }

  /**
   * 注册消息处理器
   */
  onMessage(type: WSMessageType, handler: WSMessageHandler): () => void {
    if (!this.handlers.has(type)) {
      this.handlers.set(type, new Set());
    }

    this.handlers.get(type)!.add(handler);

    // 返回取消注册的函数
    return () => {
      const typeHandlers = this.handlers.get(type);
      if (typeHandlers) {
        typeHandlers.delete(handler);
        if (typeHandlers.size === 0) {
          this.handlers.delete(type);
        }
      }
    };
  }

  /**
   * 处理收到的消息
   */
  private handleMessage(data: string): void {
    try {
      const message: WSMessage = JSON.parse(data);
      console.log(`📨 WebSocket message: ${message.type}`, message);

      // 处理ping/pong
      if (message.type === WSMessageType.PING) {
        this.sendMessage({
          type: WSMessageType.PONG,
        });
        return;
      }

      // 分发给对应的处理器
      const typeHandlers = this.handlers.get(message.type);
      if (typeHandlers) {
        typeHandlers.forEach(handler => {
          try {
            handler(message);
          } catch (error) {
            console.error(`❌ Error handling message ${message.type}:`, error);
          }
        });
      }
    } catch (error) {
      console.error('❌ Failed to parse WebSocket message:', error);
    }
  }

  /**
   * 开始心跳
   */
  private startHeartbeat(): void {
    this.heartbeatInterval = setInterval(() => {
      if (this.isConnected()) {
        this.sendMessage({
          type: WSMessageType.PING,
        });
      } else {
        this.stopHeartbeat();
      }
    }, 30000); // 30秒心跳
  }

  /**
   * 停止心跳
   */
  private stopHeartbeat(): void {
    if (this.heartbeatInterval) {
      clearInterval(this.heartbeatInterval);
      this.heartbeatInterval = undefined;
    }
  }

  /**
   * 尝试重连
   */
  private attemptReconnect(sessionId: string): void {
    if (this.reconnectAttempts >= this.maxReconnectAttempts) {
      console.error(`❌ Max reconnection attempts reached: ${this.maxReconnectAttempts}`);
      return;
    }

    const delay = this.reconnectDelay * Math.pow(2, this.reconnectAttempts);
    this.reconnectAttempts++;

    console.log(`🔄 Attempting to reconnect WebSocket (${this.reconnectAttempts}/${this.maxReconnectAttempts}) in ${delay}ms`);

    setTimeout(async () => {
      try {
        await this.connect(sessionId);
      } catch (error) {
        console.error(`❌ Reconnection failed:`, error);
        this.attemptReconnect(sessionId);
      }
    }, delay);
  }

  /**
   * 获取连接统计信息
   */
  getConnectionStats() {
    return {
      isConnected: this.isConnected(),
      reconnectAttempts: this.reconnectAttempts,
      maxReconnectAttempts: this.maxReconnectAttempts,
      sessionId: this.sessionId,
      handlers: Object.fromEntries(
        Array.from(this.handlers.entries()).map(([type, handlers]) => [type, handlers.size])
      ),
    };
  }
}

// 全局WebSocket管理器实例
let wsManager: WebSocketManager | null = null;

export function getWebSocketManager(url?: string): WebSocketManager {
  if (!wsManager) {
    if (!url) {
      throw new Error('WebSocket URL is required for first initialization');
    }
    wsManager = new WebSocketManager(url);
  }
  return wsManager;
}

export function cleanupWebSocketManager(): void {
  if (wsManager) {
    wsManager.disconnect();
    wsManager = null;
  }
}
