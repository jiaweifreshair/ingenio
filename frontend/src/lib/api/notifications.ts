/**
 * 通知API客户端
 * 与后端NotificationController交互
 *
 * @author Ingenio Team
 * @since 1.0.0
 */

import type {
  Notification,
  NotificationSettings,
  NotificationFilters,
} from '@/types/notification';
import { getApiBaseUrl } from '@/lib/api/base-url';
import { normalizeApiResponse } from '@/lib/api/response';
import type { PageResult } from '@/types/project';

const API_BASE_URL = getApiBaseUrl();

/**
 * 获取通知列表（分页）
 *
 * 注意：此API依赖后端NotificationController实现
 * 当前后端未实现该接口，返回空列表
 * TODO: 待后端实现 /v1/notifications 端点后移除降级逻辑
 */
export async function listNotifications(
  filters: NotificationFilters = {}
): Promise<PageResult<Notification>> {
  const { unreadOnly, type, current = 1, size = 20 } = filters;

  try {
    // 构建查询参数
    const params = new URLSearchParams();
    params.append('current', current.toString());
    params.append('size', size.toString());

    if (unreadOnly) {
      params.append('unreadOnly', 'true');
    }

    if (type) {
      params.append('type', type);
    }

    const response = await fetch(`${API_BASE_URL}/v1/notifications?${params.toString()}`, {
      method: 'GET',
      headers: {
        'Content-Type': 'application/json',
        // TODO: 添加认证token
        // 'Authorization': `Bearer ${getToken()}`,
      },
      credentials: 'include',
    });

    // 当API不存在时（404），优雅降级返回空列表
    if (response.status === 404) {
      console.warn('[通知API] 后端NotificationController未实现，返回空列表');
      return {
        records: [],
        total: 0,
        current,
        size,
        pages: 0,
        hasNext: false,
        hasPrevious: false,
      };
    }

    if (!response.ok) {
      throw new Error(`获取通知列表失败: ${response.statusText}`);
    }

    const raw = await response.json();
    const result = normalizeApiResponse<PageResult<Notification>>(raw);

    if (!result.success) {
      throw new Error(result.message || result.error || '获取通知列表失败');
    }

    return result.data as PageResult<Notification>;
  } catch (error) {
    // 捕获网络错误等异常，返回空列表
    if (error instanceof TypeError && error.message.includes('fetch')) {
      console.warn('[通知API] 网络请求失败，返回空列表');
      return {
        records: [],
        total: 0,
        current,
        size,
        pages: 0,
        hasNext: false,
        hasPrevious: false,
      };
    }
    throw error;
  }
}

/**
 * 获取未读通知数量
 *
 * 注意：此API依赖后端NotificationController实现
 * 当前后端未实现该接口，返回默认值0
 * TODO: 待后端实现 /v1/notifications/unread-count 端点后移除降级逻辑
 */
export async function getUnreadCount(): Promise<number> {
  try {
    const response = await fetch(`${API_BASE_URL}/v1/notifications/unread-count`, {
      method: 'GET',
      headers: {
        'Content-Type': 'application/json',
      },
      credentials: 'include',
    });

    // 当API不存在时（404），优雅降级返回0
    if (response.status === 404) {
      console.warn('[通知API] 后端NotificationController未实现，返回默认值0');
      return 0;
    }

    if (!response.ok) {
      throw new Error(`获取未读数量失败: ${response.statusText}`);
    }

    const raw = await response.json();
    const result = normalizeApiResponse<{ count: number }>(raw);

    if (!result.success) {
      console.warn(
        '[通知API] 获取未读数量失败，返回默认值0: %s',
        result.message || result.error || '未知错误'
      );
      return 0;
    }

    return (result.data as { count: number }).count ?? 0;
  } catch (error) {
    // 捕获网络错误等异常，返回默认值0
    if (error instanceof TypeError && error.message.includes('fetch')) {
      console.warn('[通知API] 网络请求失败，返回默认值0');
      return 0;
    }
    console.warn('[通知API] 获取未读数量异常，返回默认值0', error);
    return 0;
  }
}

/**
 * 标记通知为已读
 */
