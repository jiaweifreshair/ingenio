/**
 * AI能力选择器组件（完整版）
 * 允许用户浏览和选择AI能力类型，支持智能推荐、筛选、搜索等功能
 *
 * 功能特性：
 * - 智能推荐算法（基于用户需求关键词匹配）
 * - 多维度筛选（分类、搜索、复杂度）
 * - 实时统计（总成本、总工期、复杂度分布）
 * - 响应式布局（4个断点：Mobile/Tablet/Desktop/Large）
 * - 完整的无障碍支持（ARIA标签、键盘导航）
 * - 流畅的微交互动画
 *
 * @author Ingenio Team
 * @since 1.0.0
 */
'use client';

import { useState, useMemo, useCallback, useEffect } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { cn } from '@/lib/utils';
import {
  AICapabilityType,
  AICapabilityCategory,
  type AICapabilityPickerProps,
  type FilterOptions,
  type AICapability,
} from '@/types/ai-capability';
import { AI_CAPABILITIES } from '@/data/ai-capabilities';
import { Card } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Badge } from '@/components/ui/badge';
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { Sparkles, Search, X, Filter } from 'lucide-react';
import { AICapabilityCard } from './ai-capability-card';
import { AICapabilityDetailModal } from './ai-capability-detail-modal';
import { AICapabilitySummary } from './ai-capability-summary';

/**
 * 智能推荐关键词映射表
 */
const RECOMMENDATION_KEYWORDS_MAP: Array<{
  keywords: string[];
  capabilityType: AICapabilityType;
  weight: number;
}> = [
  {
    keywords: ['聊天', '对话', '客服', '机器人', 'chat', 'bot'],
    capabilityType: AICapabilityType.CHATBOT,
    weight: 1.0,
  },
  {
    keywords: ['视频', '直播', '监控', 'video'],
    capabilityType: AICapabilityType.VIDEO_ANALYSIS,
    weight: 1.0,
  },
  {
    keywords: ['文档', 'PDF', 'OCR', '识别', 'document'],
    capabilityType: AICapabilityType.OCR_DOCUMENT,
    weight: 1.0,
  },
  {
    keywords: ['翻译', '多语言', '国际化', 'translate', 'i18n'],
    capabilityType: AICapabilityType.TRANSLATION,
    weight: 1.0,
  },
  {
    keywords: ['语音', '录音', '转写', 'speech', 'voice'],
    capabilityType: AICapabilityType.SPEECH_RECOGNITION,
    weight: 1.0,
  },
  {
    keywords: ['图片', '照片', '图像', 'image', 'photo'],
    capabilityType: AICapabilityType.IMAGE_RECOGNITION,
    weight: 0.9,
  },
  {
    keywords: ['推荐', '个性化', '定制', 'recommend', 'personalize'],
    capabilityType: AICapabilityType.HYPER_PERSONALIZATION,
    weight: 0.9,
  },
  {
    keywords: ['分析', '预测', '趋势', 'analytics', 'predict'],
    capabilityType: AICapabilityType.PREDICTIVE_ANALYTICS,
    weight: 0.8,
  },
  {
    keywords: ['搜索', '查询', '检索', 'search', 'query'],
    capabilityType: AICapabilityType.SMART_SEARCH,
    weight: 0.8,
  },
  {
    keywords: ['知识', '图谱', '关系', 'knowledge', 'graph'],
    capabilityType: AICapabilityType.KNOWLEDGE_GRAPH,
    weight: 0.7,
  },
  {
    keywords: ['问答', 'FAQ', 'Q&A', 'qa'],
    capabilityType: AICapabilityType.QA_SYSTEM,
    weight: 0.7,
  },
  {
    keywords: ['生成', '创作', '写作', 'generate', 'write'],
    capabilityType: AICapabilityType.TEXT_GENERATION,
    weight: 0.6,
  },
  {
    keywords: ['代码', '编程', '开发', 'code', 'programming'],
    capabilityType: AICapabilityType.CODE_GENERATION,
    weight: 0.6,
  },
  {
    keywords: ['审核', '内容', '安全', 'moderation', 'safety'],
    capabilityType: AICapabilityType.CONTENT_MODERATION,
    weight: 0.6,
  },
  {
    keywords: ['情感', '情绪', '舆情', 'sentiment', 'emotion'],
    capabilityType: AICapabilityType.SENTIMENT_ANALYSIS,
    weight: 0.6,
  },
  {
    keywords: ['实时', '流', 'stream', 'realtime'],
    capabilityType: AICapabilityType.REALTIME_STREAM,
    weight: 0.7,
  },
  {
    keywords: ['多模态', '融合', 'multimodal', 'fusion'],
    capabilityType: AICapabilityType.MULTIMODAL_FUSION,
    weight: 0.7,
  },
];

