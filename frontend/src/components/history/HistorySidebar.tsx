import { Sheet, SheetContent, SheetHeader, SheetTitle, SheetTrigger } from "@/components/ui/sheet";
import { Button } from "@/components/ui/button";
import { ScrollArea } from "@/components/ui/scroll-area";
import { Clock, RotateCcw, Eye } from "lucide-react";
import { AppSpecListItem, getAppSpecList } from "@/lib/api/appspec";
import { useEffect, useState } from "react";
import { formatDistanceToNow } from "date-fns";
import { zhCN } from "date-fns/locale";

interface HistorySidebarProps {
  currentAppSpecId?: string;
  onRestore?: (appSpecId: string) => void;
  onPreview?: (appSpecId: string) => void;
}

export function HistorySidebar({ 
  currentAppSpecId, 
  onRestore,
  onPreview 
}: HistorySidebarProps) {
  const [history, setHistory] = useState<AppSpecListItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [isOpen, setIsOpen] = useState(false);

  const loadHistory = async () => {
    setLoading(true);
    try {
      const response = await getAppSpecList({ 
        page: 1, 
        limit: 20, 
        sortBy: 'createdAt', 
        sortOrder: 'desc' 
      });
      if (response.success) {
        setHistory(response.data?.items || []);
      }
    } catch (error) {
      console.error('Failed to load history:', error);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (isOpen) {
      loadHistory();
    }
  }, [isOpen]);

  return (
    <Sheet open={isOpen} onOpenChange={setIsOpen}>
      <SheetTrigger asChild>
        <Button variant="outline" size="icon" className="fixed right-4 top-24 z-50 rounded-full shadow-md bg-background" title="版本历史">
          <Clock className="h-5 w-5 text-muted-foreground" />
        </Button>
      </SheetTrigger>
      <SheetContent className="w-[350px] sm:w-[450px]">
        <SheetHeader>
          <SheetTitle className="flex items-center gap-2">
            <Clock className="h-5 w-5" />
            版本时光机
          </SheetTitle>
        </SheetHeader>
        
        <div className="mt-6 h-[calc(100vh-120px)]">
          <ScrollArea className="h-full pr-4">
            {loading ? (
              <div className="text-center py-10 text-muted-foreground">加载中...</div>
            ) : history.length === 0 ? (
              <div className="text-center py-10 text-muted-foreground">暂无历史记录</div>
            ) : (
              <div className="space-y-4">
                {history.map((item) => (
                  <div 
                    key={item.id} 
                    className={`group relative flex flex-col gap-2 p-4 rounded-lg border transition-all hover:shadow-md ${
                      currentAppSpecId === item.id 
                        ? 'bg-primary/5 border-primary' 
                        : 'bg-card hover:bg-accent/50'
                    }`}
                  >
                    <div className="flex justify-between items-start">
                      <span className="font-semibold text-sm truncate max-w-[200px]">
                        {item.userRequirement || "无描述"}
                      </span>
                      <span className="text-xs text-muted-foreground whitespace-nowrap">
                        {formatDistanceToNow(new Date(item.createdAt), { addSuffix: true, locale: zhCN })}
                      </span>
                    </div>
                    
                    <div className="flex items-center gap-2 text-xs text-muted-foreground mt-1">
                      <span className="bg-secondary px-2 py-0.5 rounded-full">v{item.version}</span>
                      <span>{item.status}</span>
                    </div>

                    <div className="flex gap-2 mt-3 opacity-0 group-hover:opacity-100 transition-opacity">
                      {onPreview && (
                        <Button 
                          size="sm" 
                          variant="outline" 
                          className="h-7 text-xs flex-1"
                          onClick={() => onPreview(item.id)}
                        >
                          <Eye className="w-3 h-3 mr-1" /> 预览
                        </Button>
                      )}
                      {onRestore && (
                        <Button 
                          size="sm" 
                          className="h-7 text-xs flex-1"
                          onClick={() => onRestore(item.id)}
                        >
                          <RotateCcw className="w-3 h-3 mr-1" /> 恢复
                        </Button>
                      )}
                    </div>
                  </div>
                ))}
              </div>
            )}
          </ScrollArea>
        </div>
      </SheetContent>
    </Sheet>
  );
}
