/**
 * useAuth Hook
 * 简化访问认证状态和动作的自定义Hook
 *
 * 使用方式：
 * ```typescript
 * const { user, isAuthenticated, login, logout } = useAuth();
 * ```
 *
 * @author Ingenio Team
 * @since 1.0.0
 */

import { useAuthStore } from '@/stores/auth-store';

/**
 * useAuth自定义Hook
 * 提供便捷的认证状态和动作访问
 *
 * @returns 认证状态和动作
 */
export function useAuth() {
  const user = useAuthStore((state) => state.user);
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);
  const loading = useAuthStore((state) => state.loading);
  const initialized = useAuthStore((state) => state.initialized);

  const login = useAuthStore((state) => state.login);
  const register = useAuthStore((state) => state.register);
  const loginWithGoogle = useAuthStore((state) => state.loginWithGoogle);
  const loginWithGitHub = useAuthStore((state) => state.loginWithGitHub);
  const logout = useAuthStore((state) => state.logout);
  const setUser = useAuthStore((state) => state.setUser);
  const initialize = useAuthStore((state) => state.initialize);

  return {
    // 状态
    user,
    isAuthenticated,
    loading,
    initialized,

    // 动作
    login,
    register,
    loginWithGoogle,
    loginWithGitHub,
    logout,
    setUser,
    initialize,
  };
}
