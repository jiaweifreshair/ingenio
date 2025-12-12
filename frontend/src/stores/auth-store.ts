/**
 * 认证状态管理Store
 * 使用Zustand管理全局认证状态
 *
 * 功能：
 * - 用户登录/登出状态管理
 * - 用户信息存储
 * - Token自动加载
 * - 持久化状态
 *
 * @author Ingenio Team
 * @since 1.0.0
 */

import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import * as authApi from '@/lib/api/auth';
import { getToken } from '@/lib/auth/token';

/**
 * 用户信息接口
 */
export interface User {
  /** 用户ID */
  userId: string;
  /** 用户名 */
  username: string;
  /** 邮箱 */
  email: string;
  /** 角色 */
  role: string;
}

/**
 * 认证状态接口
 */
interface AuthState {
  /** 当前用户信息，null表示未登录 */
  user: User | null;
  /** 是否已认证 */
  isAuthenticated: boolean;
  /** 是否正在加载 */
  loading: boolean;
  /** 是否已初始化（从本地存储加载） */
  initialized: boolean;
}

/**
 * 认证动作接口
 */
interface AuthActions {
  /**
   * 用户名/邮箱登录
   * @param usernameOrEmail - 用户名或邮箱
   * @param password - 密码
   */
  login: (usernameOrEmail: string, password: string) => Promise<void>;

  /**
   * 用户注册
   * @param username - 用户名
   * @param email - 邮箱
   * @param password - 密码
   */
  register: (username: string, email: string, password: string) => Promise<void>;

  /**
   * Google OAuth登录
   * @param code - OAuth授权码
   */
  loginWithGoogle: (code: string) => Promise<void>;

  /**
   * GitHub OAuth登录
   * @param code - OAuth授权码
   */
  loginWithGitHub: (code: string) => Promise<void>;

  /**
   * 登出
   */
  logout: () => Promise<void>;

  /**
   * 设置用户信息
   * @param user - 用户信息
   */
  setUser: (user: User | null) => void;

  /**
   * 初始化认证状态
   * 从localStorage加载Token并恢复用户状态
   */
  initialize: () => void;

  /**
   * 设置加载状态
   * @param loading - 是否加载中
   */
  setLoading: (loading: boolean) => void;
}

/**
 * 认证Store类型
 */
type AuthStore = AuthState & AuthActions;

/**
 * 认证Store
 * 使用Zustand管理全局认证状态，并持久化到localStorage
 */
export const useAuthStore = create<AuthStore>()(
  persist(
    (set, get) => ({
      // ==================== 状态 ====================
      user: null,
      isAuthenticated: false,
      loading: false,
      initialized: false,

      // ==================== 动作 ====================

      /**
       * 用户名/邮箱登录
       */
      login: async (usernameOrEmail: string, password: string) => {
        set({ loading: true });
        try {
          const response = await authApi.login(usernameOrEmail, password);

          // 设置用户信息
          const user: User = {
            userId: response.userId,
            username: response.username,
            email: response.email,
            role: response.role,
          };

          set({
            user,
            isAuthenticated: true,
            loading: false,
          });
        } catch (error) {
          set({ loading: false });
          throw error; // 抛出错误给调用方处理
        }
      },

      /**
       * 用户注册
       */
      register: async (username: string, email: string, password: string) => {
        set({ loading: true });
        try {
          const response = await authApi.register(username, email, password);

          // 设置用户信息（注册成功自动登录）
          const user: User = {
            userId: response.userId,
            username: response.username,
            email: response.email,
            role: response.role,
          };

          set({
            user,
            isAuthenticated: true,
            loading: false,
          });
        } catch (error) {
          set({ loading: false });
          throw error;
        }
      },

      /**
       * Google OAuth登录
       */
      loginWithGoogle: async (code: string) => {
        set({ loading: true });
        try {
          const response = await authApi.loginWithGoogle(code);

          // 设置用户信息
          const user: User = {
            userId: response.userId,
            username: response.username,
            email: response.email,
            role: response.role,
          };

          set({
            user,
            isAuthenticated: true,
            loading: false,
          });
        } catch (error) {
          set({ loading: false });
          throw error;
        }
      },

      /**
       * GitHub OAuth登录
       */
      loginWithGitHub: async (code: string) => {
        set({ loading: true });
        try {
          const response = await authApi.loginWithGitHub(code);

          // 设置用户信息
          const user: User = {
            userId: response.userId,
            username: response.username,
            email: response.email,
            role: response.role,
          };

          set({
            user,
            isAuthenticated: true,
            loading: false,
          });
        } catch (error) {
          set({ loading: false });
          throw error;
        }
      },

      /**
       * 登出
       */
      logout: async () => {
        try {
          await authApi.logout();
        } catch (error) {
          console.error('Logout API error:', error);
        } finally {
          // 清除用户信息
          set({
            user: null,
            isAuthenticated: false,
          });
        }
      },

      /**
       * 设置用户信息
       */
      setUser: (user: User | null) => {
        set({
          user,
          isAuthenticated: !!user,
        });
      },

      /**
       * 初始化认证状态
       * 从localStorage加载Token并检查有效性
       */
      initialize: () => {
        const token = getToken();

        if (token) {
          // Token存在，设置为已认证
          // TODO: Phase 3.4 - 可以添加Token验证逻辑
          // 目前简单地从持久化存储中加载用户信息
          const { user } = get();
          set({
            isAuthenticated: !!user,
            initialized: true,
          });
        } else {
          // Token不存在，清除用户信息
          set({
            user: null,
            isAuthenticated: false,
            initialized: true,
          });
        }
      },

      /**
       * 设置加载状态
       */
      setLoading: (loading: boolean) => {
        set({ loading });
      },
    }),
    {
      name: 'auth-storage', // localStorage key
      partialize: (state) => ({
        // 只持久化user状态，其他状态不持久化
        user: state.user,
        isAuthenticated: state.isAuthenticated,
      }),
    }
  )
);
