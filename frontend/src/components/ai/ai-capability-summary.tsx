/**
 * AI能力选择摘要组件
 * 显示已选择的AI能力列表、统计信息、确认按钮
 *
 * @author Ingenio Team
 * @since 1.0.0
 */
'use client';

import { useMemo } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { cn } from '@/lib/utils';
import { Card, CardContent } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import {
  X,
  DollarSign,
  Clock,
  TrendingUp,
  AlertCircle,
  CheckCircle2,
  Trash2,
} from 'lucide-react';
import {
  AICapabilitySummaryProps,
  ComplexityLevel,
} from '@/types/ai-capability';
import { calculateStats } from '@/data/ai-capabilities';

/**
 * 复杂度分布颜色配置
 */
const COMPLEXITY_COLORS = {
  [ComplexityLevel.SIMPLE]: {
    bg: 'bg-green-500',
    label: '简单',
  },
  [ComplexityLevel.MEDIUM]: {
    bg: 'bg-orange-500',
    label: '中等',
  },
  [ComplexityLevel.COMPLEX]: {
    bg: 'bg-red-500',
    label: '复杂',
  },
};

/**
 * AI能力选择摘要组件
 */
export function AICapabilitySummary({
  selectedCapabilities,
  onRemoveCapability,
  onClearAll,
  onConfirm,
  showConfirmButton = true,
  className,
}: AICapabilitySummaryProps) {
  const stats = useMemo(() => {
    const types = selectedCapabilities.map((c) => c.type);
    return calculateStats(types);
  }, [selectedCapabilities]);

  const complexityDistribution = useMemo(() => {
    const distribution = {
      [ComplexityLevel.SIMPLE]: 0,
      [ComplexityLevel.MEDIUM]: 0,
      [ComplexityLevel.COMPLEX]: 0,
    };

    selectedCapabilities.forEach((capability) => {
      distribution[capability.complexity]++;
    });

    return distribution;
  }, [selectedCapabilities]);

  const isCostWarning = stats.totalCost > 50;
  const isDaysWarning = stats.totalDays > 15;

  if (selectedCapabilities.length === 0) {
    return null;
  }

  return (
    <motion.div
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
      exit={{ opacity: 0, y: 20 }}
      transition={{ duration: 0.3 }}
      className={cn('sticky bottom-0 z-10', className)}
    >
      <Card data-testid="summary-panel" className="border-2 border-purple-200 bg-gradient-to-br from-purple-50/90 to-pink-50/90 backdrop-blur-sm shadow-xl">
        <CardContent className="pt-6 space-y-5">
          <div className="flex items-center justify-between">
            <h3 className="text-lg font-bold text-gray-900 flex items-center gap-2">
              <CheckCircle2 className="w-5 h-5 text-purple-600" />
              已选择的AI能力
              <Badge variant="secondary" className="ml-2">
                {selectedCapabilities.length} 个
              </Badge>
            </h3>
            <Button
              variant="ghost"
              size="sm"
              onClick={onClearAll}
              className="text-gray-600 hover:text-red-600 hover:bg-red-50"
            >
              <Trash2 className="w-4 h-4 mr-2" />
              清空全部
            </Button>
          </div>

          <div className="flex flex-wrap gap-2 max-h-32 overflow-y-auto">
            <AnimatePresence mode="popLayout">
              {selectedCapabilities.map((capability, index) => (
                <motion.div
                  key={capability.type}
                  initial={{ opacity: 0, scale: 0.8 }}
                  animate={{ opacity: 1, scale: 1 }}
                  exit={{ opacity: 0, scale: 0.8 }}
                  transition={{ duration: 0.2, delay: index * 0.03 }}
                  layout
                >
                  <Badge
                    variant="secondary"
                    className="px-3 py-1.5 cursor-pointer hover:bg-red-100 hover:text-red-700 hover:border-red-300 transition-all duration-200 group"
                    onClick={() => onRemoveCapability(capability.type)}
                  >
                    <span className="mr-2">{capability.name}</span>
                    <X className="w-3 h-3 opacity-60 group-hover:opacity-100" />
                  </Badge>
                </motion.div>
              ))}
            </AnimatePresence>
          </div>

          <div className="grid grid-cols-3 gap-3">
            <motion.div
              whileHover={{ scale: 1.03 }}
              className={cn(
                'p-4 rounded-lg border-2 transition-all duration-200',
                isCostWarning
                  ? 'bg-orange-50 border-orange-200'
                  : 'bg-white border-green-200'
              )}
            >
              <div className="flex items-start gap-3">
                <div
                  className={cn(
                    'w-10 h-10 rounded-lg flex items-center justify-center',
                    isCostWarning ? 'bg-orange-100' : 'bg-green-100'
                  )}
                >
                  <DollarSign
                    className={cn(
                      'w-5 h-5',
                      isCostWarning ? 'text-orange-600' : 'text-green-600'
                    )}
                  />
                </div>
                <div className="flex-1">
                  <p className="text-xs text-gray-600 mb-1">预估月成本</p>
                  <p
                    data-testid="total-cost"
                    className={cn(
                      'text-2xl font-bold',
                      isCostWarning ? 'text-orange-600' : 'text-green-600'
                    )}
                  >
                    ${stats.totalCost.toFixed(1)}
                  </p>
                  {isCostWarning && (
                    <div className="flex items-center gap-1 mt-1">
                      <AlertCircle className="w-3 h-3 text-orange-500" />
                      <span className="text-xs text-orange-600">成本较高</span>
                    </div>
                  )}
                </div>
              </div>
            </motion.div>

            <motion.div
              whileHover={{ scale: 1.03 }}
              className={cn(
                'p-4 rounded-lg border-2 transition-all duration-200',
                isDaysWarning
                  ? 'bg-orange-50 border-orange-200'
                  : 'bg-white border-blue-200'
              )}
            >
              <div className="flex items-start gap-3">
                <div
                  className={cn(
                    'w-10 h-10 rounded-lg flex items-center justify-center',
                    isDaysWarning ? 'bg-orange-100' : 'bg-blue-100'
                  )}
                >
                  <Clock
                    className={cn(
                      'w-5 h-5',
                      isDaysWarning ? 'text-orange-600' : 'text-blue-600'
                    )}
                  />
                </div>
                <div className="flex-1">
                  <p className="text-xs text-gray-600 mb-1">预估开发工期</p>
                  <p
                    data-testid="total-days"
                    className={cn(
                      'text-2xl font-bold',
                      isDaysWarning ? 'text-orange-600' : 'text-blue-600'
                    )}
                  >
                    {stats.totalDays} 天
                  </p>
                  {isDaysWarning && (
                    <div className="flex items-center gap-1 mt-1">
                      <AlertCircle className="w-3 h-3 text-orange-500" />
                      <span className="text-xs text-orange-600">工期较长</span>
                    </div>
                  )}
                </div>
              </div>
            </motion.div>

            <motion.div
              whileHover={{ scale: 1.03 }}
              className="p-4 bg-white rounded-lg border-2 border-purple-200 transition-all duration-200"
            >
              <div className="flex items-start gap-3">
                <div className="w-10 h-10 rounded-lg bg-purple-100 flex items-center justify-center">
                  <TrendingUp className="w-5 h-5 text-purple-600" />
                </div>
                <div className="flex-1">
                  <p className="text-xs text-gray-600 mb-1">平均复杂度</p>
                  <p className="text-2xl font-bold text-purple-600">
                    {stats.avgComplexity.toFixed(1)}
                  </p>
                </div>
              </div>
            </motion.div>
          </div>

          <div className="space-y-2">
            <p className="text-xs text-gray-600 font-medium">复杂度分布</p>
            <div className="flex gap-1 h-2 rounded-full overflow-hidden bg-gray-200">
              {Object.entries(complexityDistribution).map(
                ([level, count]) =>
                  count > 0 && (
                    <motion.div
                      key={level}
                      initial={{ width: 0 }}
                      animate={{
                        width: `${(count / selectedCapabilities.length) * 100}%`,
                      }}
                      transition={{ duration: 0.5, ease: 'easeOut' }}
                      className={
                        COMPLEXITY_COLORS[level as ComplexityLevel].bg
                      }
                      title={`${COMPLEXITY_COLORS[level as ComplexityLevel].label}: ${count}个`}
                    />
                  )
              )}
            </div>
            <div className="flex items-center justify-between text-xs text-gray-600">
              {Object.entries(complexityDistribution).map(([level, count]) => (
                <div key={level} className="flex items-center gap-1">
                  <div
                    className={cn(
                      'w-2 h-2 rounded-full',
                      COMPLEXITY_COLORS[level as ComplexityLevel].bg
                    )}
                  />
                  <span>
                    {COMPLEXITY_COLORS[level as ComplexityLevel].label}:{' '}
                    {count}
                  </span>
                </div>
              ))}
            </div>
          </div>

          {showConfirmButton && (
            <motion.div whileHover={{ scale: 1.02 }} whileTap={{ scale: 0.98 }}>
              <Button
                onClick={onConfirm}
                className="w-full bg-gradient-to-r from-purple-600 to-pink-600 hover:from-purple-700 hover:to-pink-700 text-white shadow-lg shadow-purple-300 h-12 text-base font-semibold"
              >
                <CheckCircle2 className="w-5 h-5 mr-2" />
                确认选择（{selectedCapabilities.length} 个能力）
              </Button>
            </motion.div>
          )}
        </CardContent>
      </Card>
    </motion.div>
  );
}
