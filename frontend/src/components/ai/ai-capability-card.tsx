/**
 * AI能力卡片组件
 * 显示单个AI能力的详细信息，支持选中、推荐标识、动画效果
 *
 * @author Ingenio Team
 * @since 1.0.0
 */
'use client';

import { motion } from 'framer-motion';
import { cn } from '@/lib/utils';
import { Card, CardContent } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import {
  Sparkles,
  CheckCircle2,
  Info,
  Zap,
  Flame,
  AlertTriangle,
  MessageSquare,
  HelpCircle,
  Database,
  Image as ImageIcon,
  Mic,
  FileText,
  Heart,
  ThumbsUp,
  Shield,
  Languages,
  Code,
  Video,
  Network,
  Radio,
  User,
  TrendingUp,
  Search,
  Layers,
} from 'lucide-react';
import {
  AICapabilityCardProps,
  ComplexityLevel,
} from '@/types/ai-capability';

/**
 * 图标映射表
 */
const ICON_MAP: Record<string, React.ComponentType<{ className?: string }>> = {
  MessageSquare,
  HelpCircle,
  Database,
  Image: ImageIcon,
  Mic,
  FileText,
  Heart,
  ThumbsUp,
  Shield,
  Languages,
  Code,
  Video,
  Network,
  Radio,
  User,
  TrendingUp,
  Search,
  Layers,
};

/**
 * 复杂度配置
 */
const COMPLEXITY_CONFIG = {
  [ComplexityLevel.SIMPLE]: {
    icon: Zap,
    label: '简单',
    color: 'text-green-600',
    bgColor: 'bg-green-100',
    borderColor: 'border-green-300',
  },
  [ComplexityLevel.MEDIUM]: {
    icon: Flame,
    label: '中等',
    color: 'text-orange-600',
    bgColor: 'bg-orange-100',
    borderColor: 'border-orange-300',
  },
  [ComplexityLevel.COMPLEX]: {
    icon: AlertTriangle,
    label: '复杂',
    color: 'text-red-600',
    bgColor: 'bg-red-100',
    borderColor: 'border-red-300',
  },
};

/**
 * AI能力卡片组件
 */
