/**
 * 平台配置表单组件
 *
 * 设计理念：
 * - 每个平台有独立的配置字段
 * - 动态表单，根据选中平台显示不同配置项
 * - 表单验证，确保必填字段完整
 * - 智能默认值，减少用户输入负担
 * - 清晰的字段说明和示例
 *
 * 使用场景：
 * - PublishDialog中的配置步骤
 * - 项目设置页的平台配置
 * - 批量发布的统一配置
 */
'use client';

import React from 'react';
import { cn } from '@/lib/utils';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import { Textarea } from '@/components/ui/textarea';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Info } from 'lucide-react';
import type { PlatformType } from './publish-dialog';

/**
 * Android平台配置
 */
export interface AndroidConfig {
  packageName: string;
  appName: string;
  versionName: string;
  versionCode: number;
  minSdkVersion: number;
  targetSdkVersion: number;
}

/**
 * iOS平台配置
 */
export interface IosConfig {
  bundleId: string;
  appName: string;
  versionName: string;
  buildNumber: number;
  teamId?: string;
  minIosVersion: string;
}

/**
 * H5平台配置
 */
export interface H5Config {
  title: string;
  domain?: string;
  seoKeywords?: string;
  seoDescription?: string;
}

/**
 * 小程序平台配置
 */
export interface MiniAppConfig {
  appId: string;
  appName: string;
  platform: 'wechat' | 'alipay' | 'bytedance';
}

/**
 * 所有平台配置的联合类型
 */
export type PlatformConfigs = {
  android?: AndroidConfig;
  ios?: IosConfig;
  h5?: H5Config;
  miniapp?: MiniAppConfig;
};

/**
 * 组件Props
 */
interface PlatformConfigFormProps {
  /** 当前平台 */
  platform: PlatformType;
  /** 平台配置 */
  config: PlatformConfigs;
  /** 配置变更回调 */
  onChange: (config: PlatformConfigs) => void;
  /** 容器类名 */
  className?: string;
}

/**
 * 表单字段组件
 */
const FormField: React.FC<{
  label: string;
  description?: string;
  required?: boolean;
  children: React.ReactNode;
}> = ({ label, description, required, children }) => {
  return (
    <div className="space-y-2">
      <Label className="flex items-center gap-2">
        {label}
        {required && <Badge variant="destructive" className="text-xs px-1.5 py-0">必填</Badge>}
      </Label>
      {children}
      {description && (
        <div className="flex items-start gap-1.5 text-xs text-muted-foreground">
          <Info className="h-3 w-3 mt-0.5 flex-shrink-0" />
          <span>{description}</span>
        </div>
      )}
    </div>
  );
};

/**
 * Android配置表单
 */
const AndroidConfigForm: React.FC<{
  config?: AndroidConfig;
  onChange: (config: AndroidConfig) => void;
}> = ({ config, onChange }) => {
  const defaultConfig: AndroidConfig = {
    packageName: config?.packageName || 'com.ingenio.app',
    appName: config?.appName || 'IngenioApp',
    versionName: config?.versionName || '1.0.0',
    versionCode: config?.versionCode || 1,
    minSdkVersion: config?.minSdkVersion || 24,
    targetSdkVersion: config?.targetSdkVersion || 34,
  };

  const updateField = <K extends keyof AndroidConfig>(
    field: K,
    value: AndroidConfig[K]
  ) => {
    onChange({ ...defaultConfig, [field]: value });
  };

  return (
    <div className="space-y-4">
      <FormField
        label="包名"
        description="唯一标识应用的包名，格式：com.company.app"
        required
      >
        <Input
          value={defaultConfig.packageName}
          onChange={(e) => updateField('packageName', e.target.value)}
          placeholder="com.ingenio.app"
        />
      </FormField>

      <FormField label="应用名称" description="显示在设备上的应用名称" required>
        <Input
          value={defaultConfig.appName}
          onChange={(e) => updateField('appName', e.target.value)}
          placeholder="IngenioApp"
        />
      </FormField>

      <div className="grid grid-cols-2 gap-4">
        <FormField label="版本号" description="如：1.0.0" required>
          <Input
            value={defaultConfig.versionName}
            onChange={(e) => updateField('versionName', e.target.value)}
            placeholder="1.0.0"
          />
        </FormField>

        <FormField label="版本代码" description="整数递增，如：1" required>
          <Input
            type="number"
            value={defaultConfig.versionCode}
            onChange={(e) => updateField('versionCode', parseInt(e.target.value) || 1)}
            placeholder="1"
          />
        </FormField>
      </div>

      <div className="grid grid-cols-2 gap-4">
        <FormField label="最小SDK版本" description="Android 7.0 = API 24">
          <Select
            value={defaultConfig.minSdkVersion.toString()}
            onValueChange={(value) => updateField('minSdkVersion', parseInt(value))}
          >
            <SelectTrigger>
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="24">API 24 (Android 7.0)</SelectItem>
              <SelectItem value="26">API 26 (Android 8.0)</SelectItem>
              <SelectItem value="28">API 28 (Android 9.0)</SelectItem>
              <SelectItem value="29">API 29 (Android 10)</SelectItem>
              <SelectItem value="30">API 30 (Android 11)</SelectItem>
            </SelectContent>
          </Select>
        </FormField>

        <FormField label="目标SDK版本" description="Android 14 = API 34">
          <Select
            value={defaultConfig.targetSdkVersion.toString()}
            onValueChange={(value) => updateField('targetSdkVersion', parseInt(value))}
          >
            <SelectTrigger>
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="33">API 33 (Android 13)</SelectItem>
              <SelectItem value="34">API 34 (Android 14)</SelectItem>
              <SelectItem value="35">API 35 (Android 15)</SelectItem>
            </SelectContent>
          </Select>
        </FormField>
      </div>
    </div>
  );
};

