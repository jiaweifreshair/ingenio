'use client';

import { useState, useEffect, useCallback } from 'react';
import { getUserCredits, UserCredits } from '@/lib/api/billing';
import { getToken } from '@/lib/auth/token';

/**
 * 用户余额 Hook
 * 获取和管理用户的生成次数余额
 * 仅在用户已登录时才调用API
 */
export function useCredits() {
  const [credits, setCredits] = useState<UserCredits | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const fetchCredits = useCallback(async () => {
    // 直接检查token，避免依赖zustand状态同步问题
    const token = getToken();
    if (!token) {
      setLoading(false);
      setCredits(null);
      return;
    }
    try {
      setLoading(true);
      setError(null);
      const response = await getUserCredits();
      if (response.success && response.data) {
        setCredits(response.data);
      }
    } catch (err) {
      setError('获取余额失败');
      console.error('获取余额失败:', err);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchCredits();
  }, [fetchCredits]);

  /**
   * 检查是否有足够余额
   */
  const hasCredits = useCallback((required: number = 1) => {
    return (credits?.remaining ?? 0) >= required;
  }, [credits]);

  return {
    credits,
    loading,
    error,
    hasCredits,
    refetch: fetchCredits,
  };
}
