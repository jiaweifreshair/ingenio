/**
 * 移动端设备边框组件
 *
 * 设计理念：
 * - 精确还原真实设备外观（iPhone、Android）
 * - 支持多种设备型号切换
 * - 包含设备特有UI元素（刘海、圆角、Home Indicator、状态栏）
 * - 响应式缩放，适应不同屏幕尺寸
 * - 优雅的3D阴影和光泽效果
 *
 * 使用场景：
 * - 应用预览页面的移动端预览
 * - 营销展示页面的设备模拟
 * - 截图工具的设备边框
 */
'use client';

import React from 'react';
import { cn } from '@/lib/utils';

/**
 * 设备类型
 */
export type DeviceType =
  | 'iphone-14-pro'
  | 'iphone-se'
  | 'iphone-14-pro-max'
  | 'pixel-7'
  | 'galaxy-s23'
  | 'pixel-7-pro';

/**
 * 设备配置接口
 */
export interface DeviceConfig {
  /** 设备名称 */
  name: string;
  /** 设备宽度（px） */
  width: number;
  /** 设备高度（px） */
  height: number;
  /** 边框宽度（px） */
  bezelWidth: number;
  /** 是否有刘海 */
  hasNotch: boolean;
  /** 刘海宽度（px） */
  notchWidth?: number;
  /** 刘海高度（px） */
  notchHeight?: number;
  /** 是否有Home Indicator（iPhone底部横条） */
  hasHomeIndicator: boolean;
  /** 圆角半径（px） */
  borderRadius: number;
  /** 设备颜色 */
  color: string;
  /** 屏幕圆角半径（px） */
  screenBorderRadius: number;
}

/**
 * 预定义设备配置
 */
export const DEVICE_CONFIGS: Record<DeviceType, DeviceConfig> = {
  'iphone-14-pro': {
    name: 'iPhone 14 Pro',
    width: 393,
    height: 852,
    bezelWidth: 12,
    hasNotch: true,
    notchWidth: 126,
    notchHeight: 30,
    hasHomeIndicator: true,
    borderRadius: 55,
    color: '#1f1f1f',
    screenBorderRadius: 47,
  },
  'iphone-14-pro-max': {
    name: 'iPhone 14 Pro Max',
    width: 430,
    height: 932,
    bezelWidth: 12,
    hasNotch: true,
    notchWidth: 126,
    notchHeight: 30,
    hasHomeIndicator: true,
    borderRadius: 55,
    color: '#1f1f1f',
    screenBorderRadius: 47,
  },
  'iphone-se': {
    name: 'iPhone SE',
    width: 375,
    height: 667,
    bezelWidth: 10,
    hasNotch: false,
    hasHomeIndicator: false,
    borderRadius: 38,
    color: '#2c2c2c',
    screenBorderRadius: 0,
  },
  'pixel-7': {
    name: 'Google Pixel 7',
    width: 412,
    height: 915,
    bezelWidth: 8,
    hasNotch: true,
    notchWidth: 80,
    notchHeight: 24,
    hasHomeIndicator: false,
    borderRadius: 32,
    color: '#5f6368',
    screenBorderRadius: 24,
  },
  'pixel-7-pro': {
    name: 'Google Pixel 7 Pro',
    width: 412,
    height: 892,
    bezelWidth: 8,
    hasNotch: true,
    notchWidth: 80,
    notchHeight: 24,
    hasHomeIndicator: false,
    borderRadius: 32,
    color: '#5f6368',
    screenBorderRadius: 24,
  },
  'galaxy-s23': {
    name: 'Samsung Galaxy S23',
    width: 360,
    height: 780,
    bezelWidth: 8,
    hasNotch: true,
    notchWidth: 60,
    notchHeight: 20,
    hasHomeIndicator: false,
    borderRadius: 28,
    color: '#2d2d2d',
    screenBorderRadius: 20,
  },
};

/**
 * 组件Props
 */
interface DeviceFrameProps {
  /** 设备类型 */
  device: DeviceType;
  /** 子内容（应用界面） */
  children: React.ReactNode;
  /** 缩放比例（0-1） */
  scale?: number;
  /** 是否显示设备名称 */
  showDeviceName?: boolean;
  /** 容器类名 */
  className?: string;
  /** 是否显示状态栏 */
  showStatusBar?: boolean;
}

/**
 * iPhone刘海组件
 */
