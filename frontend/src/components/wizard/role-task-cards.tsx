/**
 * 角色化任务详情卡片组件
 *
 * 设计理念：
 * - 参考"码上飞"的角色化任务展示
 * - 4个角色：产品经理、架构师、工程师、测试
 * - 每个角色显示当前任务、进度和状态
 * - 苹果风格的卡片设计和动画效果
 *
 * 角色与Agent映射：
 * - 产品经理 → PlanAgent（需求分析）
 * - 架构师 → DatabaseSchemaGenerator（数据库设计）
 * - 工程师 → KotlinMultiplatformGenerator（代码生成）
 * - 测试 → ValidationOrchestrator（质量验证）
 */
'use client';

import React from 'react';
import { cn } from '@/lib/utils';
import { Card, CardContent, CardHeader } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Progress } from '@/components/ui/progress';
import {
  User,
  GitBranch,
  Code2,
  TestTube2,
  Clock,
  CheckCircle2,
  Loader2,
  AlertCircle,
  Sparkles,
} from 'lucide-react';

/**
 * 角色类型枚举
 */
export type RoleType = 'product_manager' | 'architect' | 'engineer' | 'tester';

/**
 * 任务状态枚举
 */
export type TaskStatus = 'idle' | 'working' | 'completed' | 'failed';

/**
 * 单个角色任务定义
 */
export interface RoleTask {
  /** 角色类型 */
  role: RoleType;
  /** 角色名称 */
  roleName: string;
  /** 当前任务描述 */
  currentTask: string;
  /** 任务状态 */
  status: TaskStatus;
  /** 任务进度（0-100） */
  progress: number;
  /** 已完成任务数 */
  completedTasks?: number;
  /** 总任务数 */
  totalTasks?: number;
  /** 任务耗时（毫秒） */
  duration?: number;
  /** 角色图标 */
  icon: React.ReactNode;
  /** 角色颜色主题 */
  colorTheme: {
    bg: string;
    text: string;
    border: string;
    icon: string;
  };
}

/**
 * 组件Props
 */
interface RoleTaskCardsProps {
  /** 角色任务列表 */
  tasks: RoleTask[];
  /** 是否显示为竖向堆叠（移动端） */
  vertical?: boolean;
  /** 类名 */
  className?: string;
  /** 任务卡片点击回调 */
  onTaskClick?: (task: RoleTask) => void;
}

/**
 * 默认4个角色配置
 */
const DEFAULT_ROLES: Omit<RoleTask, 'currentTask' | 'status' | 'progress'>[] = [
  {
    role: 'product_manager',
    roleName: '产品经理',
    icon: <User className="h-5 w-5" />,
    colorTheme: {
      bg: 'from-purple-500 to-pink-500',
      text: 'text-purple-700',
      border: 'border-purple-500',
      icon: 'bg-gradient-to-br from-purple-500 to-pink-500',
    },
  },
  {
    role: 'architect',
    roleName: '架构师',
    icon: <GitBranch className="h-5 w-5" />,
    colorTheme: {
      bg: 'from-blue-500 to-cyan-500',
      text: 'text-blue-700',
      border: 'border-blue-500',
      icon: 'bg-gradient-to-br from-blue-500 to-cyan-500',
    },
  },
  {
    role: 'engineer',
    roleName: '工程师',
    icon: <Code2 className="h-5 w-5" />,
    colorTheme: {
      bg: 'from-green-500 to-emerald-500',
      text: 'text-green-700',
      border: 'border-green-500',
      icon: 'bg-gradient-to-br from-green-500 to-emerald-500',
    },
  },
  {
    role: 'tester',
    roleName: '测试工程师',
    icon: <TestTube2 className="h-5 w-5" />,
    colorTheme: {
      bg: 'from-orange-500 to-amber-500',
      text: 'text-orange-700',
      border: 'border-orange-500',
      icon: 'bg-gradient-to-br from-orange-500 to-amber-500',
    },
  },
];

/**
 * 获取任务状态徽章配置
 */
const getStatusBadge = (status: TaskStatus) => {
  switch (status) {
    case 'completed':
      return {
        label: '已完成',
        variant: 'default' as const,
        icon: <CheckCircle2 className="h-3 w-3" />,
      };
    case 'working':
      return {
        label: '进行中',
        variant: 'secondary' as const,
        icon: <Loader2 className="h-3 w-3 animate-spin" />,
      };
    case 'failed':
      return {
        label: '失败',
        variant: 'destructive' as const,
        icon: <AlertCircle className="h-3 w-3" />,
      };
    default:
      return {
        label: '待开始',
        variant: 'outline' as const,
        icon: <Clock className="h-3 w-3" />,
      };
  }
};

/**
 * 单个角色任务卡片
 */
