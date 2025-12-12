/**
 * 项目设置相关类型定义
 *
 * @author Ingenio Team
 * @since 1.0.0
 */

/**
 * 项目设置类型
 */
export interface ProjectSettings {
  /** 基本信息 */
  basic: {
    name: string;
    description: string;
    coverImageUrl?: string;
    visibility: 'public' | 'private' | 'unlisted';
    tags?: string[];
    ageGroup?: string;
  };
  /** 高级设置 */
  advanced: {
    alias?: string;
    archived: boolean;
  };
  /** 集成设置 */
  integrations: {
    githubEnabled: boolean;
    githubRepo?: string;
    customDomain?: string;
    webhookUrl?: string;
  };
  /** 成员列表 */
  members?: ProjectMember[];
}

/**
 * 项目成员类型
 */
export interface ProjectMember {
  /** 用户ID */
  id: string;
  /** 用户名 */
  username: string;
  /** 邮箱 */
  email: string;
  /** 头像URL */
  avatarUrl?: string;
  /** 角色 */
  role: 'owner' | 'editor' | 'viewer';
  /** 加入时间 */
  joinedAt: string;
}

/**
 * 邀请成员请求
 */
export interface InviteMemberRequest {
  /** 邀请的邮箱 */
  email: string;
  /** 角色 */
  role: 'editor' | 'viewer';
}

/**
 * 项目转移请求
 */
export interface TransferProjectRequest {
  /** 目标用户ID */
  targetUserId: string;
}

/**
 * 更新项目设置请求
 */
export interface UpdateProjectSettingsRequest {
  name?: string;
  description?: string;
  coverImageUrl?: string;
  visibility?: 'public' | 'private' | 'unlisted';
  tags?: string[];
  ageGroup?: string;
  alias?: string;
  metadata?: Record<string, unknown>;
}
