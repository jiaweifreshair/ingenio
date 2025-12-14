"use client";

import { useState, useEffect } from "react";
import { TopNav } from "@/components/layout/top-nav";
import { Footer } from "@/components/layout/footer";
import { CategorySidebar } from "@/components/templates/category-sidebar";
import { TemplateFilterBar } from "@/components/templates/template-filter-bar";
import { TemplateCard } from "@/components/templates/template-card";
import { TemplateDetailDialog } from "@/components/templates/template-detail-dialog";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import {
  Template,
  TemplateCategory,
  TemplateQueryParams,
  CategoryMeta,
  TemplatePageResponse,
} from "@/types/template";
import {
  getCategories,
  queryTemplates,
  favoriteTemplate as favoriteTemplateApi,
} from "@/lib/api/templates";
import { ChevronLeft, ChevronRight } from "lucide-react";
import { useToast } from "@/hooks/use-toast";

/**
 * 应用模版页面
 * 展示所有可用的应用模板，支持搜索、筛选、分类浏览
 */
export default function TemplatesPage(): React.ReactElement {
  const { toast } = useToast();

  // 分类数据
  const [categories, setCategories] = useState<CategoryMeta[]>([]);
  const [selectedCategory, setSelectedCategory] = useState<TemplateCategory>(
    TemplateCategory.ALL
  );

  // 筛选和分页
  const [filters, setFilters] = useState<TemplateQueryParams>({
    category: TemplateCategory.ALL,
    sortBy: "popular",
    page: 1,
    pageSize: 12,
  });

  // 模板数据
  const [templatesData, setTemplatesData] =
    useState<TemplatePageResponse | null>(null);
  const [loading, setLoading] = useState(true);

  // 详情弹窗
  const [selectedTemplate, setSelectedTemplate] = useState<Template | null>(
    null
  );
  const [detailDialogOpen, setDetailDialogOpen] = useState(false);

  // 加载分类
  useEffect(() => {
    const loadCategories = async () => {
      try {
        const data = await getCategories();
        setCategories(data);
      } catch (error) {
        console.error("加载分类失败:", error);
        toast({
          title: "加载失败",
          description: "无法加载分类数据，请刷新页面重试",
          variant: "destructive",
        });
      }
    };

    loadCategories();
  }, [toast]);

  // 加载模板列表
  useEffect(() => {
    const loadTemplates = async () => {
      setLoading(true);
      try {
        const data = await queryTemplates(filters);
        setTemplatesData(data);
      } catch (error) {
        console.error("加载模板失败:", error);
        toast({
          title: "加载失败",
          description: "无法加载模板数据，请刷新页面重试",
          variant: "destructive",
        });
      } finally {
        setLoading(false);
      }
    };

    loadTemplates();
  }, [filters, toast]);

  // 分类切换
  const handleCategoryChange = (category: TemplateCategory) => {
    setSelectedCategory(category);
    setFilters({
      ...filters,
      category,
      page: 1,
    });
  };

  // 筛选变更
  const handleFiltersChange = (newFilters: TemplateQueryParams) => {
    setFilters({
      ...newFilters,
      category: selectedCategory,
    });
  };

  // 模板卡片点击
  const handleTemplateClick = (template: Template) => {
    setSelectedTemplate(template);
    setDetailDialogOpen(true);
  };

  // 使用模板
  const handleUseTemplate = async (template: Template) => {
    try {
      // 跳转到创建页面，携带模板ID参数
      // RequirementForm会自动检测并加载模板
      // V2.0: 使用意图识别+双重选择机制的创建流程
      window.location.href = `/?templateId=${template.id}&templateName=${encodeURIComponent(template.name)}`;

      toast({
        title: "使用模板",
        description: `正在加载模板: ${template.name}`,
      });
    } catch (error) {
      console.error("使用模板失败:", error);
      toast({
        title: "使用失败",
        description: "无法加载模板，请重试",
        variant: "destructive",
      });
    }
  };

  // 收藏模板
  const handleFavoriteTemplate = async (template: Template) => {
    try {
      await favoriteTemplateApi(template.id);
      toast({
        title: "收藏成功",
        description: `已收藏模板: ${template.name}`,
      });
    } catch (error) {
      console.error("收藏模板失败:", error);
      toast({
        title: "收藏失败",
        description: "无法收藏模板，请稍后重试",
        variant: "destructive",
      });
    }
  };

  // 分页控制
  const handlePageChange = (newPage: number) => {
    setFilters({
      ...filters,
      page: newPage,
    });
    // 滚动到顶部
    window.scrollTo({ top: 0, behavior: "smooth" });
  };

  return (
    <div className="flex min-h-screen flex-col">
      {/* 顶部导航 */}
      <TopNav />

      {/* 主要内容 */}
      <main className="flex-1 bg-gradient-to-b from-background to-muted/20">
        <div className="container mx-auto px-4 py-8">
          {/* 页面标题 */}
          <div className="mb-8 text-center">
            <h1 className="mb-4 text-4xl font-bold">应用模版</h1>
            <p className="text-lg text-muted-foreground">
              精选应用模板，快速启动你的项目
            </p>
          </div>

          {/* 筛选栏 */}
          <div className="mb-6">
            <TemplateFilterBar
              filters={filters}
              onFiltersChange={handleFiltersChange}
            />
          </div>

          {/* 主要内容区 */}
          <div className="flex flex-col gap-6 lg:flex-row">
            {/* 左侧分类导航 */}
            <aside className="w-full lg:w-64 shrink-0">
              <CategorySidebar
                categories={categories}
                selectedCategory={selectedCategory}
                onCategoryChange={handleCategoryChange}
              />
            </aside>

            {/* 右侧模板列表 */}
            <div className="flex-1">
              {/* 加载状态 */}
              {loading && (
                <div className="grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-3">
                  {Array.from({ length: 6 }).map((_, index) => (
                    <div key={index} className="space-y-4">
                      <Skeleton className="h-48 w-full" />
                      <Skeleton className="h-4 w-3/4" />
                      <Skeleton className="h-4 w-full" />
                      <Skeleton className="h-4 w-1/2" />
                    </div>
                  ))}
                </div>
              )}

              {/* 模板列表 */}
              {!loading && templatesData && templatesData.items.length > 0 && (
                <>
                  <div className="mb-4 text-sm text-muted-foreground">
                    找到 {templatesData.total} 个模板
                  </div>
                  <div className="grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-3">
                    {templatesData.items.map((template) => (
                      <TemplateCard
                        key={template.id}
                        template={template}
                        onClick={handleTemplateClick}
                      />
                    ))}
                  </div>

                  {/* 分页控制 */}
                  {templatesData.totalPages > 1 && (
                    <div className="mt-8 flex items-center justify-center gap-2">
                      <Button
                        variant="outline"
                        size="icon"
                        disabled={filters.page === 1}
                        onClick={() => handlePageChange(filters.page! - 1)}
                      >
                        <ChevronLeft className="h-4 w-4" />
                      </Button>
                      <div className="flex items-center gap-1">
                        {Array.from(
                          { length: templatesData.totalPages },
                          (_, i) => i + 1
                        )
                          .filter((page) => {
                            // 显示当前页、首页、末页，以及当前页附近的页码
                            return (
                              page === 1 ||
                              page === templatesData.totalPages ||
                              Math.abs(page - filters.page!) <= 1
                            );
                          })
                          .map((page, index, array) => {
                            // 添加省略号
                            const showEllipsis =
                              index > 0 && page - array[index - 1] > 1;
                            return (
                              <>
                                {showEllipsis && (
                                  <span
                                    key={`ellipsis-${page}`}
                                    className="px-2 text-muted-foreground"
                                  >
                                    ...
                                  </span>
                                )}
                                <Button
                                  key={page}
                                  variant={
                                    page === filters.page
                                      ? "default"
                                      : "outline"
                                  }
                                  size="icon"
                                  onClick={() => handlePageChange(page)}
                                >
                                  {page}
                                </Button>
                              </>
                            );
                          })}
                      </div>
                      <Button
                        variant="outline"
                        size="icon"
                        disabled={filters.page === templatesData.totalPages}
                        onClick={() => handlePageChange(filters.page! + 1)}
                      >
                        <ChevronRight className="h-4 w-4" />
                      </Button>
                    </div>
                  )}
                </>
              )}

              {/* 空状态 */}
              {!loading && templatesData && templatesData.items.length === 0 && (
                <div className="flex flex-col items-center justify-center py-16 text-center">
                  <p className="mb-4 text-lg text-muted-foreground">
                    没有找到匹配的模板
                  </p>
                  <Button
                    variant="outline"
                    onClick={() =>
                      handleFiltersChange({
                        search: "",
                        difficulty: undefined,
                        platform: undefined,
                        sortBy: "popular",
                        page: 1,
                      })
                    }
                  >
                    清除筛选条件
                  </Button>
                </div>
              )}
            </div>
          </div>
        </div>
      </main>

      {/* 页脚 */}
      <Footer />

      {/* 模板详情弹窗 */}
      <TemplateDetailDialog
        open={detailDialogOpen}
        onClose={() => setDetailDialogOpen(false)}
        template={selectedTemplate}
        onUseTemplate={handleUseTemplate}
        onFavoriteTemplate={handleFavoriteTemplate}
      />
    </div>
  );
}
