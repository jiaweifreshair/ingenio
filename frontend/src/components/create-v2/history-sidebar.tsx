'use client';

import React from 'react';
import { Button } from '@/components/ui/button';
import { ScrollArea } from '@/components/ui/scroll-area';
import { History, Clock, ChevronRight, Trash2 } from 'lucide-react';
import { cn } from '@/lib/utils';
import { formatDistanceToNow } from 'date-fns';
import { zhCN } from 'date-fns/locale';

export interface HistoryItem {
  id: string;
  requirement: string;
  timestamp: number;
  status: 'draft' | 'completed';
}

interface HistorySidebarProps {
  items: HistoryItem[];
  onSelect: (item: HistoryItem) => void;
  onDelete?: (id: string) => void;
  className?: string;
}

export function HistorySidebar({ 
  items, 
  onSelect, 
  onDelete,
  className 
}: HistorySidebarProps) {
  return (
    <div className={cn("flex flex-col h-full border-r bg-muted/10", className)}>
      <div className="p-4 border-b flex items-center justify-between">
        <div className="flex items-center gap-2 font-semibold text-sm text-muted-foreground">
          <History className="w-4 h-4" />
          <span>历史记录</span>
        </div>
        <span className="text-xs bg-muted px-2 py-0.5 rounded-full text-muted-foreground">
          {items.length}
        </span>
      </div>

      <ScrollArea className="flex-1">
        <div className="p-3 space-y-2">
          {items.length === 0 ? (
            <div className="text-center py-8 text-muted-foreground text-sm">
              <Clock className="w-8 h-8 mx-auto mb-2 opacity-20" />
              <p>暂无历史记录</p>
            </div>
          ) : (
            items.map((item) => (
              <div
                key={item.id}
                className="group relative flex flex-col gap-1 p-3 rounded-lg hover:bg-accent cursor-pointer transition-colors border border-transparent hover:border-border/50"
                onClick={() => onSelect(item)}
              >
                <div className="flex items-start justify-between gap-2">
                  <p className="text-sm font-medium line-clamp-2 leading-relaxed text-foreground/90">
                    {item.requirement}
                  </p>
                  {onDelete && (
                    <Button
                      variant="ghost"
                      size="icon"
                      className="h-6 w-6 opacity-0 group-hover:opacity-100 transition-opacity -mr-1 -mt-1 text-muted-foreground hover:text-destructive"
                      onClick={(e) => {
                        e.stopPropagation();
                        onDelete(item.id);
                      }}
                    >
                      <Trash2 className="w-3 h-3" />
                    </Button>
                  )}
                </div>
                
                <div className="flex items-center justify-between mt-1">
                  <span className="text-[10px] text-muted-foreground/70">
                    {formatDistanceToNow(item.timestamp, { addSuffix: true, locale: zhCN })}
                  </span>
                  <ChevronRight className="w-3 h-3 text-muted-foreground/50 opacity-0 group-hover:opacity-100 transition-all transform group-hover:translate-x-0.5" />
                </div>
              </div>
            ))
          )}
        </div>
      </ScrollArea>
    </div>
  );
}
