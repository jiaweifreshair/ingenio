import { useState } from 'react';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import { Label } from "@/components/ui/label";
import { MessageSquare, ThumbsUp, ThumbsDown, Loader2 } from "lucide-react";
import { useToast } from "@/hooks/use-toast";
import { useApi } from '@/hooks/use-api';

interface FeedbackDialogProps {
  taskId?: string; // 关联的任务ID或SandboxID
  trigger?: React.ReactNode;
}

export function FeedbackDialog({ taskId, trigger }: FeedbackDialogProps) {
  const [open, setOpen] = useState(false);
  const [feedback, setFeedback] = useState("");
  const [rating, setRating] = useState<"up" | "down" | null>(null);
  const { request, loading } = useApi();
  const { toast } = useToast();

  const handleSubmit = async () => {
    if (!feedback.trim() && !rating) {
      toast({
        title: "请填写反馈内容",
        description: "请至少选择评分或填写反馈意见",
        variant: "destructive",
      });
      return;
    }

    try {
      await request('/v1/feedback', {
        method: 'POST',
        body: JSON.stringify({
          taskId,
          content: feedback,
          rating,
          source: 'quick-preview',
        }),
      }, { showSuccessToast: false });

      toast({
        title: "感谢您的反馈！",
        description: "我们会根据您的建议不断改进。",
      });
      
      setOpen(false);
      setFeedback("");
      setRating(null);
    } catch {
      // 错误已由 useApi 处理
    }
  };

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        {trigger || (
          <Button variant="ghost" size="sm" className="gap-2">
            <MessageSquare className="w-4 h-4" />
            反馈
          </Button>
        )}
      </DialogTrigger>
      <DialogContent className="sm:max-w-[425px]">
        <DialogHeader>
          <DialogTitle>提供反馈</DialogTitle>
          <DialogDescription>
            您对这次生成的原型满意吗？您的反馈将帮助我们改进 AI 模型。
          </DialogDescription>
        </DialogHeader>
        <div className="grid gap-4 py-4">
          <div className="flex justify-center gap-4">
            <Button
              variant={rating === "up" ? "default" : "outline"}
              size="lg"
              className="w-24 gap-2"
              onClick={() => setRating("up")}
            >
              <ThumbsUp className="w-4 h-4" />
              满意
            </Button>
            <Button
              variant={rating === "down" ? "destructive" : "outline"}
              size="lg"
              className="w-24 gap-2"
              onClick={() => setRating("down")}
            >
              <ThumbsDown className="w-4 h-4" />
              不满意
            </Button>
          </div>
          <div className="grid gap-2">
            <Label htmlFor="feedback">详细建议 (可选)</Label>
            <Textarea
              id="feedback"
              placeholder="请描述您遇到的问题或改进建议..."
              value={feedback}
              onChange={(e) => setFeedback(e.target.value)}
              className="h-24"
            />
          </div>
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={() => setOpen(false)}>取消</Button>
          <Button onClick={handleSubmit} disabled={loading}>
            {loading && <Loader2 className="w-4 h-4 mr-2 animate-spin" />}
            提交反馈
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
