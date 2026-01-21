/**
 * TopNav组件
 * 秒构AI顶部导航栏，包含通知图标、未读数量红点和移动端导航
 *
 * @author Ingenio Team
 * @since 1.0.0
 */
"use client";

import * as React from "react";
import Link from "next/link";
import NextImage from "next/image";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import {
  Sheet,
  SheetContent,
  SheetTrigger,
  SheetHeader,
  SheetTitle,
} from "@/components/ui/sheet";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { User, Bell, Menu, LogIn, UserPlus, LogOut, CreditCard } from "lucide-react";
import { getUnreadCount } from "@/lib/api/notifications";
import { getToken } from "@/lib/auth/token";
import { logout } from "@/lib/api/auth";
import { useRouter } from "next/navigation";
import { useLanguage } from "@/contexts/LanguageContext";
import { LanguageSwitcher } from "./language-switcher";

export function TopNav(): React.ReactElement {
  const router = useRouter();
  const { t } = useLanguage();
  const [unreadCount, setUnreadCount] = React.useState(0);
  const [mobileMenuOpen, setMobileMenuOpen] = React.useState(false);
  const [pollEnabled, setPollEnabled] = React.useState(true); // 控制轮询开关
  const [isLoggedIn, setIsLoggedIn] = React.useState(false); // 登录状态
  const [isLoggingOut, setIsLoggingOut] = React.useState(false); // 退出中状态

  /**
   * 检查用户登录状态
   * 通过Token存在性判断
   */
  React.useEffect(() => {
    const token = getToken();
    setIsLoggedIn(!!token);
  }, []);

  /**
   * 加载未读通知数量
   * 失败时默认显示0，不影响UI正常显示
   * 连续404错误时自动停止轮询
   */
  React.useEffect(() => {
    let consecutiveErrors = 0;
    const MAX_CONSECUTIVE_ERRORS = 3; // 连续3次404后停止轮询

    const loadUnreadCount = async () => {
      // 如果轮询已停止，不再请求
      if (!pollEnabled) {
        return;
      }

      try {
        const count = await getUnreadCount();
        setUnreadCount(count);
        consecutiveErrors = 0; // 成功时重置错误计数
      } catch (error) {
        consecutiveErrors++;
        console.warn(
          `[TopNav] 加载未读通知数量失败 (${consecutiveErrors}/${MAX_CONSECUTIVE_ERRORS}):`,
          error
        );
        setUnreadCount(0);

        // 连续失败3次后停止轮询
        if (consecutiveErrors >= MAX_CONSECUTIVE_ERRORS) {
          console.warn("[TopNav] 检测到API持续不可用，停止轮询");
          setPollEnabled(false);
        }
      }
    };

    loadUnreadCount();

    // 每30秒轮询一次（仅当轮询开启时）
    const interval = setInterval(() => {
      if (pollEnabled) {
        loadUnreadCount();
      }
    }, 30000);

    return () => clearInterval(interval);
  }, [pollEnabled]);

  /**
   * 处理退出登录
   * 调用后端API并清除本地Token，跳转到首页
   */
  const handleLogout = async () => {
    setIsLoggingOut(true);
    try {
      await logout();
      setIsLoggedIn(false);
      setMobileMenuOpen(false);
      router.push("/");
    } catch (error) {
      console.error("退出登录失败:", error);
    } finally {
      setIsLoggingOut(false);
    }
  };

  /**
   * 导航链接配置
   *
   * 设计理念：应用管理型导航（方案B）
   * - 直达Dashboard核心工作台
   * - 应用模版快速启动
   * - 产品功能介绍
   */
  const navLinks = [
    { href: "/dashboard", label: t('nav.projects') },
    { href: "/templates", label: t('nav.templates') },
    ...(process.env.NODE_ENV === "development"
      ? [
          { href: "/benchmarks", label: "Benchmarks 对照" },
          { href: "/examples", label: "TSX 示例" },
        ]
      : []),
  ];

  return (
    <header className="sticky top-0 z-50 w-full bg-background/70 backdrop-blur-xl border-none supports-[backdrop-filter]:bg-background/60">
      <div className="container flex h-14 max-w-screen-2xl items-center">
        {/* Logo和品牌名 */}
        <div className="mr-8 flex">
          <Link href="/" className="flex items-center space-x-2">
            <NextImage src="/logo.png" alt="Ingenio Logo" width={40} height={40} className="h-10 w-10" />
            <span className="hidden font-semibold sm:inline-block text-foreground tracking-tight">Ingenio 妙构</span>
          </Link>
        </div>

        {/* 桌面端导航菜单 */}
        <nav className="hidden lg:flex items-center space-x-8 text-sm font-medium">
          {navLinks.map((link) => (
            <Link
              key={link.href}
              href={link.href}
              className="transition-colors text-muted-foreground/80 hover:text-foreground"
            >
              {link.label}
            </Link>
          ))}
        </nav>

        {/* 移动端菜单按钮（使用Sheet组件） */}
        <div className="lg:hidden flex-1">
          <Sheet open={mobileMenuOpen} onOpenChange={setMobileMenuOpen}>
            <SheetTrigger asChild>
              <Button variant="ghost" size="icon" className="ml-2 rounded-full">
                <Menu className="h-5 w-5" />
                <span className="sr-only">{t('nav.menu')}</span>
              </Button>
            </SheetTrigger>
            <SheetContent side="left" className="w-[280px] sm:w-[320px]">
              <SheetHeader>
                <SheetTitle>Ingenio 妙构</SheetTitle>
              </SheetHeader>

              {/* 导航链接列表 */}
              <nav className="flex flex-col mt-6 space-y-1">
                {navLinks.map((link) => (
                  <Link
                    key={link.href}
                    href={link.href}
                    onClick={() => setMobileMenuOpen(false)}
                    className="px-4 py-3 text-base font-medium rounded-md transition-colors hover:bg-accent hover:text-accent-foreground"
                  >
                    {link.label}
                  </Link>
                ))}
              </nav>

              {/* 分隔线 */}
              <div className="my-4 border-t" />

              {/* 快捷操作按钮 */}
              <div className="flex flex-col space-y-2">
                <div className="px-4 py-2">
                    <LanguageSwitcher />
                </div>
                {isLoggedIn ? (
                  // 已登录：显示免费开始、通知、个人中心
                  <>
                    <Button asChild className="w-full rounded-full">
                      <Link
                        href="/"
                        data-location="mobile-menu"
                        onClick={() => setMobileMenuOpen(false)}
                      >
                        {t('nav.getStarted')}
                      </Link>
                    </Button>
                    <Button
                      variant="outline"
                      className="w-full justify-start rounded-full"
                      asChild
                    >
                      <Link
                        href="/notifications"
                        onClick={() => setMobileMenuOpen(false)}
                      >
                        <Bell className="h-4 w-4 mr-2" />
                        {t('nav.notifications')}
                        {unreadCount > 0 && (
                          <Badge variant="destructive" className="ml-auto">
                            {unreadCount > 99 ? "99+" : unreadCount}
                          </Badge>
                        )}
                      </Link>
                    </Button>
                    <Button
                      variant="outline"
                      className="w-full justify-start rounded-full"
                      asChild
                    >
                      <Link
                        href="/billing"
                        onClick={() => setMobileMenuOpen(false)}
                      >
                        <CreditCard className="h-4 w-4 mr-2" />
                        {t('nav.billing')}
                      </Link>
                    </Button>
                    <Button
                      variant="outline"
                      className="w-full justify-start rounded-full"
                      asChild
                    >
                      <Link
                        href="/account"
                        onClick={() => setMobileMenuOpen(false)}
                      >
                        <User className="h-4 w-4 mr-2" />
                        {t('nav.profile')}
                      </Link>
                    </Button>
                    <Button
                      variant="outline"
                      className="w-full justify-start text-destructive hover:text-destructive rounded-full"
                      onClick={handleLogout}
                      disabled={isLoggingOut}
                    >
                      <LogOut className="h-4 w-4 mr-2" />
                      {isLoggingOut ? t('nav.loggingOut') : t('nav.logout')}
                    </Button>
                  </>
                ) : (
                  // 未登录：显示登录、注册按钮
                  <>
                    <Button asChild className="w-full rounded-full">
                      <Link
                        href="/login"
                        onClick={() => setMobileMenuOpen(false)}
                      >
                        <LogIn className="h-4 w-4 mr-2" />
                        {t('nav.login')}
                      </Link>
                    </Button>
                    <Button
                      variant="outline"
                      className="w-full rounded-full"
                      asChild
                    >
                      <Link
                        href="/register"
                        onClick={() => setMobileMenuOpen(false)}
                      >
                        <UserPlus className="h-4 w-4 mr-2" />
                        {t('nav.register')}
                      </Link>
                    </Button>
                  </>
                )}
              </div>
            </SheetContent>
          </Sheet>
        </div>

        {/* 桌面端CTA按钮 */}
        <div className="hidden lg:flex flex-1 items-center justify-end space-x-4">
          <LanguageSwitcher />
          {isLoggedIn ? (
            // 已登录：显示通知、个人中心、免费开始
            <>
              {/* 通知中心下拉 */}
              <DropdownMenu>
                <DropdownMenuTrigger asChild>
                  <Button variant="ghost" size="icon" className="relative rounded-full w-8 h-8">
                    <Bell className="h-4 w-4 text-muted-foreground" />
                    {unreadCount > 0 && (
                      <Badge
                        variant="destructive"
                        className="absolute -top-1 -right-1 h-4 w-4 flex items-center justify-center p-0 text-[10px]"
                      >
                        {unreadCount > 99 ? "99+" : unreadCount}
                      </Badge>
                    )}
                    <span className="sr-only">{t('nav.notificationCenter')} ({unreadCount})</span>
                  </Button>
                </DropdownMenuTrigger>
                <DropdownMenuContent align="end" className="w-80 z-[60]">
                  <DropdownMenuLabel>{t('nav.notifications')}</DropdownMenuLabel>
                  <DropdownMenuSeparator />
                  <div className="max-h-64 overflow-y-auto">
                    {unreadCount > 0 ? (
                      <div className="p-3 text-sm text-muted-foreground">
                        {t('nav.unreadNotifications')}: {unreadCount}
                      </div>
                    ) : (
                      <div className="p-3 text-sm text-muted-foreground text-center">
                        {t('nav.noNotifications')}
                      </div>
                    )}
                  </div>
                  <DropdownMenuSeparator />
                  <DropdownMenuItem asChild>
                    <Link href="/notifications" className="w-full cursor-pointer">
                      {t('nav.viewAllNotifications')}
                    </Link>
                  </DropdownMenuItem>
                </DropdownMenuContent>
              </DropdownMenu>

              {/* 账单中心按钮 */}
              <Button variant="ghost" size="icon" asChild className="rounded-full w-8 h-8">
                <Link href="/billing">
                  <CreditCard className="h-4 w-4 text-muted-foreground" />
                  <span className="sr-only">{t('nav.billing')}</span>
                </Link>
              </Button>

              {/* 个人中心按钮 - 登录后显示绿色 */}
              <Button variant="ghost" size="icon" asChild className="rounded-full w-8 h-8">
                <Link href="/account">
                  <User className="h-4 w-4 text-green-500" />
                  <span className="sr-only">{t('nav.profile')}</span>
                </Link>
              </Button>

              {/* 退出登录按钮 */}
              <Button
                variant="ghost"
                size="icon"
                onClick={handleLogout}
                disabled={isLoggingOut}
                title={t('nav.logout')}
                className="rounded-full w-8 h-8 text-muted-foreground hover:text-destructive"
              >
                <LogOut className="h-4 w-4" />
                <span className="sr-only">{t('nav.logout')}</span>
              </Button>
            </>
          ) : (
            // 未登录：显示登录、注册按钮
            <>
              <Button variant="ghost" size="sm" asChild className="rounded-md text-muted-foreground hover:text-foreground font-medium">
                <Link href="/login">
                  {t('nav.login')}
                </Link>
              </Button>

              <Button asChild size="sm" className="rounded-md px-4 font-medium">
                <Link href="/register">
                  {t('nav.register')}
                </Link>
              </Button>
            </>
          )}
        </div>
      </div>
    </header>
  );
}
