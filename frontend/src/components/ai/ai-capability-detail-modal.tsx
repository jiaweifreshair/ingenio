/**
 * AI能力详情弹窗组件
 * 显示AI能力的完整信息，包括描述、用例、技术栈、成本估算等
 *
 * @author Ingenio Team
 * @since 1.0.0
 */
'use client';

import { motion, AnimatePresence } from 'framer-motion';
import { cn } from '@/lib/utils';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { ScrollArea } from '@/components/ui/scroll-area';
import {
  CheckCircle2,
  X,
  Zap,
  Flame,
  AlertTriangle,
  DollarSign,
  Clock,
  Code2,
  Lightbulb,
} from 'lucide-react';
import { AICapabilityDetailProps, ComplexityLevel } from '@/types/ai-capability';

/**
 * 复杂度配置
 */
const COMPLEXITY_CONFIG = {
  [ComplexityLevel.SIMPLE]: {
    icon: Zap,
    label: '简单',
    description: '1-2天即可完成开发和测试',
    color: 'text-green-600',
    bgColor: 'bg-green-50',
    borderColor: 'border-green-200',
  },
  [ComplexityLevel.MEDIUM]: {
    icon: Flame,
    label: '中等',
    description: '3-5天完成开发、测试和优化',
    color: 'text-orange-600',
    bgColor: 'bg-orange-50',
    borderColor: 'border-orange-200',
  },
  [ComplexityLevel.COMPLEX]: {
    icon: AlertTriangle,
    label: '复杂',
    description: '5-10天完成开发、测试和性能优化',
    color: 'text-red-600',
    bgColor: 'bg-red-50',
    borderColor: 'border-red-200',
  },
};

/**
 * AI能力详情弹窗组件
 */
