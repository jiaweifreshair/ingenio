'use client';

import { useCallback } from 'react';
import { useRouter } from 'next/navigation';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { LoginForm } from './login-form';
import { OAuthButtons } from './oauth-buttons';
import { LogIn } from 'lucide-react';

/**
 * 登录对话框组件
 *
 * 功能：
 * - 弹窗形式展示登录表单
 * - 支持受控模式（外部控制open状态）
 * - 登录成功后自动关闭并执行回调
 * - 支持OAuth第三方登录
 *
 * 使用场景：
 * - 未登录用户尝试执行需要登录的操作时弹出
 * - 首页/创建页面检测到未登录状态时引导登录
 *
 * @author Ingenio Team
 * @since 1.0.0
 */

interface LoginDialogProps {
  /** 对话框是否打开 */
  open: boolean;
  /** 对话框状态变化回调 */
  onOpenChange: (open: boolean) => void;
  /** 登录成功回调 */
  onSuccess?: () => void;
  /** 自定义标题 */
  title?: string;
  /** 自定义描述 */
  description?: string;
}

export function LoginDialog({
  open,
  onOpenChange,
  onSuccess,
  title = '登录以继续',
  description = '登录后即可使用AI生成功能',
}: LoginDialogProps) {
  const router = useRouter();

  /**
   * 处理登录成功
   */
  const handleLoginSuccess = useCallback(() => {
    // 关闭对话框
    onOpenChange(false);
    // 执行成功回调
    onSuccess?.();
  }, [onOpenChange, onSuccess]);

  /**
   * 跳转到注册页面
   */
  const handleGoToRegister = () => {
    onOpenChange(false);
    router.push('/register');
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-[425px]">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <LogIn className="h-5 w-5 text-primary" />
            {title}
          </DialogTitle>
          <DialogDescription>
            {description}
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-6 py-4">
          {/* 登录表单 */}
          <LoginForm onSuccess={handleLoginSuccess} />

          {/* 分割线 */}
          <div className="relative">
            <div className="absolute inset-0 flex items-center">
              <span className="w-full border-t" />
            </div>
            <div className="relative flex justify-center text-xs uppercase">
              <span className="bg-background px-2 text-muted-foreground">
                或使用以下方式登录
              </span>
            </div>
          </div>

          {/* OAuth登录按钮 */}
          <OAuthButtons />

          {/* 注册引导 */}
          <div className="text-center text-sm text-muted-foreground">
            还没有账号？{' '}
            <Button
              variant="link"
              className="p-0 h-auto text-primary"
              onClick={handleGoToRegister}
            >
              立即注册
            </Button>
          </div>
        </div>
      </DialogContent>
    </Dialog>
  );
}
