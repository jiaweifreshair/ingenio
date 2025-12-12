/**
 * WebSocket Agent状态Hook
 * 提供Agent实时状态更新的Hook接口
 *
 * 修复记录 v1.3 (2025-11-10):
 * - 修复getWebSocketManager调用，传入wsUrl参数
 * - 删除未使用的agentType变量
 * - 修复TokenUsage字段名称映射
 * - 删除未使用的msg变量
 * - 修复wsManagerRef类型定义，使用null初始值
 */
'use client';

import { useState, useEffect, useCallback, useRef } from 'react';
import {
  getWebSocketManager,
  WebSocketManager,
  WSMessageType,
  type AgentStatusChangedMessage,
  type AgentProgressUpdatedMessage,
  type GenerationStepChangedMessage,
  type WSMessage
} from '@/lib/websocket/manager';
import { AgentExecutionStatus, AgentState, AgentType } from '@/types/wizard';

/**
 * Agent状态Hook返回值
 */
interface UseWSAgentStatusReturn {
  /** Agent状态列表 */
  agents: AgentExecutionStatus[];
  /** 当前生成步骤 */
  currentStep: string;
  /** 总体进度 */
  progress: number;
  /** 是否连接中 */
  isConnected: boolean;
  /** 错误信息 */
  error?: string;
  /** 发送消息 */
  sendMessage: (type: WSMessageType, data?: unknown) => void;
  /** 重连WebSocket */
  reconnect: () => Promise<void>;
}

/**
 * WebSocket Agent状态Hook
 */
