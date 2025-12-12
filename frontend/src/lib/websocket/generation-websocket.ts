/**
 * 生成任务WebSocket管理器
 * 负责与后端WebSocket建立连接，接收实时状态更新
 *
 * 修复记录 v1.1 (2025-11-10):
 * - 删除重复的onError定义（Line 59和63冲突）
 * - 修复onError参数类型，统一为ErrorMessage
 * - 删除未使用的event参数（Line 190）
 * - 修正onError回调类型
 */
export interface WebSocketMessage {
  type: string;
  timestamp: number;
  [key: string]: unknown;
}

export interface AgentStatusMessage {
  type: 'agentStatus';
  agentInfo: {
    agentType: string;
    status: string;
    progress: number;
    message: string;
    timestamp: number;
  };
}

export interface TaskStatusMessage {
  type: 'taskStatus';
  taskId: string;
  status: string;
  currentAgent: string;
  progress: number;
  timestamp: number;
}

export interface ErrorMessage {
  type: 'error';
  error: string;
  timestamp: number;
}

export interface PongMessage {
  type: 'pong';
  timestamp: number;
}

export interface CancelResponseMessage {
  type: 'cancelResponse';
  cancelled: boolean;
  timestamp: number;
}

export type GenerationWebSocketMessage =
  | AgentStatusMessage
  | TaskStatusMessage
  | ErrorMessage
  | PongMessage
  | CancelResponseMessage;

export interface GenerationWebSocketOptions {
  taskId: string;
  onOpen?: () => void;
  onClose?: (event: CloseEvent) => void;
  // 修复：删除重复的onError，统一为ErrorMessage类型
  onError?: (message: ErrorMessage) => void;
  onMessage?: (message: GenerationWebSocketMessage) => void;
  onTaskStatus?: (message: TaskStatusMessage) => void;
  onAgentStatus?: (message: AgentStatusMessage) => void;
  reconnectInterval?: number;
  maxReconnectAttempts?: number;
}

export class GenerationWebSocket {
  private ws: WebSocket | null = null;
  private options: GenerationWebSocketOptions;
  private reconnectAttempts = 0;
  private reconnectTimer: NodeJS.Timeout | null = null;
  private pingTimer: NodeJS.Timeout | null = null;
  private isManualClose = false;

  constructor(options: GenerationWebSocketOptions) {
    this.options = {
      reconnectInterval: 3000,
      maxReconnectAttempts: 10,
      ...options,
    };
  }

  /**
   * 连接WebSocket
   */
  connect(): void {
    if (this.ws && this.ws.readyState === WebSocket.OPEN) {
      console.log('WebSocket已连接');
      return;
    }

    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const host = window.location.host;
    const wsUrl = `${protocol}//${host}/ws/generation/${this.options.taskId}`;

    console.log(`连接WebSocket: ${wsUrl}`);

    try {
      this.ws = new WebSocket(wsUrl);
      this.setupEventListeners();
    } catch (error) {
      console.error('WebSocket连接失败:', error);
      this.scheduleReconnect();
    }
  }

  /**
   * 断开连接
   */
  disconnect(): void {
    this.isManualClose = true;
    this.clearReconnectTimer();
    this.clearPingTimer();

    if (this.ws) {
      this.ws.close();
      this.ws = null;
    }
  }

  /**
   * 发送消息
   */
  send(message: WebSocketMessage): boolean {
    if (!this.ws || this.ws.readyState !== WebSocket.OPEN) {
      console.warn('WebSocket未连接，无法发送消息');
      return false;
    }

    try {
      this.ws.send(JSON.stringify(message));
      return true;
    } catch (error) {
      console.error('发送WebSocket消息失败:', error);
      return false;
    }
  }

  /**
   * 订阅任务状态更新
   */
  subscribe(): boolean {
    return this.send({
      type: 'subscribe',
      timestamp: Date.now(),
    });
  }

  /**
   * 取消任务
   */
  cancelTask(): boolean {
    return this.send({
      type: 'cancel',
      timestamp: Date.now(),
    });
  }

