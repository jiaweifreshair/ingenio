"use client";

import { Input } from "@/components/ui/input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  TemplateDifficulty,
  TargetPlatform,
  TemplateQueryParams,
} from "@/types/template";
import { Search } from "lucide-react";

/**
 * 筛选栏组件属性
 */
export interface TemplateFilterBarProps {
  /** 当前筛选参数 */
  filters: TemplateQueryParams;
  /** 筛选变更回调 */
  onFiltersChange: (filters: TemplateQueryParams) => void;
}

/**
 * 模板筛选栏组件
 * 提供搜索、难度、平台、排序等筛选功能
 */
export function TemplateFilterBar({
  filters,
  onFiltersChange,
}: TemplateFilterBarProps): React.ReactElement {
  return (
    <div className="space-y-4 rounded-lg border border-border/50 bg-card/50 p-4 backdrop-blur-sm">
      {/* 搜索框 */}
      <div className="relative">
        <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
        <Input
          data-testid="search-input"
          type="text"
          placeholder="搜索模板名称、描述、标签..."
          className="pl-10"
          value={filters.search || ""}
          onChange={(e) =>
            onFiltersChange({ ...filters, search: e.target.value, page: 1 })
          }
        />
      </div>

      {/* 筛选器行 */}
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
        {/* 难度筛选 */}
        <div>
          <label className="mb-2 block text-sm font-medium">难度</label>
          <Select
            data-testid="difficulty-filter"
            value={filters.difficulty || "all"}
            onValueChange={(value) =>
              onFiltersChange({
                ...filters,
                difficulty:
                  value === "all" ? undefined : (value as TemplateDifficulty),
                page: 1,
              })
            }
          >
            <SelectTrigger>
              <SelectValue placeholder="选择难度" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="all">全部难度</SelectItem>
              <SelectItem value={TemplateDifficulty.SIMPLE}>简单</SelectItem>
              <SelectItem value={TemplateDifficulty.MEDIUM}>中等</SelectItem>
              <SelectItem value={TemplateDifficulty.COMPLEX}>复杂</SelectItem>
            </SelectContent>
          </Select>
        </div>

        {/* 平台筛选 */}
        <div>
          <label className="mb-2 block text-sm font-medium">平台</label>
          <Select
            data-testid="platform-filter"
            value={filters.platform || "all"}
            onValueChange={(value) =>
              onFiltersChange({
                ...filters,
                platform:
                  value === "all" ? undefined : (value as TargetPlatform),
                page: 1,
              })
            }
          >
            <SelectTrigger>
              <SelectValue placeholder="选择平台" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="all">全部平台</SelectItem>
              <SelectItem value={TargetPlatform.ANDROID}>Android</SelectItem>
              <SelectItem value={TargetPlatform.IOS}>iOS</SelectItem>
              <SelectItem value={TargetPlatform.WEB}>Web</SelectItem>
              <SelectItem value={TargetPlatform.WECHAT}>
                微信小程序
              </SelectItem>
              <SelectItem value={TargetPlatform.HARMONY}>鸿蒙</SelectItem>
            </SelectContent>
          </Select>
        </div>

        {/* 排序方式 */}
        <div>
          <label className="mb-2 block text-sm font-medium">排序</label>
          <Select
            data-testid="sort-filter"
            value={filters.sortBy || "popular"}
            onValueChange={(value) =>
              onFiltersChange({
                ...filters,
                sortBy: value as "newest" | "popular" | "rating",
                page: 1,
              })
            }
          >
            <SelectTrigger>
              <SelectValue placeholder="排序方式" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="popular">最热门</SelectItem>
              <SelectItem value="newest">最新发布</SelectItem>
              <SelectItem value="rating">评分最高</SelectItem>
            </SelectContent>
          </Select>
        </div>

        {/* 占位 */}
        <div className="hidden lg:block" />
      </div>

      {/* 当前筛选条件显示 */}
      {(filters.search ||
        filters.difficulty ||
        filters.platform ||
        filters.sortBy) && (
        <div className="flex flex-wrap items-center gap-2 text-sm">
          <span className="text-muted-foreground">当前筛选:</span>
          {filters.search && (
            <span className="rounded-md bg-muted px-2 py-1">
              关键词: {filters.search}
            </span>
          )}
          {filters.difficulty && (
            <span className="rounded-md bg-muted px-2 py-1">
              难度: {filters.difficulty === TemplateDifficulty.SIMPLE && "简单"}
              {filters.difficulty === TemplateDifficulty.MEDIUM && "中等"}
              {filters.difficulty === TemplateDifficulty.COMPLEX && "复杂"}
            </span>
          )}
          {filters.platform && (
            <span className="rounded-md bg-muted px-2 py-1">
              平台: {filters.platform}
            </span>
          )}
          <button
            onClick={() =>
              onFiltersChange({
                search: "",
                difficulty: undefined,
                platform: undefined,
                sortBy: "popular",
                page: 1,
              })
            }
            className="text-primary hover:underline"
          >
            清除筛选
          </button>
        </div>
      )}
    </div>
  );
}