/**
 * 类别显示名称映射
 */
const CATEGORY_LABELS: Record<AICapabilityCategory | 'ALL', string> = {
  ALL: '全部',
  [AICapabilityCategory.CONVERSATION]: '对话',
  [AICapabilityCategory.VISION]: '视觉',
  [AICapabilityCategory.DOCUMENT]: '文档',
  [AICapabilityCategory.ANALYTICS]: '分析',
  [AICapabilityCategory.GENERATION]: '生成',
  [AICapabilityCategory.AUDIO]: '音频',
  [AICapabilityCategory.REALTIME]: '实时',
};

/**
 * 获取智能推荐
 */
function getRecommendations(userRequirement: string): AICapabilityType[] {
  if (!userRequirement || userRequirement.trim().length === 0) {
    return [];
  }

  const lowerRequirement = userRequirement.toLowerCase();
  const recommendations: Array<{
    type: AICapabilityType;
    score: number;
  }> = [];

  RECOMMENDATION_KEYWORDS_MAP.forEach((rule) => {
    const matchCount = rule.keywords.filter((keyword) =>
      lowerRequirement.includes(keyword)
    ).length;

    if (matchCount > 0) {
      const existingIndex = recommendations.findIndex(
        (r) => r.type === rule.capabilityType
      );

      if (existingIndex >= 0) {
        recommendations[existingIndex].score += matchCount * rule.weight;
      } else {
        recommendations.push({
          type: rule.capabilityType,
          score: matchCount * rule.weight,
        });
      }
    }
  });

  return recommendations
    .sort((a, b) => b.score - a.score)
    .slice(0, 5)
    .map((r) => r.type);
}

/**
 * 使用防抖Hook（修复版）
 */
function useDebouncedValue<T>(value: T, delay: number): T {
  const [debouncedValue, setDebouncedValue] = useState<T>(value);

  useEffect(() => {
    const handler = setTimeout(() => {
      setDebouncedValue(value);
    }, delay);

    return () => {
      clearTimeout(handler);
    };
  }, [value, delay]);

  return debouncedValue;
}

/**
 * AI能力选择器主组件
 */