export async function markAsRead(id: string): Promise<void> {
  const response = await fetch(`${API_BASE_URL}/v1/notifications/${id}/read`, {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
    },
    credentials: 'include',
  });

  if (!response.ok) {
    throw new Error(`标记已读失败: ${response.statusText}`);
  }

  const raw = await response.json();
  const result = normalizeApiResponse<void>(raw);

  if (!result.success) {
    throw new Error(result.message || result.error || '标记已读失败');
  }
}

/**
 * 全部标记为已读
 */
export async function markAllAsRead(): Promise<void> {
  const response = await fetch(`${API_BASE_URL}/v1/notifications/read-all`, {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
    },
    credentials: 'include',
  });

  if (!response.ok) {
    throw new Error(`批量标记已读失败: ${response.statusText}`);
  }

  const raw = await response.json();
  const result = normalizeApiResponse<void>(raw);

  if (!result.success) {
    throw new Error(result.message || result.error || '批量标记已读失败');
  }
}

/**
 * 删除通知
 */
export async function deleteNotification(id: string): Promise<void> {
  const response = await fetch(`${API_BASE_URL}/v1/notifications/${id}`, {
    method: 'DELETE',
    headers: {
      'Content-Type': 'application/json',
    },
    credentials: 'include',
  });

  if (!response.ok) {
    throw new Error(`删除通知失败: ${response.statusText}`);
  }

  const raw = await response.json();
  const result = normalizeApiResponse<void>(raw);

  if (!result.success) {
    throw new Error(result.message || result.error || '删除通知失败');
  }
}

/**
 * 获取通知设置
 *
 * 注意：此API依赖后端NotificationController实现
 * 当前后端未实现该接口，返回默认设置（全部开启）
 * TODO: 待后端实现 /v1/notifications/settings 端点后移除降级逻辑
 */
export async function getNotificationSettings(): Promise<NotificationSettings> {
  try {
    const response = await fetch(`${API_BASE_URL}/v1/notifications/settings`, {
      method: 'GET',
      headers: {
        'Content-Type': 'application/json',
      },
      credentials: 'include',
    });

    // 当API不存在时（404），优雅降级返回默认设置
    if (response.status === 404) {
      console.warn('[通知API] 后端NotificationController未实现，返回默认设置');
      return {
        emailEnabled: true,
        pushEnabled: true,
        frequency: 'realtime',
        systemNotifications: true,
        commentNotifications: true,
        likeNotifications: true,
        forkNotifications: true,
        buildNotifications: true,
        mentionNotifications: true,
      };
    }

    if (!response.ok) {
      throw new Error(`获取通知设置失败: ${response.statusText}`);
    }

    const raw = await response.json();
    const result = normalizeApiResponse<NotificationSettings>(raw);

    if (!result.success) {
      console.warn(
        '[通知API] 获取通知设置失败，返回默认设置: %s',
        result.message || result.error || '未知错误'
      );
      return getDefaultNotificationSettings();
    }

    return (result.data as NotificationSettings) ?? getDefaultNotificationSettings();
  } catch (error) {
    // 捕获网络错误等异常，返回默认设置
    if (error instanceof TypeError && error.message.includes('fetch')) {
      console.warn('[通知API] 网络请求失败，返回默认设置');
      return getDefaultNotificationSettings();
    }
    console.warn('[通知API] 获取通知设置异常，返回默认设置', error);
    return getDefaultNotificationSettings();
  }
}

function getDefaultNotificationSettings(): NotificationSettings {
  return {
    emailEnabled: true,
    pushEnabled: true,
    frequency: 'realtime',
    systemNotifications: true,
    commentNotifications: true,
    likeNotifications: true,
    forkNotifications: true,
    buildNotifications: true,
    mentionNotifications: true,
  };
}

/**
 * 更新通知设置
 */
export async function updateNotificationSettings(
  settings: Partial<NotificationSettings>
): Promise<void> {
  const response = await fetch(`${API_BASE_URL}/v1/notifications/settings`, {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
    },
    credentials: 'include',
    body: JSON.stringify(settings),
  });

  if (!response.ok) {
    throw new Error(`更新通知设置失败: ${response.statusText}`);
  }

  const raw = await response.json();
  const result = normalizeApiResponse<void>(raw);

  if (!result.success) {
    throw new Error(result.message || result.error || '更新通知设置失败');
  }
}