/**
 * iOS配置表单
 */
const IosConfigForm: React.FC<{
  config?: IosConfig;
  onChange: (config: IosConfig) => void;
}> = ({ config, onChange }) => {
  const defaultConfig: IosConfig = {
    bundleId: config?.bundleId || 'com.ingenio.app',
    appName: config?.appName || 'IngenioApp',
    versionName: config?.versionName || '1.0.0',
    buildNumber: config?.buildNumber || 1,
    teamId: config?.teamId || '',
    minIosVersion: config?.minIosVersion || '15.0',
  };

  const updateField = <K extends keyof IosConfig>(
    field: K,
    value: IosConfig[K]
  ) => {
    onChange({ ...defaultConfig, [field]: value });
  };

  return (
    <div className="space-y-4">
      <FormField
        label="Bundle ID"
        description="唯一标识应用的Bundle ID，格式：com.company.app"
        required
      >
        <Input
          value={defaultConfig.bundleId}
          onChange={(e) => updateField('bundleId', e.target.value)}
          placeholder="com.ingenio.app"
        />
      </FormField>

      <FormField label="应用名称" description="显示在设备上的应用名称" required>
        <Input
          value={defaultConfig.appName}
          onChange={(e) => updateField('appName', e.target.value)}
          placeholder="IngenioApp"
        />
      </FormField>

      <div className="grid grid-cols-2 gap-4">
        <FormField label="版本号" description="如：1.0.0" required>
          <Input
            value={defaultConfig.versionName}
            onChange={(e) => updateField('versionName', e.target.value)}
            placeholder="1.0.0"
          />
        </FormField>

        <FormField label="Build Number" description="整数递增，如：1" required>
          <Input
            type="number"
            value={defaultConfig.buildNumber}
            onChange={(e) => updateField('buildNumber', parseInt(e.target.value) || 1)}
            placeholder="1"
          />
        </FormField>
      </div>

      <FormField label="Team ID" description="Apple Developer Team ID（选填）">
        <Input
          value={defaultConfig.teamId}
          onChange={(e) => updateField('teamId', e.target.value)}
          placeholder="XXXXXXXXXX"
        />
      </FormField>

      <FormField label="最低支持版本" description="最低支持的iOS版本">
        <Select
          value={defaultConfig.minIosVersion}
          onValueChange={(value) => updateField('minIosVersion', value)}
        >
          <SelectTrigger>
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="13.0">iOS 13.0</SelectItem>
            <SelectItem value="14.0">iOS 14.0</SelectItem>
            <SelectItem value="15.0">iOS 15.0</SelectItem>
            <SelectItem value="16.0">iOS 16.0</SelectItem>
            <SelectItem value="17.0">iOS 17.0</SelectItem>
          </SelectContent>
        </Select>
      </FormField>
    </div>
  );
};

/**
 * H5配置表单
 */
