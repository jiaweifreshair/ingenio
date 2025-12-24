package com.ingenio.backend.common.context;

/**
 * 租户ID上下文持有者。
 * 使用 ThreadLocal 在单个请求线程中安全地存储和访问租户ID。
 */
public class TenantContextHolder {

    private static final ThreadLocal<String> tenantIdHolder = new InheritableThreadLocal<>();

    /**
     * 设置当前线程的租户ID。
     *
     * @param tenantId 租户ID
     */
    public static void setTenantId(String tenantId) {
        tenantIdHolder.set(tenantId);
    }

    /**
     * 获取当前线程的租户ID。
     *
     * @return 租户ID，如果未设置则返回 null
     */
    public static String getTenantId() {
        return tenantIdHolder.get();
    }

    /**
     * 清除当前线程的租户ID。
     * 必须在请求处理完成后（例如在Filter的finally块中）调用，以防内存泄漏。
     */
    public static void clear() {
        tenantIdHolder.remove();
    }
}