export function AICapabilityPicker({
  selectedCapabilities,
  onSelectionChange,
  userRequirement,
  maxSelection = 5,
  disabled = false,
  showCostEstimate = true,
  showRecommendations = true,
  className,
}: AICapabilityPickerProps) {
  const [filterOptions, setFilterOptions] = useState<FilterOptions>({
    selectedCategory: 'ALL',
    searchQuery: '',
    complexityFilter: [],
    showRecommendedOnly: false,
  });

  const [detailModalState, setDetailModalState] = useState<{
    open: boolean;
    capability: AICapability | null;
  }>({
    open: false,
    capability: null,
  });

  const debouncedSearchQuery = useDebouncedValue(
    filterOptions.searchQuery,
    300
  );

  const recommendedTypes = useMemo(() => {
    if (!showRecommendations || !userRequirement) return [];
    return getRecommendations(userRequirement);
  }, [showRecommendations, userRequirement]);

  const filteredCapabilities = useMemo(() => {
    let result = AI_CAPABILITIES;

    if (filterOptions.selectedCategory !== 'ALL') {
      result = result.filter(
        (c) => c.category === filterOptions.selectedCategory
      );
    }

    if (debouncedSearchQuery.trim()) {
      const query = debouncedSearchQuery.toLowerCase();
      result = result.filter(
        (c) =>
          c.name.toLowerCase().includes(query) ||
          c.nameEn.toLowerCase().includes(query) ||
          c.description.toLowerCase().includes(query) ||
          c.useCases.some((uc) => uc.toLowerCase().includes(query)) ||
          c.techStack.some((ts) => ts.toLowerCase().includes(query))
      );
    }

    if (
      filterOptions.complexityFilter &&
      filterOptions.complexityFilter.length > 0
    ) {
      result = result.filter((c) =>
        filterOptions.complexityFilter?.includes(c.complexity)
      );
    }

    if (filterOptions.showRecommendedOnly) {
      result = result.filter((c) => recommendedTypes.includes(c.type));
    }

    return result;
  }, [
    filterOptions.selectedCategory,
    debouncedSearchQuery,
    filterOptions.complexityFilter,
    filterOptions.showRecommendedOnly,
    recommendedTypes,
  ]);

  const selectedCapabilityObjects = useMemo(() => {
    return selectedCapabilities
      .map((type) => AI_CAPABILITIES.find((c) => c.type === type))
      .filter((c): c is AICapability => c !== undefined);
  }, [selectedCapabilities]);

  const handleToggle = useCallback(
    (type: AICapabilityType) => {
      if (disabled) return;

      if (selectedCapabilities.includes(type)) {
        onSelectionChange(selectedCapabilities.filter((t) => t !== type));
      } else {
        if (selectedCapabilities.length >= maxSelection) {
          alert(`最多只能选择${maxSelection}个AI能力`);
          return;
        }
        onSelectionChange([...selectedCapabilities, type]);
      }
    },
    [disabled, selectedCapabilities, maxSelection, onSelectionChange]
  );

  const handleClearAll = useCallback(() => {
    onSelectionChange([]);
  }, [onSelectionChange]);

  const handleCategoryChange = useCallback((category: string) => {
    setFilterOptions((prev) => ({
      ...prev,
      selectedCategory: category as AICapabilityCategory | 'ALL',
    }));
  }, []);

  const handleSearchChange = useCallback(
    (e: React.ChangeEvent<HTMLInputElement>) => {
      setFilterOptions((prev) => ({
        ...prev,
        searchQuery: e.target.value,
      }));
    },
    []
  );

  const handleClearSearch = useCallback(() => {
    setFilterOptions((prev) => ({
      ...prev,
      searchQuery: '',
    }));
  }, []);

  const handleShowDetail = useCallback((capability: AICapability) => {
    setDetailModalState({
      open: true,
      capability,
    });
  }, []);

  const handleCloseDetail = useCallback(() => {
    setDetailModalState({
      open: false,
      capability: null,
    });
  }, []);

  const handleToggleSelectionFromModal = useCallback(() => {
    if (detailModalState.capability) {
      handleToggle(detailModalState.capability.type);
    }
  }, [detailModalState.capability, handleToggle]);

  return (
    <div
      className={cn('space-y-6', className)}
      role="region"
      aria-label="AI能力选择器"
    >
      <motion.div
        initial={{ opacity: 0, y: -20 }}
        animate={{ opacity: 1, y: 0 }}
        className="flex items-center justify-between flex-wrap gap-4"
      >
        <div className="flex items-center gap-3">
          <h2 className="text-2xl font-bold text-gray-900">🤖 选择AI能力</h2>
          <Badge variant="outline" className="text-sm">
            {selectedCapabilities.length} / {AI_CAPABILITIES.length}
          </Badge>
          {recommendedTypes.length > 0 && (
            <Badge
              variant="outline"
              className="bg-yellow-100 text-yellow-800 border-yellow-300"
            >
              <Sparkles className="w-3 h-3 mr-1" />
              {recommendedTypes.length} 个推荐
            </Badge>
          )}
        </div>
        <div className="flex gap-2">
          {showRecommendations && recommendedTypes.length > 0 && (
            <Button
              variant="outline"
              size="sm"
              onClick={() =>
                setFilterOptions((prev) => ({
                  ...prev,
                  showRecommendedOnly: !prev.showRecommendedOnly,
                }))
              }
            >
              <Sparkles className="w-4 h-4 mr-2" />
              {filterOptions.showRecommendedOnly ? '显示全部' : '仅看推荐'}
            </Button>
          )}
          {selectedCapabilities.length > 0 && (
            <Button
              variant="outline"
              size="sm"
              onClick={handleClearAll}
              disabled={disabled}
            >
              <X className="w-4 h-4 mr-2" />
              清空选择
            </Button>
          )}
        </div>
      </motion.div>

      <motion.div
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
        transition={{ delay: 0.1 }}
        className="space-y-4"
      >
        <div>
          <p className="text-sm text-muted-foreground mb-2 flex items-center gap-2">
            <Filter className="w-4 h-4" />
            按类别筛选
          </p>
          <Tabs
            value={filterOptions.selectedCategory}
            onValueChange={handleCategoryChange}
          >
            <TabsList className="grid w-full grid-cols-4 lg:grid-cols-8 gap-2">
              <TabsTrigger value="ALL">
                {CATEGORY_LABELS.ALL} ({AI_CAPABILITIES.length})
              </TabsTrigger>
              {Object.values(AICapabilityCategory).map((category) => {
                const count = AI_CAPABILITIES.filter(
                  (c) => c.category === category
                ).length;
                return (
                  <TabsTrigger key={category} value={category}>
                    {CATEGORY_LABELS[category]} ({count})
                  </TabsTrigger>
                );
              })}
            </TabsList>
          </Tabs>
        </div>

        <div className="relative">
          <Search className="absolute left-3 top-1/2 transform -translate-y-1/2 w-4 h-4 text-muted-foreground" />
          <Input
            data-testid="search-input"
            placeholder="🔍 搜索AI能力（名称、描述、使用场景、技术栈...）"
            value={filterOptions.searchQuery}
            onChange={handleSearchChange}
            className="pl-10 pr-10"
            aria-label="搜索AI能力"
          />
          {filterOptions.searchQuery && (
            <button
              onClick={handleClearSearch}
              className="absolute right-3 top-1/2 transform -translate-y-1/2 text-muted-foreground hover:text-foreground"
              aria-label="清除搜索"
            >
              <X className="w-4 h-4" />
            </button>
          )}
        </div>
      </motion.div>

      <div
        className={cn(
          'grid gap-4',
          'grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4'
        )}
      >
        <AnimatePresence mode="popLayout">
          {filteredCapabilities.length > 0 ? (
            filteredCapabilities.map((capability, index) => {
              const isSelected = selectedCapabilities.includes(
                capability.type
              );
              const isRecommended = recommendedTypes.includes(capability.type);

              return (
                <motion.div
                  key={capability.type}
                  initial={{ opacity: 0, y: 20 }}
                  animate={{ opacity: 1, y: 0 }}
                  exit={{ opacity: 0, scale: 0.8 }}
                  transition={{ duration: 0.3, delay: index * 0.05 }}
                  layout
                >
                  <AICapabilityCard
                    capability={capability}
                    isSelected={isSelected}
                    isRecommended={isRecommended}
                    onToggle={() => handleToggle(capability.type)}
                    onShowDetail={handleShowDetail}
                    disabled={disabled}
                  />
                </motion.div>
              );
            })
          ) : (
            <motion.div
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              className="col-span-full"
            >
              <EmptyState
                searchQuery={filterOptions.searchQuery}
                onClearFilters={() =>
                  setFilterOptions({
                    selectedCategory: 'ALL',
                    searchQuery: '',
                    complexityFilter: [],
                    showRecommendedOnly: false,
                  })
                }
              />
            </motion.div>
          )}
        </AnimatePresence>
      </div>

      <AnimatePresence>
        {selectedCapabilities.length > 0 && showCostEstimate && (
          <AICapabilitySummary
            selectedCapabilities={selectedCapabilityObjects}
            onRemoveCapability={handleToggle}
            onClearAll={handleClearAll}
            onConfirm={() => {/* 选择确认 */}}

            showConfirmButton={true}
          />
        )}
      </AnimatePresence>

      <AICapabilityDetailModal
        capability={detailModalState.capability}
        open={detailModalState.open}
        onClose={handleCloseDetail}
        isSelected={
          detailModalState.capability
            ? selectedCapabilities.includes(detailModalState.capability.type)
            : false
        }
        onToggleSelection={handleToggleSelectionFromModal}
      />
    </div>
  );
}