export function AICapabilityCard({
  capability,
  isSelected,
  isRecommended,
  onToggle,
  disabled = false,
  onShowDetail,
  className,
}: AICapabilityCardProps) {
  const IconComponent = ICON_MAP[capability.icon] || FileText;
  const complexityConfig = COMPLEXITY_CONFIG[capability.complexity];
  const ComplexityIcon = complexityConfig.icon;

  const handleKeyDown = (e: React.KeyboardEvent<HTMLDivElement>) => {
    if (e.key === 'Enter' || e.key === ' ') {
      e.preventDefault();
      if (!disabled) {
        onToggle();
      }
    }
  };

  const handleShowDetail = (e: React.MouseEvent<HTMLButtonElement>) => {
    e.stopPropagation();
    if (onShowDetail && !disabled) {
      onShowDetail(capability);
    }
  };

  return (
    <motion.div
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.3 }}
      whileHover={disabled ? {} : { y: -4 }}
      whileTap={disabled ? {} : { scale: 0.98 }}
    >
      <Card
        role="button"
        tabIndex={disabled ? -1 : 0}
        aria-pressed={isSelected}
        aria-label={`${capability.name}，${capability.description}，预估成本每月${capability.estimatedCost}美元，复杂度${complexityConfig.label}，${isSelected ? '已选中' : '未选中'}${isRecommended ? '，推荐' : ''}`}
        data-testid="capability-card"
        onClick={disabled ? undefined : onToggle}
        onKeyDown={handleKeyDown}
        className={cn(
          'cursor-pointer transition-all duration-300 relative overflow-hidden',
          'hover:shadow-xl hover:shadow-purple-100',
          isSelected && 'border-2 border-purple-500 bg-purple-50/50 shadow-lg',
          !isSelected && 'border border-gray-200 hover:border-purple-300',
          disabled && 'opacity-50 cursor-not-allowed',
          className
        )}
      >
        <CardContent className="p-5 space-y-3">
          {isRecommended && (
            <motion.div
              initial={{ opacity: 0, scale: 0.8 }}
              animate={{ opacity: 1, scale: 1 }}
              transition={{ duration: 0.3 }}
            >
              <Badge
                data-testid="recommended-badge"
                className={cn(
                  'absolute top-3 right-3',
                  'bg-gradient-to-r from-yellow-400 to-orange-400',
                  'text-white border-0 shadow-md',
                  'animate-pulse'
                )}
              >
                <Sparkles className="w-3 h-3 mr-1" />
                推荐
              </Badge>
            </motion.div>
          )}

          <div className="flex items-start justify-between">
            <div
              className={cn(
                'w-12 h-12 rounded-lg flex items-center justify-center',
                'transition-colors duration-300',
                isSelected
                  ? 'bg-purple-100 text-purple-600'
                  : 'bg-gray-100 text-gray-600'
              )}
            >
              <IconComponent className="w-6 h-6" />
            </div>

            {isSelected && (
              <motion.div
                initial={{ scale: 0 }}
                animate={{ scale: [0, 1.2, 1] }}
                transition={{ duration: 0.3 }}
              >
                <CheckCircle2 className="w-6 h-6 text-green-500" />
              </motion.div>
            )}
          </div>

          <div className="space-y-1">
            <h3 className="font-semibold text-base text-gray-900 leading-tight">
              {capability.name}
            </h3>
            <p className="text-xs text-gray-500">{capability.nameEn}</p>
          </div>

          <p className="text-sm text-gray-600 line-clamp-2 min-h-[40px]">
            {capability.description}
          </p>

          <div className="flex flex-wrap gap-2">
            <Badge
              variant="outline"
              className={cn(
                'flex items-center gap-1',
                complexityConfig.bgColor,
                complexityConfig.color,
                complexityConfig.borderColor
              )}
            >
              <ComplexityIcon className="w-3 h-3" />
              {complexityConfig.label}
            </Badge>

            {capability.isNew && (
              <Badge
                variant="outline"
                className="bg-blue-100 text-blue-600 border-blue-300"
              >
                新功能
              </Badge>
            )}

            {capability.isPopular && (
              <Badge
                variant="outline"
                className="bg-pink-100 text-pink-600 border-pink-300"
              >
                热门
              </Badge>
            )}
          </div>

          <div className="flex items-center justify-between text-xs text-gray-500 pt-2 border-t border-gray-100">
            <div className="flex items-center gap-1">
              <span className="font-medium text-gray-700">
                ${capability.estimatedCost}
              </span>
              /月
            </div>
            <div className="flex items-center gap-1">
              <span className="font-medium text-gray-700">
                {capability.estimatedDays}天
              </span>
              开发
            </div>
          </div>

          <div className="flex gap-2 pt-2">
            <Button
              variant={isSelected ? 'default' : 'outline'}
              size="sm"
              className={cn(
                'flex-1 transition-all duration-300',
                isSelected &&
                  'bg-purple-600 hover:bg-purple-700 shadow-md shadow-purple-200'
              )}
              onClick={(e) => {
                e.stopPropagation();
                onToggle();
              }}
              disabled={disabled}
            >
              {isSelected ? (
                <>
                  <CheckCircle2 className="w-4 h-4 mr-1" />
                  已选
                </>
              ) : (
                '+ 选择'
              )}
            </Button>

            {onShowDetail && (
              <Button
                data-testid="view-details-btn"
                variant="ghost"
                size="sm"
                className="px-3"
                onClick={handleShowDetail}
                disabled={disabled}
                aria-label={`查看${capability.name}详情`}
              >
                <Info className="w-4 h-4" />
              </Button>
            )}
          </div>
        </CardContent>

        {isSelected && (
          <motion.div
            className="absolute inset-0 bg-gradient-to-br from-purple-500/5 to-pink-500/5 pointer-events-none"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            transition={{ duration: 0.3 }}
          />
        )}
      </Card>
    </motion.div>
  );
}
