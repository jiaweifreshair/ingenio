"use client";

import { cn } from "@/lib/utils";
import { CategoryMeta, TemplateCategory } from "@/types/template";
import { Badge } from "@/components/ui/badge";

/**
 * 分类侧边栏组件属性
 */
export interface CategorySidebarProps {
  /** 分类列表 */
  categories: CategoryMeta[];
  /** 当前选中的分类 */
  selectedCategory: TemplateCategory;
  /** 分类切换回调 */
  onCategoryChange: (category: TemplateCategory) => void;
}

/**
 * 分类侧边栏组件
 * 用于模板库页面的分类导航
 */
export function CategorySidebar({
  categories,
  selectedCategory,
  onCategoryChange,
}: CategorySidebarProps): React.ReactElement {
  return (
    <div className="w-full rounded-lg border border-border/50 bg-card/50 p-4 backdrop-blur-sm">
      <h2 className="mb-4 text-lg font-semibold">模板分类</h2>
      <nav className="space-y-1">
        {categories.map((category) => {
          const isSelected = selectedCategory === category.id;
          return (
            <button
              key={category.id}
              onClick={() => onCategoryChange(category.id)}
              className={cn(
                "flex w-full items-center justify-between rounded-lg px-4 py-3 text-left transition-all",
                isSelected
                  ? "bg-primary text-primary-foreground shadow-md"
                  : "hover:bg-muted/50"
              )}
            >
              <div className="flex items-center gap-3">
                <span className="text-xl">{category.icon}</span>
                <span className="font-medium">{category.name}</span>
              </div>
              <Badge
                variant={isSelected ? "secondary" : "outline"}
                className={cn(
                  "ml-auto",
                  isSelected && "bg-primary-foreground/20 text-primary-foreground"
                )}
              >
                {category.count}
              </Badge>
            </button>
          );
        })}
      </nav>
    </div>
  );
}
