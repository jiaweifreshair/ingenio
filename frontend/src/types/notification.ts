/**
 * 通知相关类型定义
 *
 * @author Ingenio Team
 * @since 1.0.0
 */

/**
 * 通知类型枚举
 */
export enum NotificationType {
  /** 系统通知 */
  SYSTEM = 'system',
  /** 评论通知 */
  COMMENT = 'comment',
  /** 点赞通知 */
  LIKE = 'like',
  /** 项目派生通知 */
  FORK = 'fork',
  /** 构建完成通知 */
  BUILD = 'build',
  /** @提醒通知 */
  MENTION = 'mention',
}

/**
 * 通知数据类型
 */
export interface Notification {
  /** 通知ID */
  id: string;
  /** 通知类型 */
  type: NotificationType;
  /** 通知标题 */
  title: string;
  /** 通知内容 */
  content: string;
  /** 跳转链接（可选） */
  linkUrl?: string;
  /** 是否已读 */
  isRead: boolean;
  /** 创建时间 */
  createdAt: string;
  /** 发送者信息（可选） */
  sender?: {
    id: string;
    name: string;
    avatar?: string;
  };
}

/**
 * 通知设置类型
 */
export interface NotificationSettings {
  /** 邮件通知开关 */
  emailEnabled: boolean;
  /** 推送通知开关 */
  pushEnabled: boolean;
  /** 通知频率（实时/每日汇总） */
  frequency: 'realtime' | 'daily';
  /** 系统通知订阅 */
  systemNotifications: boolean;
  /** 评论通知订阅 */
  commentNotifications: boolean;
  /** 点赞通知订阅 */
  likeNotifications: boolean;
  /** 派生通知订阅 */
  forkNotifications: boolean;
  /** 构建通知订阅 */
  buildNotifications: boolean;
  /** @提醒通知订阅 */
  mentionNotifications: boolean;
}

/**
 * 通知统计类型
 */
export interface NotificationStats {
  /** 未读通知数量 */
  unreadCount: number;
  /** 总通知数量 */
  totalCount: number;
}

/**
 * 通知筛选参数
 */
export interface NotificationFilters {
  /** 是否只显示未读 */
  unreadOnly?: boolean;
  /** 通知类型筛选 */
  type?: NotificationType;
  /** 当前页码 */
  current?: number;
  /** 每页数量 */
  size?: number;
}
