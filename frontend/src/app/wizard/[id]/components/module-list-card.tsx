/**
 * ModuleListCard - 功能模块列表卡片组件
 *
 * 功能：
 * - 显示应用的功能模块列表
 * - 显示模块的描述、复杂度、优先级
 * - 支持空状态展示
 *
 * Props:
 * - modules: 模块列表
 */
'use client';

import React from 'react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { FileText } from 'lucide-react';

/**
 * 模块定义接口
 */
export interface Module {
  name: string;
  description: string;
  priority: string;
  complexity: number;
  pages: string[];
}

/**
 * ModuleListCard Props接口
 */
export interface ModuleListCardProps {
  /** 模块列表 */
  modules: Module[];
  /** 自定义类名 */
  className?: string;
}

/**
 * 获取优先级Badge变体
 */
const getPriorityVariant = (priority: string) => {
  switch (priority) {
    case 'high':
      return 'destructive';
    case 'medium':
      return 'secondary';
    default:
      return 'outline';
  }
};

/**
 * 获取优先级显示文本
 */
const getPriorityText = (priority: string): string => {
  switch (priority) {
    case 'high':
      return '高优先级';
    case 'medium':
      return '中优先级';
    default:
      return '低优先级';
  }
};

/**
 * ModuleListCard组件
 */
export const ModuleListCard: React.FC<ModuleListCardProps> = ({
  modules,
  className,
}) => {
  if (modules.length === 0) {
    return null;
  }

  return (
    <Card className={className}>
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          <FileText className="w-4 h-4" />
          功能模块 ({modules.length})
        </CardTitle>
      </CardHeader>
      <CardContent>
        <div className="grid gap-2">
          {modules.map((module, index) => (
            <div
              key={index}
              className="flex items-center justify-between p-3 bg-muted rounded-lg"
            >
              <div>
                <p className="font-medium text-sm">{module.name}</p>
                <p className="text-xs text-muted-foreground">
                  {module.description}
                </p>
              </div>
              <div className="flex items-center gap-2">
                <Badge variant="outline" className="text-xs">
                  复杂度 {module.complexity}
                </Badge>
                <Badge
                  variant={getPriorityVariant(module.priority)}
                  className="text-xs"
                >
                  {getPriorityText(module.priority)}
                </Badge>
              </div>
            </div>
          ))}
        </div>
      </CardContent>
    </Card>
  );
};

// 设置displayName便于调试
ModuleListCard.displayName = 'ModuleListCard';
