'use client';

import React, { useState } from 'react';
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { createOrder, getOrderStatus, CreditPackage } from '@/lib/api/billing';
import { Loader2, QrCode, ExternalLink } from 'lucide-react';

interface PaymentDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  packageCode: string | null;
  packages: CreditPackage[];
  onSuccess?: () => void;
}

/**
 * 支付对话框组件
 * 显示支付二维码或跳转链接
 */
export function PaymentDialog({
  open,
  onOpenChange,
  packageCode,
  packages,
  onSuccess,
}: PaymentDialogProps): React.ReactElement {
  const [loading, setLoading] = useState(false);
  const [payData, setPayData] = useState<{ orderNo: string; payDataType: string; payData: string } | null>(null);
  const [checking, setChecking] = useState(false);

  const selectedPackage = packages.find((p) => p.code === packageCode);

  const handleCreateOrder = async () => {
    if (!packageCode) return;

    setLoading(true);
    try {
      const response = await createOrder({
        packageCode,
        payChannel: 'ALIPAY_PC',
      });

      if (response.success && response.data) {
        setPayData({
          orderNo: response.data.orderNo,
          payDataType: response.data.payDataType,
          payData: response.data.payData,
        });
      }
    } catch (error) {
      console.error('创建订单失败:', error);
    } finally {
      setLoading(false);
    }
  };

  const handleCheckPayment = async () => {
    if (!payData?.orderNo) return;

    setChecking(true);
    try {
      const response = await getOrderStatus(payData.orderNo);
      if (response.success && response.data?.status === 'PAID') {
        onSuccess?.();
        onOpenChange(false);
        setPayData(null);
      } else {
        alert('订单尚未支付，请完成支付后再试');
      }
    } catch (error) {
      console.error('查询订单失败:', error);
    } finally {
      setChecking(false);
    }
  };

  const handleClose = () => {
    onOpenChange(false);
    setPayData(null);
  };

  return (
    <Dialog open={open} onOpenChange={handleClose}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>
            {payData ? '完成支付' : '确认购买'}
          </DialogTitle>
        </DialogHeader>

        {!payData ? (
          // 确认购买界面
          <div className="space-y-6 py-4">
            {selectedPackage && (
              <div className="bg-muted/50 rounded-lg p-4">
                <div className="flex justify-between items-center mb-2">
                  <span className="font-medium">{selectedPackage.name}</span>
                  <span className="text-2xl font-bold">¥{selectedPackage.price}</span>
                </div>
                <p className="text-sm text-muted-foreground">
                  {selectedPackage.credits} 次生成机会
                </p>
              </div>
            )}

            <div className="flex gap-3">
              <Button variant="outline" className="flex-1" onClick={handleClose}>
                取消
              </Button>
              <Button
                className="flex-1 bg-gradient-to-r from-purple-600 to-blue-600"
                onClick={handleCreateOrder}
                disabled={loading}
              >
                {loading ? (
                  <>
                    <Loader2 className="h-4 w-4 mr-2 animate-spin" />
                    创建订单...
                  </>
                ) : (
                  '确认支付'
                )}
              </Button>
            </div>
          </div>
        ) : (
          // 支付界面
          <div className="space-y-6 py-4">
            {payData.payDataType === 'QR' ? (
              // 二维码支付
              <div className="flex flex-col items-center">
                <div className="p-4 bg-white rounded-lg">
                  <QrCode className="h-48 w-48 text-gray-400" />
                  <p className="text-center text-sm text-muted-foreground mt-2">
                    请使用支付宝扫码支付
                  </p>
                </div>
              </div>
            ) : (
              // URL 跳转支付
              <div className="flex flex-col items-center gap-4">
                <p className="text-center text-muted-foreground">
                  点击下方按钮跳转到支付宝完成支付
                </p>
                <Button
                  className="w-full"
                  onClick={() => window.open(payData.payData, '_blank')}
                >
                  <ExternalLink className="h-4 w-4 mr-2" />
                  前往支付宝支付
                </Button>
              </div>
            )}

            <div className="text-center text-sm text-muted-foreground">
              订单号: {payData.orderNo}
            </div>

            <Button
              variant="outline"
              className="w-full"
              onClick={handleCheckPayment}
              disabled={checking}
            >
              {checking ? (
                <>
                  <Loader2 className="h-4 w-4 mr-2 animate-spin" />
                  查询中...
                </>
              ) : (
                '我已完成支付'
              )}
            </Button>
          </div>
        )}
      </DialogContent>
    </Dialog>
  );
}
