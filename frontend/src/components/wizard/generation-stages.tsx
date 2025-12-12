/**
 * 生成流程5阶段可视化组件
 *
 * 设计理念：
 * - 参考"码上飞"的5阶段进度展示
 * - 苹果风格的极简设计和磨砂玻璃效果
 * - 横向流程图展示（桌面端）和竖向堆叠（移动端）
 * - 实时状态更新和流畅动画过渡
 *
 * 5个阶段：
 * 1. 需求分析 - PlanAgent分析用户需求
 * 2. 数据库设计 - DatabaseSchemaGenerator生成DDL
 * 3. 代码生成 - KotlinMultiplatformGenerator生成代码
 * 4. 测试验证 - ValidationOrchestrator执行验证
 * 5. 预览发布 - 最终打包和预览
 */
'use client';

import React, { useMemo } from 'react';
import { cn } from '@/lib/utils';
import { Progress } from '@/components/ui/progress';
import { Badge } from '@/components/ui/badge';
import {
  Database,
  Code2,
  CheckCircle2,
  Loader2,
  Rocket,
  Lightbulb,
  Clock,
  AlertCircle,
} from 'lucide-react';

/**
 * 阶段状态枚举
 */
export type StageStatus = 'pending' | 'in_progress' | 'completed' | 'failed';

/**
 * 单个阶段定义
 */
export interface GenerationStage {
  /** 阶段ID */
  id: string;
  /** 阶段名称 */
  name: string;
  /** 阶段描述 */
  description: string;
  /** 阶段图标 */
  icon: React.ReactNode;
  /** 阶段状态 */
  status: StageStatus;
  /** 阶段进度（0-100） */
  progress?: number;
  /** 阶段耗时（毫秒） */
  duration?: number;
  /** 阶段错误信息 */
  error?: string;
}

/**
 * 组件Props
 */
interface GenerationStagesProps {
  /** 当前执行到的阶段ID */
  currentStageId?: string;
  /** 总体进度（0-100） */
  overallProgress: number;
  /** 是否显示为竖向布局（移动端） */
  vertical?: boolean;
  /** 类名 */
  className?: string;
  /** 阶段点击回调 */
  onStageClick?: (stage: GenerationStage) => void;
}

/**
 * 默认5个阶段配置
 */
const DEFAULT_STAGES: Omit<GenerationStage, 'status' | 'progress'>[] = [
  {
    id: 'analysis',
    name: '需求分析',
    description: 'AI分析理解你的需求',
    icon: <Lightbulb className="h-5 w-5" />,
  },
  {
    id: 'schema',
    name: '数据库设计',
    description: '生成数据库架构',
    icon: <Database className="h-5 w-5" />,
  },
  {
    id: 'code',
    name: '代码生成',
    description: '生成Kotlin代码',
    icon: <Code2 className="h-5 w-5" />,
  },
  {
    id: 'validation',
    name: '测试验证',
    description: '编译测试和性能验证',
    icon: <CheckCircle2 className="h-5 w-5" />,
  },
  {
    id: 'preview',
    name: '预览发布',
    description: '打包并准备预览',
    icon: <Rocket className="h-5 w-5" />,
  },
];

/**
 * 根据总体进度计算各阶段状态
 */
const calculateStageStatuses = (
  overallProgress: number,
  currentStageId?: string
): GenerationStage[] => {
  const currentStageIndex = currentStageId
    ? DEFAULT_STAGES.findIndex((stage) => stage.id === currentStageId)
    : Math.floor(overallProgress / (100 / DEFAULT_STAGES.length));

  return DEFAULT_STAGES.map((stage, index) => {
    let status: StageStatus = 'pending';
    let progress = 0;

    if (index < currentStageIndex) {
      status = 'completed';
      progress = 100;
    } else if (index === currentStageIndex) {
      status = 'in_progress';
      progress = (overallProgress % (100 / DEFAULT_STAGES.length)) * DEFAULT_STAGES.length;
    }

    return {
      ...stage,
      status,
      progress,
    };
  });
};

/**
 * 获取阶段状态颜色类名
 */
const getStageColorClass = (status: StageStatus): string => {
  switch (status) {
    case 'completed':
      return 'border-green-500 bg-green-50 text-green-700';
    case 'in_progress':
      return 'border-blue-500 bg-blue-50 text-blue-700';
    case 'failed':
      return 'border-red-500 bg-red-50 text-red-700';
    default:
      return 'border-border/50 bg-card/30 text-muted-foreground';
  }
};

/**
 * 获取阶段图标颜色类名
 */
