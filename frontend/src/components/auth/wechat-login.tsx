/**
 * 微信扫码登录组件
 * 展示微信登录二维码并轮询扫码状态
 *
 * 功能：
 * - 生成微信登录二维码
 * - 轮询检查扫码状态（每2秒）
 * - 扫码成功后自动登录
 * - 二维码过期自动刷新
 *
 * @author Ingenio Team
 * @since 1.0.0
 */

'use client';

import { useEffect, useState, useRef } from 'react';
import Image from 'next/image';
import { Button } from '@/components/ui/button';
import { Skeleton } from '@/components/ui/skeleton';
import { useToast } from '@/hooks/use-toast';
import {
  generateWechatQrcode,
  checkWechatScanStatus,
  type ScanStatus,
} from '@/lib/api/auth';

/**
 * 组件Props
 */
interface WechatLoginProps {
  /** 登录成功回调 */
  onSuccess: () => void;
  /** 是否禁用（未勾选用户协议时禁用） */
  disabled?: boolean;
}

/**
 * 微信扫码登录组件
 */
export function WechatLogin({
  onSuccess,
  disabled = false,
}: WechatLoginProps): React.ReactElement {
  const { toast } = useToast();

  // 二维码状态
  const [qrcodeUrl, setQrcodeUrl] = useState<string | null>(null);

  // 扫码状态
  const [scanStatus, setScanStatus] = useState<ScanStatus>('pending');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // 轮询定时器引用
  const pollIntervalRef = useRef<NodeJS.Timeout | null>(null);
  const expiryTimerRef = useRef<NodeJS.Timeout | null>(null);

  /**
   * 生成二维码
   */
  const fetchQrcode = async () => {
    if (disabled) {
      toast({
        title: '请先同意用户协议',
        description: '勾选下方"同意用户协议"后方可登录',
        variant: 'destructive',
      });
      return;
    }

    setLoading(true);
    setError(null);

    try {
      const data = await generateWechatQrcode();
      setQrcodeUrl(data.qrcodeUrl);
      setScanStatus('pending');

      // 开始轮询
      startPolling(data.sceneStr);

      // 设置过期定时器
      if (expiryTimerRef.current) {
        clearTimeout(expiryTimerRef.current);
      }
      expiryTimerRef.current = setTimeout(() => {
        setScanStatus('expired');
        stopPolling();
        toast({
          title: '二维码已过期',
          description: '请点击刷新按钮重新生成',
          variant: 'default',
        });
      }, data.expiresIn * 1000);
    } catch (err) {
      const errorMessage =
        err instanceof Error ? err.message : '生成二维码失败';
      setError(errorMessage);
      toast({
        title: '生成二维码失败',
        description: errorMessage,
        variant: 'destructive',
      });
    } finally {
      setLoading(false);
    }
  };

  /**
   * 开始轮询扫码状态
   */
  const startPolling = (scene: string) => {
    // 清除旧的定时器
    stopPolling();

    // 每2秒轮询一次
    pollIntervalRef.current = setInterval(async () => {
      try {
        const statusData = await checkWechatScanStatus(scene);
        setScanStatus(statusData.status);

        // 如果扫码成功，停止轮询并触发登录成功回调
        if (statusData.status === 'confirmed') {
          stopPolling();
          onSuccess();
        }

        // 如果过期，停止轮询
        if (statusData.status === 'expired') {
          stopPolling();
        }
      } catch (err) {
        console.error('轮询扫码状态失败:', err);
      }
    }, 2000);
  };

  /**
   * 停止轮询
   */
  const stopPolling = () => {
    if (pollIntervalRef.current) {
      clearInterval(pollIntervalRef.current);
      pollIntervalRef.current = null;
    }
  };

  /**
   * 刷新二维码
   */
  const handleRefresh = () => {
    fetchQrcode();
  };

  /**
   * 组件挂载时生成二维码
   */
  useEffect(() => {
    if (!disabled) {
      fetchQrcode();
    }

    // 组件卸载时清理定时器
    return () => {
      stopPolling();
      if (expiryTimerRef.current) {
        clearTimeout(expiryTimerRef.current);
      }
    };
  }, [disabled]);

  /**
   * 渲染状态提示文本
   */
  const renderStatusText = () => {
    switch (scanStatus) {
      case 'pending':
        return '请使用微信扫码登录';
      case 'scanned':
        return '已扫码，请在手机上确认';
      case 'confirmed':
        return '登录成功！正在跳转...';
      case 'expired':
        return '二维码已过期，请刷新';
      default:
        return '加载中...';
    }
  };

  /**
   * 渲染状态图标
   */
  const renderStatusIcon = () => {
    switch (scanStatus) {
      case 'scanned':
        return (
          <div className="flex h-48 w-48 items-center justify-center bg-green-100 rounded-lg">
            <svg
              className="h-24 w-24 text-green-500"
              fill="none"
              viewBox="0 0 24 24"
              stroke="currentColor"
            >
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth={2}
                d="M5 13l4 4L19 7"
              />
            </svg>
          </div>
        );
      case 'confirmed':
        return (
          <div className="flex h-48 w-48 items-center justify-center bg-blue-100 rounded-lg">
            <svg
              className="h-24 w-24 animate-spin text-blue-500"
              fill="none"
              viewBox="0 0 24 24"
            >
              <circle
                className="opacity-25"
                cx="12"
                cy="12"
                r="10"
                stroke="currentColor"
                strokeWidth="4"
              />
              <path
                className="opacity-75"
                fill="currentColor"
                d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"
              />
            </svg>
          </div>
        );
      case 'expired':
        return (
          <div className="flex h-48 w-48 items-center justify-center bg-gray-100 rounded-lg">
            <svg
              className="h-24 w-24 text-gray-400"
              fill="none"
              viewBox="0 0 24 24"
              stroke="currentColor"
            >
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth={2}
                d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z"
              />
            </svg>
          </div>
        );
      default:
        return null;
    }
  };

  return (
    <div className="flex flex-col items-center gap-4">
      {/* 二维码展示区域 */}
      <div className="relative flex h-48 w-48 items-center justify-center rounded-lg bg-white p-2">
        {loading && <Skeleton className="h-full w-full" />}

        {error && (
          <div className="flex flex-col items-center gap-2 text-center">
            <svg
              className="h-12 w-12 text-red-500"
              fill="none"
              viewBox="0 0 24 24"
              stroke="currentColor"
            >
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth={2}
                d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"
              />
            </svg>
            <p className="text-sm text-gray-600">{error}</p>
          </div>
        )}

        {!loading && !error && qrcodeUrl && scanStatus === 'pending' && (
          <Image
            src={qrcodeUrl}
            alt="微信登录二维码"
            width={192}
            height={192}
            className="rounded"
          />
        )}

        {!loading && !error && scanStatus !== 'pending' && renderStatusIcon()}

        {/* 禁用遮罩层 */}
        {disabled && (
          <div className="absolute inset-0 flex items-center justify-center rounded-lg bg-black/50">
            <p className="text-sm text-white">请先同意用户协议</p>
          </div>
        )}
      </div>

      {/* 状态提示文本 */}
      <p className="text-center text-sm text-gray-200">{renderStatusText()}</p>

      {/* 刷新按钮 */}
      {(scanStatus === 'expired' || error) && (
        <Button
          onClick={handleRefresh}
          variant="outline"
          size="sm"
          className="bg-white/10 text-white hover:bg-white/20"
          disabled={disabled}
        >
          刷新二维码
        </Button>
      )}
    </div>
  );
}
