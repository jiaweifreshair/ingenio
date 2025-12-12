/**
 * ExploreMoreCard - 探索更多功能卡片组件
 *
 * 功能：
 * - 显示推荐的后续操作
 * - 提供AI能力选择、SuperDesign、时光机版本等入口
 * - 支持自定义导航回调
 *
 * Props:
 * - appSpecId: 当前AppSpec ID
 * - onNavigate: 导航回调
 */
'use client';

import React from 'react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Sparkles, Palette, Clock } from 'lucide-react';

/**
 * ExploreMoreCard Props接口
 */
export interface ExploreMoreCardProps {
  /** 当前AppSpec ID */
  appSpecId: string;
  /** 导航回调 */
  onNavigate: (path: string) => void;
  /** 自定义类名 */
  className?: string;
}

/**
 * ExploreMoreCard组件
 */
export const ExploreMoreCard: React.FC<ExploreMoreCardProps> = ({
  appSpecId,
  onNavigate,
  className,
}) => {
  return (
    <Card
      className={`bg-gradient-to-br from-blue-50/50 via-white to-purple-50/50 ${className}`}
    >
      <CardHeader>
        <CardTitle>探索更多功能</CardTitle>
      </CardHeader>
      <CardContent>
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
          <Button
            variant="outline"
            onClick={() => onNavigate('/wizard/ai-capabilities')}
            className="flex items-center gap-3 h-auto p-4 justify-start hover:bg-purple-50/50 transition-colors"
          >
            <Sparkles className="w-6 h-6 text-purple-500 flex-shrink-0" />
            <div className="text-left">
              <div className="font-semibold mb-1">AI能力选择</div>
              <div className="text-xs text-muted-foreground">
                智能分析需求，推荐AI能力组合
              </div>
            </div>
          </Button>
          <Button
            variant="outline"
            onClick={() => onNavigate(`/superdesign/${appSpecId}`)}
            className="flex items-center gap-3 h-auto p-4 justify-start hover:bg-blue-50/50 transition-colors"
          >
            <Palette className="w-6 h-6 text-blue-500 flex-shrink-0" />
            <div className="text-left">
              <div className="font-semibold mb-1">SuperDesign</div>
              <div className="text-xs text-muted-foreground">
                AI生成3种设计风格方案
              </div>
            </div>
          </Button>
          <Button
            variant="outline"
            onClick={() => onNavigate('/dashboard')}
            className="flex items-center gap-3 h-auto p-4 justify-start hover:bg-orange-50/50 transition-colors"
          >
            <Clock className="w-6 h-6 text-orange-500 flex-shrink-0" />
            <div className="text-left">
              <div className="font-semibold mb-1">时光机版本</div>
              <div className="text-xs text-muted-foreground">
                查看版本历史，一键回溯
              </div>
            </div>
          </Button>
        </div>
      </CardContent>
    </Card>
  );
};

// 设置displayName便于调试
ExploreMoreCard.displayName = 'ExploreMoreCard';