const getIconColorClass = (status: StageStatus): string => {
  switch (status) {
    case 'completed':
      return 'bg-green-500 text-white';
    case 'in_progress':
      return 'bg-blue-500 text-white';
    case 'failed':
      return 'bg-red-500 text-white';
    default:
      return 'bg-muted text-muted-foreground';
  }
};

/**
 * 阶段卡片组件
 */
const StageCard: React.FC<{
  stage: GenerationStage;
  isLast: boolean;
  vertical?: boolean;
  onClick?: () => void;
}> = ({ stage, isLast, vertical, onClick }) => {
  const isActive = stage.status === 'in_progress';
  const isCompleted = stage.status === 'completed';

  return (
    <div className="relative flex-1">
      {/* 阶段卡片 */}
      <div
        onClick={onClick}
        className={cn(
          'group relative cursor-pointer transition-all duration-300',
          vertical ? 'mb-6' : 'mx-2'
        )}
      >
        {/* 卡片主体 */}
        <div
          className={cn(
            'rounded-2xl border-2 p-4 backdrop-blur-xl transition-all duration-300',
            getStageColorClass(stage.status),
            isActive && 'shadow-lg ring-4 ring-primary/20',
            'hover:shadow-md hover:-translate-y-0.5'
          )}
        >
          {/* 图标 */}
          <div className="mb-3 flex items-center justify-between">
            <div
              className={cn(
                'flex h-10 w-10 items-center justify-center rounded-lg transition-all duration-300',
                getIconColorClass(stage.status),
                isActive && 'animate-pulse'
              )}
            >
              {isCompleted ? (
                <CheckCircle2 className="h-5 w-5" />
              ) : isActive ? (
                <Loader2 className="h-5 w-5 animate-spin" />
              ) : stage.status === 'failed' ? (
                <AlertCircle className="h-5 w-5" />
              ) : (
                stage.icon
              )}
            </div>

            {/* 状态徽章 */}
            <Badge
              variant={
                isCompleted
                  ? 'default'
                  : isActive
                    ? 'secondary'
                    : 'outline'
              }
              className="text-xs"
            >
              {isCompleted
                ? '已完成'
                : isActive
                  ? '进行中'
                  : stage.status === 'failed'
                    ? '失败'
                    : '待开始'}
            </Badge>
          </div>

          {/* 阶段信息 */}
          <h3 className="mb-1 text-sm font-semibold">{stage.name}</h3>
          <p className="text-xs opacity-80">{stage.description}</p>

          {/* 进度条 */}
          {isActive && stage.progress !== undefined && (
            <div className="mt-3">
              <Progress value={stage.progress} className="h-1.5" />
              <p className="mt-1 text-right text-xs opacity-60">
                {Math.round(stage.progress)}%
              </p>
            </div>
          )}

          {/* 耗时 */}
          {stage.duration && (
            <div className="mt-2 flex items-center gap-1 text-xs opacity-60">
              <Clock className="h-3 w-3" />
              <span>{(stage.duration / 1000).toFixed(1)}s</span>
            </div>
          )}

          {/* 错误信息 */}
          {stage.error && (
            <p className="mt-2 text-xs text-red-600">{stage.error}</p>
          )}
        </div>
      </div>

      {/* 连接线 */}
      {!isLast && (
        <div
          className={cn(
            'absolute transition-all duration-300',
            vertical
              ? 'left-5 top-20 h-6 w-0.5'
              : 'right-0 top-10 h-0.5 w-full',
            isCompleted ? 'bg-green-500' : 'bg-border/30'
          )}
        />
      )}
    </div>
  );
};

/**
 * 生成流程5阶段可视化组件
 */
export const GenerationStages: React.FC<GenerationStagesProps> = ({
  currentStageId,
  overallProgress,
  vertical = false,
  className,
  onStageClick,
}) => {
  // 计算各阶段状态
  const stages = useMemo(
    () => calculateStageStatuses(overallProgress, currentStageId),
    [overallProgress, currentStageId]
  );

  return (
    <div className={cn('w-full', className)}>
      {/* 总体进度条 */}
      <div className="mb-8 space-y-3">
        <div className="flex items-center justify-between">
          <h2 className="text-lg font-semibold">生成进度</h2>
          <span className="text-sm text-muted-foreground">
            {Math.round(overallProgress)}%
          </span>
        </div>
        <Progress value={overallProgress} className="h-2" />
      </div>

      {/* 5阶段展示 */}
      <div
        className={cn(
          vertical ? 'flex flex-col' : 'flex flex-row items-start',
          'relative'
        )}
      >
        {stages.map((stage, index) => (
          <StageCard
            key={stage.id}
            stage={stage}
            isLast={index === stages.length - 1}
            vertical={vertical}
            onClick={() => onStageClick?.(stage)}
          />
        ))}
      </div>
    </div>
  );
};
