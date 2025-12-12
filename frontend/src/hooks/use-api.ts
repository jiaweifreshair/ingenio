import { useState, useCallback } from 'react';
import { useToast } from '@/hooks/use-toast';
import { getToken } from '@/lib/auth/token';
import { getApiBaseUrl } from '@/lib/api/base-url';
import { normalizeApiResponse } from '@/lib/api/response';

interface UseApiOptions {
  showErrorToast?: boolean;
  showSuccessToast?: boolean;
  successMessage?: string;
}

interface FetchOptions extends RequestInit {
  headers?: Record<string, string>;
}

export function useApi<T = unknown>() {
  const [data, setData] = useState<T | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<Error | null>(null);
  const { toast } = useToast();

  const request = useCallback(async (
    endpoint: string, 
    options: FetchOptions = {},
    apiOptions: UseApiOptions = { showErrorToast: true }
  ) => {
    setLoading(true);
    setError(null);
    
    try {
      const baseUrl = getApiBaseUrl();
      const token = getToken();
      
      const headers: Record<string, string> = {
        'Content-Type': 'application/json',
        ...options.headers,
      };

      if (token) {
        headers['Authorization'] = token;
      }

      // 确保endpoint以/开头（如果不是绝对路径）
      const url = endpoint.startsWith('http') 
        ? endpoint 
        : `${baseUrl}${endpoint.startsWith('/') ? endpoint : `/${endpoint}`}`;

      const response = await fetch(url, {
        ...options,
        headers,
      });

      if (!response.ok) {
        const errorText = await response.text();
        let errorMessage = response.statusText;
        try {
          const errorJson = JSON.parse(errorText);
          errorMessage = errorJson.message || errorJson.error || response.statusText;
        } catch {
          errorMessage = errorText || response.statusText;
        }
        
        throw new Error(errorMessage || `Request failed with status ${response.status}`);
      }

      const result = await response.json();
      const normalized = normalizeApiResponse<unknown>(result);

      const hasSuccessField = typeof (result as { success?: unknown }).success === 'boolean';
      const hasCodeField = (result as { code?: unknown }).code !== undefined;

      // 如果响应包含 success/code 字段，则按规范化结果判定是否成功
      if ((hasSuccessField || hasCodeField) && !normalized.success) {
        throw new Error(
          normalized.message ||
            normalized.error ||
            '请求失败'
        );
      }

      // 处理标准 ApiResponse / Result 结构
      const finalData =
        normalized.data !== undefined
          ? normalized.data
          : (result as { data?: unknown }).data !== undefined
            ? (result as { data?: unknown }).data
            : result;
      
      setData(finalData);

      if (apiOptions.showSuccessToast && apiOptions.successMessage) {
        toast({
          title: "成功",
          description: apiOptions.successMessage,
          variant: "default",
        });
      }

      return finalData;
    } catch (err) {
      const errorObj = err instanceof Error ? err : new Error(String(err));
      setError(errorObj);
      
      if (apiOptions.showErrorToast) {
        toast({
          title: "请求失败",
          description: errorObj.message,
          variant: "destructive",
        });
      }
      
      throw errorObj;
    } finally {
      setLoading(false);
    }
  }, [toast]);

  return { data, loading, error, request };
}