const IPhoneNotch: React.FC<{ config: DeviceConfig }> = ({ config }) => {
  const { notchWidth = 126, notchHeight = 30 } = config;

  return (
    <div
      className="absolute left-1/2 top-0 -translate-x-1/2 z-20"
      style={{
        width: `${notchWidth}px`,
        height: `${notchHeight}px`,
      }}
    >
      {/* 刘海背景 */}
      <div
        className="absolute inset-0 bg-black rounded-b-2xl"
        style={{
          boxShadow: '0 2px 4px rgba(0,0,0,0.3)',
        }}
      />

      {/* 听筒 */}
      <div className="absolute left-1/2 top-2 -translate-x-1/2 w-16 h-1.5 bg-gray-800 rounded-full" />

      {/* 前置摄像头 */}
      <div className="absolute left-4 top-2 w-3 h-3 bg-gray-900 rounded-full border border-gray-700" />
    </div>
  );
};

/**
 * Android打孔屏组件
 */
const AndroidPunchHole: React.FC<{ config: DeviceConfig }> = ({ config }) => {
  const { notchWidth = 80, notchHeight = 24 } = config;

  return (
    <div
      className="absolute left-1/2 top-3 -translate-x-1/2 z-20"
      style={{
        width: `${notchWidth}px`,
        height: `${notchHeight}px`,
      }}
    >
      {/* 打孔摄像头 */}
      <div className="absolute left-1/2 top-1/2 -translate-x-1/2 -translate-y-1/2 w-5 h-5 bg-black rounded-full border-2 border-gray-800" />
    </div>
  );
};

/**
 * Home Indicator组件（iPhone底部横条）
 */
const HomeIndicator: React.FC = () => {
  return (
    <div className="absolute bottom-2 left-1/2 -translate-x-1/2 z-20">
      <div className="w-32 h-1 bg-white/30 rounded-full" />
    </div>
  );
};

/**
 * 状态栏组件
 */
const StatusBar: React.FC<{ device: DeviceType }> = () => {

  return (
    <div className="absolute top-0 left-0 right-0 h-11 flex items-center justify-between px-6 text-white text-xs font-medium z-10">
      {/* 左侧：时间 */}
      <div className="flex items-center gap-1">
        <span>{new Date().toLocaleTimeString('en-US', { hour: 'numeric', minute: '2-digit', hour12: true })}</span>
      </div>

      {/* 右侧：信号、WiFi、电池 */}
      <div className="flex items-center gap-1.5">
        {/* 信号强度 */}
        <div className="flex items-end gap-px">
          <div className="w-0.5 h-1 bg-white rounded-full" />
          <div className="w-0.5 h-1.5 bg-white rounded-full" />
          <div className="w-0.5 h-2 bg-white rounded-full" />
          <div className="w-0.5 h-2.5 bg-white rounded-full" />
        </div>

        {/* WiFi */}
        <svg className="w-3.5 h-3.5" viewBox="0 0 24 24" fill="white">
          <path d="M1 9l2 2c4.97-4.97 13.03-4.97 18 0l2-2C16.93 2.93 7.08 2.93 1 9zm8 8l3 3 3-3c-1.65-1.66-4.34-1.66-6 0zm-4-4l2 2c2.76-2.76 7.24-2.76 10 0l2-2C15.14 9.14 8.87 9.14 5 13z" />
        </svg>

        {/* 电池 */}
        <div className="flex items-center">
          <div className="w-5 h-2.5 border border-white rounded-sm relative">
            <div className="absolute inset-0.5 bg-white rounded-sm" />
          </div>
          <div className="w-px h-1.5 bg-white ml-px rounded-r-sm" />
        </div>
      </div>
    </div>
  );
};

/**
 * 设备边框组件
 */
