package com.ingenio.backend.codegen.ai.model;

/**
 * 最佳实践类型枚举（V2.0 Phase 4.3）
 *
 * <p>定义代码增强的四大类别，用于标识不同的最佳实践规则</p>
 *
 * <p>最佳实践优先级：</p>
 * <ol>
 *   <li>CODE_QUALITY（代码质量）：异常处理、日志记录、参数校验</li>
 *   <li>SECURITY（安全）：SQL注入防护、XSS防御、敏感信息保护</li>
 *   <li>PERFORMANCE（性能）：缓存优化、批量操作、索引建议</li>
 *   <li>MAINTAINABILITY（可维护性）：注释完整性、命名规范、代码结构</li>
 * </ol>
 *
 * @author Ingenio Code Generator
 * @since 2025-11-18 V2.0 Phase 4.3: 最佳实践应用器
 */
public enum BestPracticeType {

    /**
     * 代码质量增强
     *
     * <p>包含：</p>
     * <ul>
     *   <li>异常处理：try-catch包装、BusinessException抛出</li>
     *   <li>日志记录：操作日志、错误日志、性能日志</li>
     *   <li>参数校验：null检查、类型验证、范围验证</li>
     * </ul>
     */
    CODE_QUALITY("代码质量", "异常处理、日志记录、参数校验"),

    /**
     * 安全最佳实践
     *
     * <p>包含：</p>
     * <ul>
     *   <li>SQL注入防护：PreparedStatement、参数化查询</li>
     *   <li>XSS防御：HTML转义、输入过滤</li>
     *   <li>敏感信息保护：密码脱敏、日志脱敏</li>
     *   <li>权限校验：数据权限、操作权限</li>
     * </ul>
     */
    SECURITY("安全防护", "SQL注入防护、敏感信息保护、权限校验"),

    /**
     * 性能优化建议
     *
     * <p>包含：</p>
     * <ul>
     *   <li>缓存优化：Redis缓存、本地缓存</li>
     *   <li>批量操作：批量插入、批量更新</li>
     *   <li>索引建议：数据库索引、查询优化</li>
     *   <li>N+1问题：关联查询优化</li>
     * </ul>
     */
    PERFORMANCE("性能优化", "缓存优化、批量操作、索引建议"),

    /**
     * 可维护性增强
     *
     * <p>包含：</p>
     * <ul>
     *   <li>注释完整性：JavaDoc注释、行内注释</li>
     *   <li>命名规范：驼峰命名、语义化命名</li>
     *   <li>代码结构：单一职责、低耦合</li>
     * </ul>
     */
    MAINTAINABILITY("可维护性", "注释完整性、命名规范、代码结构");

    /**
     * 最佳实践类型名称
     */
    private final String name;

    /**
     * 最佳实践类型描述
     */
    private final String description;

    BestPracticeType(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public String toString() {
        return String.format("%s(%s)", name, description);
    }
}
