'use client';

import { Input } from '@/components/ui/input';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import { Button } from '@/components/ui/button';
import { Search, SlidersHorizontal } from 'lucide-react';
import { ProjectStatus } from '@/types/project';

/**
 * 筛选栏组件
 * 提供搜索、状态筛选、排序功能
 */
interface FilterBarProps {
  keyword: string;
  status: string;
  onKeywordChange: (keyword: string) => void;
  onStatusChange: (status: string) => void;
  onSearch: () => void;
}

export function FilterBar({
  keyword,
  status,
  onKeywordChange,
  onStatusChange,
  onSearch,
}: FilterBarProps): React.ReactElement {
  return (
    <div className="flex flex-col sm:flex-row gap-4 items-center">
      {/* 搜索框 */}
      <div className="relative flex-1 w-full">
        <Search className="absolute left-3 top-1/2 transform -translate-y-1/2 h-4 w-4 text-muted-foreground" />
        <Input
          type="text"
          placeholder="搜索应用名称或描述..."
          value={keyword}
          onChange={(e) => onKeywordChange(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter') {
              onSearch();
            }
          }}
          className="pl-10 pr-4"
        />
      </div>

      {/* 状态筛选 */}
      <Select value={status} onValueChange={onStatusChange}>
        <SelectTrigger className="w-full sm:w-[180px]">
          <SlidersHorizontal className="h-4 w-4 mr-2" />
          <SelectValue placeholder="全部状态" />
        </SelectTrigger>
        <SelectContent>
          <SelectItem value="all">全部状态</SelectItem>
          <SelectItem value={ProjectStatus.DRAFT}>草稿</SelectItem>
          <SelectItem value={ProjectStatus.GENERATING}>生成中</SelectItem>
          <SelectItem value={ProjectStatus.COMPLETED}>生成完成</SelectItem>
          <SelectItem value={ProjectStatus.ARCHIVED}>已归档</SelectItem>
        </SelectContent>
      </Select>

      {/* 搜索按钮 */}
      <Button onClick={onSearch} className="w-full sm:w-auto">
        搜索
      </Button>
    </div>
  );
}
