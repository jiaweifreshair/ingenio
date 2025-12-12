/**
 * 个人中心页面
 * 用户管理个人信息、应用和API密钥
 */
"use client"

import * as React from "react"
import { TopNav } from "@/components/layout/top-nav"
import { Footer } from "@/components/layout/footer"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { ProfileSection } from "@/components/account/profile-section"
import { AppsSection } from "@/components/account/apps-section"
import { ApiKeysSection } from "@/components/account/api-keys-section"

/**
 * 个人中心页面
 * Apple-style Design: Clean, focused, with subtle depths and blurs
 */
export default function AccountPage(): React.ReactElement {
  return (
    <div className="flex min-h-screen flex-col bg-[#F5F5F7] dark:bg-black" data-testid="account-page">
      {/* 顶部导航 */}
      <TopNav />

      {/* 主要内容 */}
      <main className="flex-1 w-full">
        <div className="container max-w-5xl mx-auto py-12 px-6 md:px-8">
          {/* 页面标题 */}
          <div className="mb-10 text-center md:text-left">
            <h1 className="text-4xl font-bold tracking-tight text-foreground mb-3">个人中心</h1>
            <p className="text-lg text-muted-foreground font-medium">
              管理您的账号、应用与开发密钥
            </p>
          </div>

          {/* Tab导航 - Apple Segmented Control Style */}
          <Tabs defaultValue="profile" className="space-y-8" data-testid="account-tabs">
            <div className="flex justify-center md:justify-start">
              <TabsList className="h-12 items-center justify-center rounded-full bg-muted/50 p-1 text-muted-foreground backdrop-blur-xl">
                <TabsTrigger 
                  value="profile" 
                  data-testid="profile-tab"
                  className="rounded-full px-6 py-2 text-sm font-medium transition-all data-[state=active]:bg-background data-[state=active]:text-foreground data-[state=active]:shadow-sm"
                >
                  个人信息
                </TabsTrigger>
                <TabsTrigger 
                  value="apps" 
                  data-testid="apps-tab"
                  className="rounded-full px-6 py-2 text-sm font-medium transition-all data-[state=active]:bg-background data-[state=active]:text-foreground data-[state=active]:shadow-sm"
                >
                  我的应用
                </TabsTrigger>
                <TabsTrigger 
                  value="api-keys" 
                  data-testid="api-keys-tab"
                  className="rounded-full px-6 py-2 text-sm font-medium transition-all data-[state=active]:bg-background data-[state=active]:text-foreground data-[state=active]:shadow-sm"
                >
                  API密钥
                </TabsTrigger>
              </TabsList>
            </div>

            {/* Tab内容区 - Add generic enter animation */}
            <div className="animate-in fade-in slide-in-from-bottom-4 duration-500">
              <TabsContent value="profile" className="mt-0">
                <ProfileSection />
              </TabsContent>

              <TabsContent value="apps" className="mt-0">
                <AppsSection />
              </TabsContent>

              <TabsContent value="api-keys" className="mt-0">
                <ApiKeysSection />
              </TabsContent>
            </div>
          </Tabs>
        </div>
      </main>

      {/* 页脚 */}
      <Footer />
    </div>
  )
}