export const DeviceFrame: React.FC<DeviceFrameProps> = ({
  device,
  children,
  scale = 0.8,
  showDeviceName = false,
  className,
  showStatusBar = true,
}) => {
  const config = DEVICE_CONFIGS[device];
  const totalWidth = config.width + config.bezelWidth * 2;
  const totalHeight = config.height + config.bezelWidth * 2;

  return (
    <div className={cn('flex flex-col items-center gap-4', className)}>
      {/* 设备名称 */}
      {showDeviceName && (
        <div className="text-sm font-medium text-muted-foreground">
          {config.name}
        </div>
      )}

      {/* 设备外壳 */}
      <div
        className="relative"
        style={{
          width: `${totalWidth * scale}px`,
          height: `${totalHeight * scale}px`,
          transform: `scale(${scale})`,
          transformOrigin: 'top center',
        }}
      >
        {/* 设备边框（外壳） */}
        <div
          className="absolute inset-0 shadow-2xl"
          style={{
            backgroundColor: config.color,
            borderRadius: `${config.borderRadius}px`,
            boxShadow: `
              0 20px 60px -15px rgba(0, 0, 0, 0.5),
              0 10px 30px -10px rgba(0, 0, 0, 0.4),
              inset 0 1px 0 rgba(255, 255, 255, 0.1),
              inset 0 -1px 0 rgba(0, 0, 0, 0.5)
            `,
          }}
        >
          {/* 侧边按钮（音量键、电源键） */}
          {device.startsWith('iphone') && (
            <>
              {/* 音量键 */}
              <div
                className="absolute -left-1 top-32 w-1 h-16 rounded-l-sm"
                style={{
                  backgroundColor: config.color,
                  boxShadow: 'inset 1px 0 2px rgba(0, 0, 0, 0.5)',
                }}
              />
              {/* 静音键 */}
              <div
                className="absolute -left-1 top-28 w-1 h-6 rounded-l-sm"
                style={{
                  backgroundColor: config.color,
                  boxShadow: 'inset 1px 0 2px rgba(0, 0, 0, 0.5)',
                }}
              />
              {/* 电源键 */}
              <div
                className="absolute -right-1 top-40 w-1 h-20 rounded-r-sm"
                style={{
                  backgroundColor: config.color,
                  boxShadow: 'inset -1px 0 2px rgba(0, 0, 0, 0.5)',
                }}
              />
            </>
          )}

          {/* 屏幕区域 */}
          <div
            className="absolute bg-white overflow-hidden"
            style={{
              top: `${config.bezelWidth}px`,
              left: `${config.bezelWidth}px`,
              right: `${config.bezelWidth}px`,
              bottom: `${config.bezelWidth}px`,
              borderRadius: config.screenBorderRadius > 0
                ? `${config.screenBorderRadius}px`
                : '0',
              boxShadow: 'inset 0 0 0 1px rgba(0, 0, 0, 0.1)',
            }}
          >
            {/* 状态栏 */}
            {showStatusBar && <StatusBar device={device} />}

            {/* 刘海/打孔屏 */}
            {config.hasNotch && (
              device.startsWith('iphone') ? (
                <IPhoneNotch config={config} />
              ) : (
                <AndroidPunchHole config={config} />
              )
            )}

            {/* 应用内容 */}
            <div className="h-full w-full overflow-auto">
              {children}
            </div>

            {/* Home Indicator */}
            {config.hasHomeIndicator && <HomeIndicator />}
          </div>
        </div>

        {/* 设备光泽效果 */}
        <div
          className="absolute inset-0 pointer-events-none"
          style={{
            borderRadius: `${config.borderRadius}px`,
            background: 'linear-gradient(135deg, rgba(255,255,255,0.15) 0%, transparent 50%)',
          }}
        />
      </div>
    </div>
  );
};

/**
 * 设备选择器组件
 */
interface DeviceSelectorProps {
  /** 当前选中的设备 */
  selectedDevice: DeviceType;
  /** 设备切换回调 */
  onDeviceChange: (device: DeviceType) => void;
  /** 容器类名 */
  className?: string;
}

export const DeviceSelector: React.FC<DeviceSelectorProps> = ({
  selectedDevice,
  onDeviceChange,
  className,
}) => {
  const iPhoneDevices: DeviceType[] = ['iphone-14-pro', 'iphone-14-pro-max', 'iphone-se'];
  const androidDevices: DeviceType[] = ['pixel-7', 'pixel-7-pro', 'galaxy-s23'];

  return (
    <div className={cn('space-y-4', className)}>
      {/* iPhone设备 */}
      <div>
        <h4 className="text-sm font-medium text-muted-foreground mb-2">iPhone</h4>
        <div className="flex flex-wrap gap-2">
          {iPhoneDevices.map((device) => (
            <button
              key={device}
              onClick={() => onDeviceChange(device)}
              className={cn(
                'px-3 py-2 rounded-lg text-xs font-medium transition-colors',
                selectedDevice === device
                  ? 'bg-primary text-primary-foreground'
                  : 'bg-muted hover:bg-muted/80'
              )}
            >
              {DEVICE_CONFIGS[device].name}
            </button>
          ))}
        </div>
      </div>

      {/* Android设备 */}
      <div>
        <h4 className="text-sm font-medium text-muted-foreground mb-2">Android</h4>
        <div className="flex flex-wrap gap-2">
          {androidDevices.map((device) => (
            <button
              key={device}
              onClick={() => onDeviceChange(device)}
              className={cn(
                'px-3 py-2 rounded-lg text-xs font-medium transition-colors',
                selectedDevice === device
                  ? 'bg-primary text-primary-foreground'
                  : 'bg-muted hover:bg-muted/80'
              )}
            >
              {DEVICE_CONFIGS[device].name}
            </button>
          ))}
        </div>
      </div>
    </div>
  );
};
