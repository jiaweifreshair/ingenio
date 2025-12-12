import type { Metadata } from "next";
import "@/styles/globals.css";
import { cn } from "@/lib/utils";
import { Toaster } from "@/components/ui/toaster";

/**
 * 秒构AI根布局
 * Next.js 15 App Router根布局组件
 */

export const metadata: Metadata = {
  title: "秒构AI - 人人可用的应用生成器",
  description: "为校园而生，用\"选择 + 填空\"在 30 分钟做出可发布的应用",
  keywords: [
    "应用生成器",
    "No-code",
    "AI",
    "校园",
    "应用开发",
    "秒构",
    "Ingenio",
  ],
  authors: [{ name: "Ingenio Team" }],
  creator: "Ingenio Team",
};

/**
 * viewport配置（Next.js 15+需要单独导出）
 */
export const viewport = {
  width: "device-width",
  initialScale: 1,
  maximumScale: 1,
  themeColor: [
    { media: "(prefers-color-scheme: light)", color: "#2bb673" },
    { media: "(prefers-color-scheme: dark)", color: "#2bb673" },
  ],
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>): React.ReactElement {
  return (
    <html lang="zh-CN" suppressHydrationWarning>
      <body className={cn("min-h-screen font-sans antialiased")}>
        {children}
        <Toaster />
      </body>
    </html>
  );
}