const H5ConfigForm: React.FC<{
  config?: H5Config;
  onChange: (config: H5Config) => void;
}> = ({ config, onChange }) => {
  const defaultConfig: H5Config = {
    title: config?.title || 'IngenioApp',
    domain: config?.domain || '',
    seoKeywords: config?.seoKeywords || '',
    seoDescription: config?.seoDescription || '',
  };

  const updateField = <K extends keyof H5Config>(field: K, value: H5Config[K]) => {
    onChange({ ...defaultConfig, [field]: value });
  };

  return (
    <div className="space-y-4">
      <FormField label="应用标题" description="浏览器标签页显示的标题" required>
        <Input
          value={defaultConfig.title}
          onChange={(e) => updateField('title', e.target.value)}
          placeholder="IngenioApp"
        />
      </FormField>

      <FormField label="域名" description="部署后的访问域名（选填）">
        <Input
          value={defaultConfig.domain}
          onChange={(e) => updateField('domain', e.target.value)}
          placeholder="https://app.ingenio.dev"
        />
      </FormField>

      <FormField label="SEO关键词" description="搜索引擎优化关键词，逗号分隔">
        <Input
          value={defaultConfig.seoKeywords}
          onChange={(e) => updateField('seoKeywords', e.target.value)}
          placeholder="应用生成, AI开发, 低代码"
        />
      </FormField>

      <FormField label="SEO描述" description="搜索结果中显示的描述">
        <Textarea
          value={defaultConfig.seoDescription}
          onChange={(e) => updateField('seoDescription', e.target.value)}
          placeholder="使用AI快速生成跨平台应用"
          rows={3}
        />
      </FormField>
    </div>
  );
};

/**
 * 小程序配置表单
 */
const MiniAppConfigForm: React.FC<{
  config?: MiniAppConfig;
  onChange: (config: MiniAppConfig) => void;
}> = ({ config, onChange }) => {
  const defaultConfig: MiniAppConfig = {
    appId: config?.appId || '',
    appName: config?.appName || 'IngenioApp',
    platform: config?.platform || 'wechat',
  };

  const updateField = <K extends keyof MiniAppConfig>(
    field: K,
    value: MiniAppConfig[K]
  ) => {
    onChange({ ...defaultConfig, [field]: value });
  };

  return (
    <div className="space-y-4">
      <FormField label="小程序平台" description="选择目标小程序平台" required>
        <Select
          value={defaultConfig.platform}
          onValueChange={(value) =>
            updateField('platform', value as MiniAppConfig['platform'])
          }
        >
          <SelectTrigger>
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="wechat">微信小程序</SelectItem>
            <SelectItem value="alipay">支付宝小程序</SelectItem>
            <SelectItem value="bytedance">抖音小程序</SelectItem>
          </SelectContent>
        </Select>
      </FormField>

      <FormField
        label="AppID"
        description="小程序的唯一标识符，从平台获取"
        required
      >
        <Input
          value={defaultConfig.appId}
          onChange={(e) => updateField('appId', e.target.value)}
          placeholder="wx1234567890abcdef"
        />
      </FormField>

      <FormField label="小程序名称" description="显示在小程序中的名称" required>
        <Input
          value={defaultConfig.appName}
          onChange={(e) => updateField('appName', e.target.value)}
          placeholder="IngenioApp"
        />
      </FormField>
    </div>
  );
};

/**
 * 平台配置表单组件
 */
export const PlatformConfigForm: React.FC<PlatformConfigFormProps> = ({
  platform,
  config,
  onChange,
  className,
}) => {
  const renderConfigForm = () => {
    switch (platform) {
      case 'android':
        return (
          <AndroidConfigForm
            config={config.android}
            onChange={(androidConfig) => onChange({ ...config, android: androidConfig })}
          />
        );
      case 'ios':
        return (
          <IosConfigForm
            config={config.ios}
            onChange={(iosConfig) => onChange({ ...config, ios: iosConfig })}
          />
        );
      case 'h5':
        return (
          <H5ConfigForm
            config={config.h5}
            onChange={(h5Config) => onChange({ ...config, h5: h5Config })}
          />
        );
      case 'miniapp':
        return (
          <MiniAppConfigForm
            config={config.miniapp}
            onChange={(miniappConfig) => onChange({ ...config, miniapp: miniappConfig })}
          />
        );
      default:
        return null;
    }
  };

  const platformNames: Record<PlatformType, string> = {
    android: 'Android',
    ios: 'iOS',
    h5: 'H5 网页',
    miniapp: '小程序',
  };

  return (
    <Card className={cn('', className)}>
      <CardHeader>
        <CardTitle>{platformNames[platform]} 配置</CardTitle>
        <CardDescription>
          配置 {platformNames[platform]} 平台的发布参数
        </CardDescription>
      </CardHeader>
      <CardContent>{renderConfigForm()}</CardContent>
    </Card>
  );
};
