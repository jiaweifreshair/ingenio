/**
 * 生成任务Toast通知Hook
 *
 * 功能：
 * - 监听生成任务的关键事件（连接、断开、Agent启动/完成、错误）
 * - 自动触发Toast通知，提升用户体验
 * - 支持自定义通知样式和持续时间
 *
 * 设计理念：
 * - 关键事件必通知：连接状态变化、Agent完成、错误发生
 * - 非打扰式：成功类通知3秒自动消失，错误类需要手动关闭
 * - 信息完整：显示Agent名称、耗时、错误详情
 */
'use client';

import { useEffect, useRef } from 'react';
import { useToast } from '@/hooks/use-toast';
import {
  TaskStatusMessage,
  AgentStatusMessage,
  ErrorMessage,
} from '@/lib/websocket/generation-websocket';
import { AgentState } from '@/types/wizard';

/**
 * Hook配置选项
 */
export interface UseGenerationToastsOptions {
  /** 是否启用Toast通知 */
  enabled?: boolean;
  /** 是否显示连接状态通知 */
  showConnectionNotifications?: boolean;
  /** 是否显示Agent启动通知 */
  showAgentStartNotifications?: boolean;
  /** 是否显示Agent完成通知 */
  showAgentCompleteNotifications?: boolean;
  /** 是否显示错误通知 */
  showErrorNotifications?: boolean;
}

/**
 * 生成任务Toast通知Hook
 */
export function useGenerationToasts(
  isConnected: boolean,
  options: UseGenerationToastsOptions = {}
) {
  const {
    enabled = true,
    showConnectionNotifications = true,
    showAgentStartNotifications = false, // 默认关闭，避免过于频繁
    showAgentCompleteNotifications = true,
    showErrorNotifications = true,
  } = options;

  const { toast } = useToast();
  const prevConnectedRef = useRef(isConnected);

  /**
   * 显示连接成功通知
   */
  const showConnectedNotification = () => {
    if (!enabled || !showConnectionNotifications) return;

    toast({
      title: '✅ WebSocket已连接',
      description: '实时进度推送已启动',
      duration: 3000,
    });
  };

  /**
   * 显示连接断开通知
   */
  const showDisconnectedNotification = () => {
    if (!enabled || !showConnectionNotifications) return;

    toast({
      title: '⚠️ WebSocket连接断开',
      description: '正在尝试重新连接...',
      variant: 'destructive',
    });
  };

  /**
   * 显示Agent启动通知
   */
  const showAgentStartNotification = (agentName: string) => {
    if (!enabled || !showAgentStartNotifications) return;

    toast({
      title: `🚀 ${agentName}启动`,
      description: '正在执行任务...',
      duration: 2000,
    });
  };

  /**
   * 显示Agent完成通知
   */
  const showAgentCompleteNotification = (agentName: string, duration?: number) => {
    if (!enabled || !showAgentCompleteNotifications) return;

    const durationText = duration
      ? ` (耗时 ${(duration / 1000).toFixed(1)}s)`
      : '';

    toast({
      title: `✅ ${agentName}完成${durationText}`,
      description: '任务执行成功',
      duration: 3000,
    });
  };

  /**
   * 显示Agent失败通知
   */
  const showAgentFailedNotification = (agentName: string, errorMsg?: string) => {
    if (!enabled || !showErrorNotifications) return;

    toast({
      title: `❌ ${agentName}失败`,
      description: errorMsg || '任务执行出错，请查看日志',
      variant: 'destructive',
    });
  };

  /**
   * 显示任务状态更新通知
   */
  const showTaskStatusNotification = (message: TaskStatusMessage) => {
    if (!enabled) return;

    // 只在任务完成或失败时通知
    if (message.status === 'COMPLETED') {
      toast({
        title: '🎉 应用生成完成',
        description: '点击"预览"查看生成的应用',
        duration: 5000,
      });
    } else if (message.status === 'FAILED') {
      toast({
        title: '❌ 应用生成失败',
        description: '生成过程中出现错误',
        variant: 'destructive',
      });
    }
  };

  /**
   * 显示Agent状态更新通知
   */
  const showAgentStatusNotification = (message: AgentStatusMessage) => {
    if (!enabled) return;

    const agentName = message.agentInfo?.agentType || message.type;
    const agentStatus = message.agentInfo?.status;

    switch (agentStatus) {
      case AgentState.RUNNING:
        showAgentStartNotification(agentName);
        break;
      case AgentState.COMPLETED:
        showAgentCompleteNotification(agentName);
        break;
      case AgentState.FAILED:
        showAgentFailedNotification(agentName, message.agentInfo?.message);
        break;
      default:
        break;
    }
  };

  /**
   * 显示错误通知
   */
  const showErrorNotification = (message: ErrorMessage) => {
    if (!enabled || !showErrorNotifications) return;

    toast({
      title: '❌ 错误',
      description: message.error || '发生未知错误',
      variant: 'destructive',
    });
  };

  /**
   * 监听连接状态变化
   */
  useEffect(() => {
    // 跳过首次加载
    if (prevConnectedRef.current === isConnected) {
      prevConnectedRef.current = isConnected;
      return;
    }

    if (isConnected && !prevConnectedRef.current) {
      // 从断开到连接
      showConnectedNotification();
    } else if (!isConnected && prevConnectedRef.current) {
      // 从连接到断开
      showDisconnectedNotification();
    }

    prevConnectedRef.current = isConnected;
  }, [isConnected]);

  return {
    showTaskStatusNotification,
    showAgentStatusNotification,
    showErrorNotification,
    showConnectedNotification,
    showDisconnectedNotification,
    showAgentStartNotification,
    showAgentCompleteNotification,
    showAgentFailedNotification,
  };
}
