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
import { User, Bell, Menu, LogIn, UserPlus, LogOut } from "lucide-react";
import { getUnreadCount } from "@/lib/api/notifications";
import { getToken } from "@/lib/auth/token";
import { logout } from "@/lib/api/auth";
import { useRouter } from "next/navigation";

export function TopNav(): React.ReactElement {
  const router = useRouter();
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
    { href: "/dashboard", label: "我的项目" },   // ✅ 修复: /projects → /dashboard（实际路由）
    { href: "/templates", label: "应用模版" },     // ✅ 保留: 快速启动
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
                <span className="sr-only">打开菜单</span>
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
                {isLoggedIn ? (
                  // 已登录：显示免费开始、通知、个人中心
                  <>
                    <Button asChild className="w-full rounded-full">
                      <Link
                        href="/"
                        data-location="mobile-menu"
                        onClick={() => setMobileMenuOpen(false)}
                      >
                        免费开始
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
                        通知
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
                        href="/account"
                        onClick={() => setMobileMenuOpen(false)}
                      >
                        <User className="h-4 w-4 mr-2" />
                        个人中心
                      </Link>
                    </Button>
                    <Button
                      variant="outline"
                      className="w-full justify-start text-destructive hover:text-destructive rounded-full"
                      onClick={handleLogout}
                      disabled={isLoggingOut}
                    >
                      <LogOut className="h-4 w-4 mr-2" />
                      {isLoggingOut ? "退出中..." : "退出登录"}
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
                        登录
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
                        注册
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
          {isLoggedIn ? (
            // 已登录：显示通知、个人中心、免费开始
            <>
              {/* 通知中心按钮 */}
              <Button variant="ghost" size="icon" asChild className="relative rounded-full w-8 h-8">
                <Link href="/notifications">
                  <Bell className="h-4 w-4 text-muted-foreground" />
                  {unreadCount > 0 && (
                    <Badge
                      variant="destructive"
                      className="absolute -top-1 -right-1 h-4 w-4 flex items-center justify-center p-0 text-[10px]"
                    >
                      {unreadCount > 99 ? "99+" : unreadCount}
                    </Badge>
                  )}
                  <span className="sr-only">通知中心 ({unreadCount} 条未读)</span>
                </Link>
              </Button>

              {/* 个人中心按钮 */}
              <Button variant="ghost" size="icon" asChild className="rounded-full w-8 h-8">
                <Link href="/account">
                  <User className="h-4 w-4 text-muted-foreground" />
                  <span className="sr-only">个人中心</span>
                </Link>
              </Button>

              {/* 免费开始按钮 */}
              <Button asChild size="sm" className="rounded-md px-4 font-medium">
                <Link href="/" data-location="header">
                  免费开始
                </Link>
              </Button>

              {/* 退出登录按钮 */}
              <Button
                variant="ghost"
                size="icon"
                onClick={handleLogout}
                disabled={isLoggingOut}
                title="退出登录"
                className="rounded-full w-8 h-8 text-muted-foreground hover:text-destructive"
              >
                <LogOut className="h-4 w-4" />
                <span className="sr-only">退出登录</span>
              </Button>
            </>
          ) : (
            // 未登录：显示登录、注册按钮
            <>
              <Button variant="ghost" size="sm" asChild className="rounded-md text-muted-foreground hover:text-foreground font-medium">
                <Link href="/login">
                  登录
                </Link>
              </Button>

              <Button asChild size="sm" className="rounded-md px-4 font-medium">
                <Link href="/register">
                  注册
                </Link>
              </Button>
            </>
          )}
        </div>
      </div>
    </header>
  );
}