export function useWSAgentStatus(
  sessionId: string,
  wsUrl: string
): UseWSAgentStatusReturn {
  const [agents, setAgents] = useState<AgentExecutionStatus[]>([
    {
      id: 'plan-agent',
      type: AgentType.PLAN,
      name: '需求分析',
      status: AgentState.PENDING,
      progress: 0,
    },
    {
      id: 'execute-agent',
      type: AgentType.EXECUTE,
      name: 'AppSpec生成',
      status: AgentState.PENDING,
      progress: 0,
    },
    {
      id: 'validate-agent',
      type: AgentType.VALIDATE,
      name: '质量验证',
      status: AgentState.PENDING,
      progress: 0,
    },
  ]);

  const [currentStep, setCurrentStep] = useState<string>('idle');
  const [progress, setProgress] = useState<number>(0);
  const [isConnected, setIsConnected] = useState<boolean>(false);
  const [error, setError] = useState<string>();

  // 修复：使用null初始值明确类型
  const wsManagerRef = useRef<WebSocketManager | null>(null);
  const sessionIdRef = useRef<string>(sessionId);

  // 初始化WebSocket连接
  useEffect(() => {
    // 修复：getWebSocketManager需要传入wsUrl参数
    const wsManager = getWebSocketManager(wsUrl);
    wsManagerRef.current = wsManager;
    sessionIdRef.current = sessionId;

    // 连接WebSocket
    const connectWS = async () => {
      try {
        setIsConnected(false);
        setError(undefined);
        await wsManager.connect(sessionId);
        setIsConnected(true);
      } catch (err) {
        console.error('❌ Failed to connect WebSocket:', err);
        setError(err instanceof Error ? err.message : '连接失败');
        setIsConnected(false);
      }
    };

    connectWS();

    // 清理函数
    return () => {
      wsManager.disconnect();
    };
  }, [sessionId, wsUrl]);

  // 注册消息处理器
  useEffect(() => {
    if (!wsManagerRef.current || !isConnected) return;

    const wsManager = wsManagerRef.current;

    // Agent状态变化处理
    const handleAgentStatusChanged = (message: WSMessage) => {
      const statusMessage = message as AgentStatusChangedMessage;
      // 修复：删除未使用的agentType变量，直接解构需要的字段
      const { agentId, status, progress: newProgress, message: msg, currentTask } = statusMessage.data!;

      setAgents(prev => prev.map(agent => {
        if (agent.id === agentId) {
          const updatedAgent = {
            ...agent,
            status: status as AgentState,
            progress: newProgress || 0,
            message: msg,
            currentTask,
            // 状态变化时更新时间戳
            ...(status === AgentState.RUNNING && !agent.startTime && { startTime: new Date() }),
            ...(status === AgentState.COMPLETED && !agent.endTime && {
              endTime: new Date(),
              duration: agent.startTime ? new Date().getTime() - agent.startTime.getTime() : undefined
            }),
          };
          return updatedAgent;
        }
        return agent;
      }));
    };

    // Agent进度更新处理
    const handleAgentProgressUpdated = (message: WSMessage) => {
      const progressMessage = message as AgentProgressUpdatedMessage;
      const { agentId, progress: newProgress, message: msg, metrics } = progressMessage.data!;

      setAgents(prev => prev.map(agent => {
        if (agent.id === agentId) {
          return {
            ...agent,
            progress: newProgress,
            message: msg,
            // 修复：正确映射TokenUsage字段名称
            metrics: metrics ? {
              tokenUsage: {
                inputTokens: metrics.tokenUsage.input,
                outputTokens: metrics.tokenUsage.output,
                totalTokens: metrics.tokenUsage.total,
                estimatedCost: metrics.tokenUsage.total * 0.000001, // 假设每Token成本
              },
              apiCalls: agent.metrics?.apiCalls || 1,
              avgResponseTime: agent.metrics?.avgResponseTime || 0,
              successRate: agent.metrics?.successRate || 100,
            } : agent.metrics,
          };
        }
        return agent;
      }));
    };

    // 生成步骤变化处理
    const handleGenerationStepChanged = (message: WSMessage) => {
      const stepMessage = message as GenerationStepChangedMessage;
      // 修复：删除未使用的msg变量
      const { step, progress: newProgress } = stepMessage.data!;

      setCurrentStep(step);
      setProgress(newProgress);
    };

    // 注册事件监听器
    const unsubscribe1 = wsManager.onMessage(WSMessageType.AGENT_STATUS_CHANGED, handleAgentStatusChanged);
    const unsubscribe2 = wsManager.onMessage(WSMessageType.AGENT_PROGRESS_UPDATED, handleAgentProgressUpdated);
    const unsubscribe3 = wsManager.onMessage(WSMessageType.GENERATION_STEP_CHANGED, handleGenerationStepChanged);

    // 连接状态监听
    const handleConnect = () => {
      setIsConnected(true);
      setError(undefined);
    };

    const handleDisconnect = () => {
      setIsConnected(false);
    };

    const unsubscribe4 = wsManager.onMessage(WSMessageType.CONNECT, handleConnect);
    const unsubscribe5 = wsManager.onMessage(WSMessageType.DISCONNECT, handleDisconnect);

    return () => {
      unsubscribe1();
      unsubscribe2();
      unsubscribe3();
      unsubscribe4();
      unsubscribe5();
    };
  }, [isConnected]);

  // 发送消息函数
  const sendMessage = useCallback((type: WSMessageType, data?: unknown) => {
    if (wsManagerRef.current && wsManagerRef.current.isConnected()) {
      wsManagerRef.current.sendMessage({ type, data });
    } else {
      console.warn('⚠️ Cannot send message: WebSocket not connected');
    }
  }, []);

  // 重连函数
  const reconnect = useCallback(async () => {
    if (wsManagerRef.current && sessionIdRef.current) {
      try {
        await wsManagerRef.current.connect(sessionIdRef.current);
        setIsConnected(true);
        setError(undefined);
      } catch (err) {
        console.error('❌ Failed to reconnect WebSocket:', err);
        setError(err instanceof Error ? err.message : '重连失败');
        setIsConnected(false);
      }
    }
  }, []);

  return {
    agents,
    currentStep,
    progress,
    isConnected,
    error,
    sendMessage,
    reconnect,
  };
}