  /**
   * 发送心跳
   */
  ping(): boolean {
    return this.send({
      type: 'ping',
      timestamp: Date.now(),
    });
  }

  /**
   * 获取连接状态
   */
  get readyState(): number {
    return this.ws?.readyState ?? WebSocket.CLOSED;
  }

  /**
   * 是否已连接
   */
  get isConnected(): boolean {
    return this.ws?.readyState === WebSocket.OPEN;
  }

  /**
   * 设置事件监听器
   */
  private setupEventListeners(): void {
    if (!this.ws) return;

    // 修复：删除未使用的event参数
    this.ws.onopen = () => {
      console.log('WebSocket连接已建立');
      this.reconnectAttempts = 0;
      this.isManualClose = false;

      // 订阅任务状态
      this.subscribe();

      // 启动心跳
      this.startPing();

      this.options.onOpen?.();
    };

    this.ws.onclose = (event) => {
      console.log('WebSocket连接已关闭:', event.code, event.reason);
      this.clearPingTimer();

      this.options.onClose?.(event);

      // 如果不是手动关闭，尝试重连
      if (!this.isManualClose) {
        this.scheduleReconnect();
      }
    };

    // 修复：WebSocket的onerror接收Event类型，但我们的业务层onError接收ErrorMessage
    // 所以这里需要分开处理
    this.ws.onerror = (event) => {
      console.error('WebSocket连接错误:', event);
      // 将WebSocket错误包装为ErrorMessage格式
      const errorMessage: ErrorMessage = {
        type: 'error',
        error: 'WebSocket连接错误',
        timestamp: Date.now(),
      };
      this.options.onError?.(errorMessage);
    };

    this.ws.onmessage = (event) => {
      try {
        const message: GenerationWebSocketMessage = JSON.parse(event.data);
        this.handleMessage(message);
      } catch (error) {
        console.error('解析WebSocket消息失败:', error, event.data);
      }
    };
  }

  /**
   * 处理接收到的消息
   */
  private handleMessage(message: GenerationWebSocketMessage): void {
    console.debug('收到WebSocket消息:', message);

    // 通用消息回调
    this.options.onMessage?.(message);

    // 特定类型消息回调
    switch (message.type) {
      case 'taskStatus':
        this.options.onTaskStatus?.(message as TaskStatusMessage);
        break;

      case 'agentStatus':
        this.options.onAgentStatus?.(message as AgentStatusMessage);
        break;

      case 'error':
        // 修复：类型保护，确保是ErrorMessage类型
        this.options.onError?.(message as ErrorMessage);
        break;

      case 'pong':
        // 心跳响应，不需要特殊处理
        break;

      case 'cancelResponse':
        // 取消响应，可以在这里处理
        break;

      default:
        console.warn('未知消息类型:', (message as { type?: string }).type);
    }
  }

  /**
   * 安排重连
   */
  private scheduleReconnect(): void {
    if (this.reconnectAttempts >= this.options.maxReconnectAttempts!) {
      console.error('WebSocket重连次数超过限制，停止重连');
      return;
    }

    this.clearReconnectTimer();

    const delay = this.options.reconnectInterval! * Math.pow(2, this.reconnectAttempts);
    console.log(`${delay}ms后尝试第${this.reconnectAttempts + 1}次重连`);

    this.reconnectTimer = setTimeout(() => {
      this.reconnectAttempts++;
      this.connect();
    }, delay);
  }

  /**
   * 清除重连定时器
   */
  private clearReconnectTimer(): void {
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer);
      this.reconnectTimer = null;
    }
  }

  /**
   * 启动心跳
   */
  private startPing(): void {
    this.clearPingTimer();

    this.pingTimer = setInterval(() => {
      if (this.isConnected) {
        this.ping();
      } else {
        this.clearPingTimer();
      }
    }, 30000); // 每30秒发送一次心跳
  }

  /**
   * 清除心跳定时器
   */
  private clearPingTimer(): void {
    if (this.pingTimer) {
      clearInterval(this.pingTimer);
      this.pingTimer = null;
    }
  }
}
