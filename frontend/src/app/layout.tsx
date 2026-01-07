import type { Metadata } from "next";
import "@/styles/globals.css";
import { cn } from "@/lib/utils";
import { Toaster } from "@/components/ui/toaster";

/**
 * 秒构AI根布局
 * Next.js 15 App Router根布局组件
 */

export const metadata: Metadata = {
  title: "Ingenio 妙构 - AI原生应用孵化器",
  description: "不仅仅是快，更是精妙。Ingenio (妙构) 专为校园创业与企业交付打造，通过 G3 红蓝博弈引擎，构建高质量、自修复的 Java 全栈应用。",
  keywords: [
    "应用生成器",
    "No-code",
    "AI",
    "校园",
    "企业级",
    "Java",
    "Spring Boot",
    "Ingenio",
    "妙构",
  ],
  authors: [{ name: "Ingenio Team" }],
  creator: "Ingenio Team",
  icons: {
    icon: "/logo.png",
  },
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