const RoleTaskCard: React.FC<{
  task: RoleTask;
  onClick?: () => void;
}> = ({ task, onClick }) => {
  const isActive = task.status === 'working';
  const isCompleted = task.status === 'completed';
  const statusBadge = getStatusBadge(task.status);

  return (
    <Card
      onClick={onClick}
      className={cn(
        'group relative cursor-pointer overflow-hidden transition-all duration-300',
        'hover:shadow-lg hover:-translate-y-1',
        isActive && 'ring-2 ring-primary ring-offset-2',
        'bg-card/50 backdrop-blur-sm'
      )}
    >
      {/* 顶部渐变装饰条 */}
      <div
        className={cn(
          'absolute left-0 right-0 top-0 h-1 bg-gradient-to-r',
          task.colorTheme.bg
        )}
      />

      <CardHeader className="pb-3">
        <div className="flex items-start justify-between">
          {/* 角色信息 */}
          <div className="flex items-center gap-3">
            {/* 角色图标 */}
            <div
              className={cn(
                'flex h-12 w-12 items-center justify-center rounded-xl text-white shadow-md transition-transform duration-300',
                task.colorTheme.icon,
                'group-hover:scale-110',
                isActive && 'animate-pulse'
              )}
            >
              {task.icon}
            </div>

            {/* 角色名称和任务数 */}
            <div>
              <h3 className="text-lg font-semibold">{task.roleName}</h3>
              {task.completedTasks !== undefined &&
                task.totalTasks !== undefined && (
                  <p className="text-xs text-muted-foreground">
                    {task.completedTasks}/{task.totalTasks} 任务完成
                  </p>
                )}
            </div>
          </div>

          {/* 状态徽章 */}
          <Badge variant={statusBadge.variant} className="gap-1 text-xs">
            {statusBadge.icon}
            {statusBadge.label}
          </Badge>
        </div>
      </CardHeader>

      <CardContent className="space-y-4">
        {/* 当前任务描述 */}
        <div className="space-y-2">
          <div className="flex items-center gap-2">
            <Sparkles className="h-4 w-4 text-muted-foreground" />
            <span className="text-xs font-medium text-muted-foreground">
              当前任务
            </span>
          </div>
          <p className="text-sm leading-relaxed">{task.currentTask}</p>
        </div>

        {/* 进度条 */}
        {task.status === 'working' && (
          <div className="space-y-2">
            <div className="flex items-center justify-between text-xs">
              <span className="text-muted-foreground">进度</span>
              <span className={task.colorTheme.text}>
                {Math.round(task.progress)}%
              </span>
            </div>
            <Progress value={task.progress} className="h-2" />
          </div>
        )}

        {/* 耗时统计 */}
        {task.duration && task.status === 'completed' && (
          <div className="flex items-center gap-2 text-xs text-muted-foreground">
            <Clock className="h-3 w-3" />
            <span>耗时 {(task.duration / 1000).toFixed(1)}s</span>
          </div>
        )}

        {/* 完成标记 */}
        {isCompleted && (
          <div className="flex items-center gap-2 rounded-lg bg-green-50 p-2 text-sm text-green-700">
            <CheckCircle2 className="h-4 w-4" />
            <span>任务已完成</span>
          </div>
        )}
      </CardContent>
    </Card>
  );
};

/**
 * 角色化任务详情卡片组件
 */
export const RoleTaskCards: React.FC<RoleTaskCardsProps> = ({
  tasks,
  vertical = false,
  className,
  onTaskClick,
}) => {
  return (
    <div className={cn('w-full', className)}>
      {/* 标题 */}
      <div className="mb-6">
        <h2 className="mb-2 text-xl font-bold">团队协作</h2>
        <p className="text-sm text-muted-foreground">
          AI Agent团队正在为你构建应用
        </p>
      </div>

      {/* 角色任务卡片网格 */}
      <div
        className={cn(
          vertical
            ? 'flex flex-col gap-4'
            : 'grid gap-4 sm:grid-cols-2 lg:grid-cols-4'
        )}
      >
        {tasks.map((task) => (
          <RoleTaskCard
            key={task.role}
            task={task}
            onClick={() => onTaskClick?.(task)}
          />
        ))}
      </div>
    </div>
  );
};

/**
 * 辅助函数：从Agent状态映射到角色任务
 */
export const mapAgentToRoleTask = (
  agentType: string,
  agentStatus: string,
  agentProgress: number,
  currentTask?: string,
  duration?: number
): RoleTask | null => {
  const defaultRole = DEFAULT_ROLES.find((role) => {
    switch (agentType) {
      case 'PLAN':
        return role.role === 'product_manager';
      case 'SCHEMA':
        return role.role === 'architect';
      case 'EXECUTE':
      case 'CODE':
        return role.role === 'engineer';
      case 'VALIDATE':
      case 'TEST':
        return role.role === 'tester';
      default:
        return false;
    }
  });

  if (!defaultRole) return null;

  // 映射Agent状态到任务状态
  let taskStatus: TaskStatus = 'idle';
  switch (agentStatus) {
    case 'RUNNING':
    case 'IN_PROGRESS':
      taskStatus = 'working';
      break;
    case 'COMPLETED':
    case 'SUCCESS':
      taskStatus = 'completed';
      break;
    case 'FAILED':
    case 'ERROR':
      taskStatus = 'failed';
      break;
    default:
      taskStatus = 'idle';
  }

  return {
    ...defaultRole,
    currentTask:
      currentTask ||
      (taskStatus === 'completed'
        ? '任务已完成'
        : taskStatus === 'working'
          ? '正在执行任务...'
          : '等待开始'),
    status: taskStatus,
    progress: agentProgress,
    duration,
  };
};