/**
 * 空状态组件
 */
function EmptyState({
  searchQuery,
  onClearFilters,
}: {
  searchQuery: string;
  onClearFilters: () => void;
}) {
  return (
    <Card className="p-12">
      <div className="text-center space-y-4">
        <motion.div
          initial={{ scale: 0 }}
          animate={{ scale: 1 }}
          transition={{ type: 'spring', duration: 0.5 }}
          className="text-6xl"
        >
          🔍
        </motion.div>
        <h3 className="text-xl font-semibold text-gray-900">
          未找到匹配的AI能力
        </h3>
        {searchQuery && (
          <p className="text-muted-foreground">
            搜索关键词 &quot;{searchQuery}&quot; 没有匹配结果
          </p>
        )}
        <div className="space-y-2 text-sm text-muted-foreground">
          <p>试试以下操作：</p>
          <ul className="space-y-1 text-left max-w-xs mx-auto">
            <li>• 使用更简短的关键词</li>
            <li>• 切换到&quot;全部&quot;类别</li>
            <li>• 查看智能推荐</li>
            <li>• 尝试使用英文搜索</li>
          </ul>
        </div>
        <Button onClick={onClearFilters} variant="outline">
          <X className="w-4 h-4 mr-2" />
          清除筛选
        </Button>
      </div>
    </Card>
  );
}