export function AICapabilityDetailModal({
  capability,
  open,
  onClose,
  isSelected,
  onToggleSelection,
}: AICapabilityDetailProps) {
  if (!capability) return null;

  const complexityConfig = COMPLEXITY_CONFIG[capability.complexity];
  const ComplexityIcon = complexityConfig.icon;

  return (
    <Dialog open={open} onOpenChange={(open) => !open && onClose()}>
      <DialogContent
        className="max-w-3xl max-h-[90vh] p-0 overflow-hidden"
        aria-labelledby="capability-detail-title"
        aria-describedby="capability-detail-description"
      >
        <AnimatePresence>
          {open && (
            <motion.div
              initial={{ opacity: 0, scale: 0.95 }}
              animate={{ opacity: 1, scale: 1 }}
              exit={{ opacity: 0, scale: 0.95 }}
              transition={{ duration: 0.2 }}
            >
              <ScrollArea className="max-h-[90vh]">
                <div className="p-6 space-y-6">
                  {/* 头部 */}
                  <DialogHeader>
                    <div className="flex items-start justify-between gap-4">
                      <div className="flex-1 space-y-2">
                        <DialogTitle
                          id="capability-detail-title"
                          className="text-2xl font-bold text-gray-900"
                        >
                          {capability.name}
                        </DialogTitle>
                        <DialogDescription
                          id="capability-detail-description"
                          className="text-base text-gray-600"
                        >
                          {capability.nameEn}
                        </DialogDescription>
                      </div>

                      {/* 选中状态徽章 */}
                      {isSelected && (
                        <motion.div
                          initial={{ scale: 0 }}
                          animate={{ scale: 1 }}
                          transition={{ duration: 0.3 }}
                        >
                          <Badge className="bg-green-500 text-white border-0 shadow-md">
                            <CheckCircle2 className="w-4 h-4 mr-1" />
                            已选中
                          </Badge>
                        </motion.div>
                      )}
                    </div>
                  </DialogHeader>

                  {/* 简短描述 */}
                  <div className="p-4 bg-purple-50 rounded-lg border border-purple-200">
                    <p className="text-sm text-gray-700 leading-relaxed">
                      {capability.description}
                    </p>
                  </div>

                  {/* 详细描述 */}
                  {capability.detailedDescription && (
                    <div className="space-y-2">
                      <h3 className="text-lg font-semibold text-gray-900 flex items-center gap-2">
                        <Lightbulb className="w-5 h-5 text-yellow-500" />
                        功能详情
                      </h3>
                      <p className="text-sm text-gray-600 leading-relaxed">
                        {capability.detailedDescription}
                      </p>
                    </div>
                  )}

                  {/* 复杂度信息 */}
                  <div
                    className={cn(
                      'p-4 rounded-lg border',
                      complexityConfig.bgColor,
                      complexityConfig.borderColor
                    )}
                  >
                    <div className="flex items-start gap-3">
                      <ComplexityIcon
                        className={cn('w-6 h-6 mt-0.5', complexityConfig.color)}
                      />
                      <div className="flex-1 space-y-1">
                        <h4
                          className={cn(
                            'text-base font-semibold',
                            complexityConfig.color
                          )}
                        >
                          复杂度：{complexityConfig.label}
                        </h4>
                        <p className="text-sm text-gray-600">
                          {complexityConfig.description}
                        </p>
                      </div>
                    </div>
                  </div>

                  {/* 使用场景 */}
                  <div className="space-y-3">
                    <h3 className="text-lg font-semibold text-gray-900 flex items-center gap-2">
                      <Lightbulb className="w-5 h-5 text-blue-500" />
                      使用场景
                    </h3>
                    <div className="grid grid-cols-2 gap-2">
                      {capability.useCases.map((useCase, index) => (
                        <motion.div
                          key={index}
                          initial={{ opacity: 0, x: -10 }}
                          animate={{ opacity: 1, x: 0 }}
                          transition={{ delay: index * 0.05 }}
                          className="flex items-center gap-2 p-3 bg-blue-50 rounded-lg border border-blue-200"
                        >
                          <div className="w-1.5 h-1.5 rounded-full bg-blue-500" />
                          <span className="text-sm text-gray-700">{useCase}</span>
                        </motion.div>
                      ))}
                    </div>
                  </div>

                  {/* 技术栈 */}
                  <div className="space-y-3">
                    <h3 className="text-lg font-semibold text-gray-900 flex items-center gap-2">
                      <Code2 className="w-5 h-5 text-purple-500" />
                      技术栈
                    </h3>
                    <div className="flex flex-wrap gap-2">
                      {capability.techStack.map((tech, index) => (
                        <motion.div
                          key={index}
                          initial={{ opacity: 0, scale: 0.8 }}
                          animate={{ opacity: 1, scale: 1 }}
                          transition={{ delay: index * 0.05 }}
                        >
                          <Badge
                            variant="outline"
                            className="bg-purple-50 text-purple-700 border-purple-200 px-3 py-1"
                          >
                            {tech}
                          </Badge>
                        </motion.div>
                      ))}
                    </div>
                  </div>

                  {/* 成本和工期估算 */}
                  <div className="grid grid-cols-2 gap-4">
                    {/* 成本 */}
                    <div className="p-4 bg-green-50 rounded-lg border border-green-200">
                      <div className="flex items-center gap-3">
                        <div className="w-10 h-10 rounded-lg bg-green-100 flex items-center justify-center">
                          <DollarSign className="w-5 h-5 text-green-600" />
                        </div>
                        <div>
                          <p className="text-xs text-gray-600">预估月成本</p>
                          <p className="text-xl font-bold text-green-600">
                            ${capability.estimatedCost}
                          </p>
                        </div>
                      </div>
                    </div>

                    {/* 工期 */}
                    <div className="p-4 bg-blue-50 rounded-lg border border-blue-200">
                      <div className="flex items-center gap-3">
                        <div className="w-10 h-10 rounded-lg bg-blue-100 flex items-center justify-center">
                          <Clock className="w-5 h-5 text-blue-600" />
                        </div>
                        <div>
                          <p className="text-xs text-gray-600">预估开发工期</p>
                          <p className="text-xl font-bold text-blue-600">
                            {capability.estimatedDays} 天
                          </p>
                        </div>
                      </div>
                    </div>
                  </div>
                </div>
              </ScrollArea>

              {/* 底部操作栏 */}
              <DialogFooter className="px-6 py-4 bg-gray-50 border-t border-gray-200">
                <div className="flex items-center justify-between w-full gap-3">
                  <Button
                    variant="outline"
                    onClick={onClose}
                    className="flex-1"
                  >
                    <X className="w-4 h-4 mr-2" />
                    关闭
                  </Button>
                  <Button
                    variant={isSelected ? 'destructive' : 'default'}
                    onClick={() => {
                      onToggleSelection();
                      // 不自动关闭弹窗，让用户可以继续查看详情
                    }}
                    className={cn(
                      'flex-1',
                      !isSelected &&
                        'bg-purple-600 hover:bg-purple-700 shadow-md shadow-purple-200'
                    )}
                  >
                    {isSelected ? (
                      <>
                        <X className="w-4 h-4 mr-2" />
                        取消选择
                      </>
                    ) : (
                      <>
                        <CheckCircle2 className="w-4 h-4 mr-2" />
                        选择此能力
                      </>
                    )}
                  </Button>
                </div>
              </DialogFooter>
            </motion.div>
          )}
        </AnimatePresence>
      </DialogContent>
    </Dialog>
  );
}
