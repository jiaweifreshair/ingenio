'use client';

import React, { useState } from 'react';
import { useCredits } from '@/hooks/use-credits';
import { Button } from '@/components/ui/button';
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogDescription } from '@/components/ui/dialog';
import { CreditCard, Sparkles, LogIn } from 'lucide-react';
import { useRouter } from 'next/navigation';
import { getToken } from '@/lib/auth/token';

interface PaywallGuardProps {
  children: React.ReactNode;
  requiredCredits?: number;
  onBlocked?: () => void;
}

/**
 * 付费墙守卫组件
 * 包裹需要付费的功能按钮，未登录或余额不足时显示相应提示
 */
export function PaywallGuard({
  children,
  requiredCredits = 1,
  onBlocked,
}: PaywallGuardProps): React.ReactElement {
  const { credits, hasCredits } = useCredits();
  const [showDialog, setShowDialog] = useState(false);
  const router = useRouter();

  const isLoggedIn = !!getToken();

  const handleClick = (e: React.MouseEvent) => {
    if (!hasCredits(requiredCredits)) {
      e.preventDefault();
      e.stopPropagation();
      setShowDialog(true);
      onBlocked?.();
    }
  };

  return (
    <>
      <div onClick={handleClick} className="contents">
        {children}
      </div>

      <Dialog open={showDialog} onOpenChange={setShowDialog}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2">
              {isLoggedIn ? (
                <CreditCard className="h-5 w-5 text-purple-500" />
              ) : (
                <LogIn className="h-5 w-5 text-blue-500" />
              )}
              {isLoggedIn ? '生成次数不足' : '请先登录'}
            </DialogTitle>
            <DialogDescription>
              {isLoggedIn
                ? `您当前剩余 ${credits?.remaining ?? 0} 次生成机会，本次操作需要 ${requiredCredits} 次。`
                : '登录后即可使用 AI 代码生成功能，新用户可获得免费体验次数。'}
            </DialogDescription>
          </DialogHeader>

          <div className="space-y-4 py-4">
            <div className={`rounded-lg p-4 ${isLoggedIn ? 'bg-purple-50 dark:bg-purple-900/20' : 'bg-blue-50 dark:bg-blue-900/20'}`}>
              <p className={`text-sm ${isLoggedIn ? 'text-purple-800 dark:text-purple-200' : 'text-blue-800 dark:text-blue-200'}`}>
                {isLoggedIn
                  ? '购买套餐即可继续使用 AI 代码生成功能'
                  : '登录后可查看您的生成次数，或购买套餐获取更多次数'}
              </p>
            </div>

            <div className="flex gap-3">
              <Button
                variant="outline"
                className="flex-1"
                onClick={() => setShowDialog(false)}
              >
                稍后再说
              </Button>
              <Button
                className={`flex-1 ${isLoggedIn ? 'bg-gradient-to-r from-purple-600 to-blue-600' : 'bg-gradient-to-r from-blue-600 to-cyan-600'}`}
                onClick={() => {
                  setShowDialog(false);
                  router.push(isLoggedIn ? '/billing' : '/login');
                }}
              >
                {isLoggedIn ? (
                  <>
                    <Sparkles className="h-4 w-4 mr-2" />
                    立即购买
                  </>
                ) : (
                  <>
                    <LogIn className="h-4 w-4 mr-2" />
                    去登录
                  </>
                )}
              </Button>
            </div>
          </div>
        </DialogContent>
      </Dialog>
    </>
  );
}
