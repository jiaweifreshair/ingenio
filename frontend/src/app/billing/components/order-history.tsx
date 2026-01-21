'use client';

import React, { useState, useEffect } from 'react';
import { getOrders, PayOrder } from '@/lib/api/billing';
import { Badge } from '@/components/ui/badge';

/**
 * 订单历史组件
 * 显示用户的支付订单记录
 */
export function OrderHistory(): React.ReactElement {
  const [orders, setOrders] = useState<PayOrder[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    async function fetchOrders() {
      try {
        const response = await getOrders();
        if (response.success && response.data) {
          setOrders(response.data);
        }
      } catch (error) {
        console.error('获取订单列表失败:', error);
      } finally {
        setLoading(false);
      }
    }
    fetchOrders();
  }, []);

  const getStatusBadge = (status: string) => {
    switch (status) {
      case 'PAID':
        return <Badge className="bg-green-500">已支付</Badge>;
      case 'PENDING':
        return <Badge variant="outline">待支付</Badge>;
      case 'EXPIRED':
        return <Badge variant="secondary">已过期</Badge>;
      case 'CANCELLED':
        return <Badge variant="secondary">已取消</Badge>;
      default:
        return <Badge variant="outline">{status}</Badge>;
    }
  };

  const formatDate = (dateStr: string) => {
    if (!dateStr) return '-';
    const date = new Date(dateStr);
    return date.toLocaleString('zh-CN', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
    });
  };

  if (loading) {
    return (
      <div className="space-y-4">
        {[1, 2, 3].map((i) => (
          <div key={i} className="h-20 bg-muted animate-pulse rounded-lg" />
        ))}
      </div>
    );
  }

  if (orders.length === 0) {
    return (
      <div className="text-center py-12 text-muted-foreground">
        <p>暂无订单记录</p>
        <p className="text-sm mt-1">购买套餐后，订单将显示在这里</p>
      </div>
    );
  }

  return (
    <div className="space-y-4">
      {orders.map((order) => (
        <div
          key={order.orderNo}
          className="flex items-center justify-between p-4 bg-card rounded-lg border"
        >
          <div className="flex-1">
            <div className="flex items-center gap-3 mb-1">
              <span className="font-medium">{order.packageName}</span>
              {getStatusBadge(order.status)}
            </div>
            <div className="text-sm text-muted-foreground">
              <span>订单号: {order.orderNo}</span>
              <span className="mx-2">|</span>
              <span>{formatDate(order.createdAt)}</span>
            </div>
          </div>
          <div className="text-right">
            <p className="font-semibold">¥{order.amount}</p>
            <p className="text-sm text-muted-foreground">{order.creditsAmount} 次</p>
          </div>
        </div>
      ))}
    </div>
  );
}
