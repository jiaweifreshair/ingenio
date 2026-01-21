'use client';

import React, { useState, useEffect } from 'react';
import { TopNav } from '@/components/layout/top-nav';
import { Footer } from '@/components/layout/footer';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { CreditBalance } from './components/credit-balance';
import { PackageCard } from './components/package-card';
import { OrderHistory } from './components/order-history';
import { PaymentDialog } from './components/payment-dialog';
import { useRouter } from 'next/navigation';
import { getPackages, CreditPackage } from '@/lib/api/billing';
import { useCredits } from '@/hooks/use-credits';
import { getToken } from '@/lib/auth/token';

/**
 * 计费中心页面
 * 显示用户余额、套餐购买、订单历史
 */
export default function BillingPage(): React.ReactElement {
  const router = useRouter();
  const { refetch: refetchCredits } = useCredits();
  const [selectedPackage, setSelectedPackage] = useState<string | null>(null);
  const [showPaymentDialog, setShowPaymentDialog] = useState(false);
  const [packages, setPackages] = useState<CreditPackage[]>([]);

  // 未登录跳转（直接检查token）
  useEffect(() => {
    const token = getToken();
    if (!token) {
      router.push('/login?redirect=/billing');
    }
  }, [router]);

  // 获取套餐列表
  useEffect(() => {
    async function fetchPackages() {
      try {
        const response = await getPackages();
        if (response.success && response.data) {
          setPackages(response.data);
        }
      } catch (error) {
        console.error('获取套餐列表失败:', error);
      }
    }
    fetchPackages();
  }, []);

  const handlePaymentSuccess = () => {
    refetchCredits();
  };

  return (
    <div className="flex min-h-screen flex-col bg-[#F5F5F7] dark:bg-black">
      <TopNav />

      <main className="flex-1 w-full">
        <div className="container max-w-5xl mx-auto py-12 px-6">
          {/* 页面标题 */}
          <div className="mb-10 text-center">
            <h1 className="text-4xl font-bold tracking-tight mb-3">计费中心</h1>
            <p className="text-lg text-muted-foreground">
              购买生成次数，解锁 AI 代码生成能力
            </p>
          </div>

          {/* 余额显示 */}
          <CreditBalance className="mb-8" />

          {/* Tab 导航 */}
          <Tabs defaultValue="packages" className="space-y-8">
            <TabsList className="h-12 rounded-full bg-muted/50 p-1">
              <TabsTrigger value="packages" className="rounded-full px-6">
                购买套餐
              </TabsTrigger>
              <TabsTrigger value="orders" className="rounded-full px-6">
                订单记录
              </TabsTrigger>
            </TabsList>

            <TabsContent value="packages">
              <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
                {packages.map((pkg, index) => (
                  <PackageCard
                    key={pkg.code}
                    code={pkg.code}
                    name={pkg.name}
                    credits={pkg.credits}
                    price={pkg.price}
                    popular={index === 1}
                    onSelect={() => {
                      setSelectedPackage(pkg.code);
                      setShowPaymentDialog(true);
                    }}
                  />
                ))}
              </div>
            </TabsContent>

            <TabsContent value="orders">
              <OrderHistory />
            </TabsContent>
          </Tabs>
        </div>
      </main>

      <Footer />

      {/* 支付对话框 */}
      <PaymentDialog
        open={showPaymentDialog}
        onOpenChange={setShowPaymentDialog}
        packageCode={selectedPackage}
        packages={packages}
        onSuccess={handlePaymentSuccess}
      />
    </div>
  );
}
