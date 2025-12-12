package com.ingenio.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ingenio.backend.entity.PasswordResetTokenEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.Optional;
import java.util.UUID;

/**
 * 密码重置令牌Mapper接口
 * 提供密码重置令牌表的数据库访问操作
 */
@Mapper
public interface PasswordResetTokenMapper extends BaseMapper<PasswordResetTokenEntity> {

    /**
     * 根据令牌查找
     *
     * @param token 令牌字符串
     * @return 令牌实体（Optional）
     */
    @Select("SELECT * FROM password_reset_tokens WHERE token = #{token} LIMIT 1")
    Optional<PasswordResetTokenEntity> findByToken(@Param("token") String token);

    /**
     * 根据用户ID查找未使用的令牌
     *
     * @param userId 用户ID
     * @return 令牌实体（Optional）
     */
    @Select("SELECT * FROM password_reset_tokens WHERE user_id = #{userId} AND used = FALSE ORDER BY created_at DESC LIMIT 1")
    Optional<PasswordResetTokenEntity> findUnusedByUserId(@Param("userId") UUID userId);

    /**
     * 标记用户所有未使用的令牌为已使用（防止重复使用）
     *
     * @param userId 用户ID
     * @return 影响行数
     */
    @Update("UPDATE password_reset_tokens SET used = TRUE WHERE user_id = #{userId} AND used = FALSE")
    int markAllAsUsedByUserId(@Param("userId") UUID userId);

    /**
     * 清理过期的令牌
     *
     * @return 删除的行数
     */
    @Update("DELETE FROM password_reset_tokens WHERE expires_at < NOW() AND used = FALSE")
    int deleteExpiredTokens();

    /**
     * 插入密码重置令牌（UUID类型安全插入）
     *
     * 使用自定义SQL和CAST操作符确保UUID类型正确映射到PostgreSQL
     *
     * @param token 密码重置令牌实体
     * @return 影响的行数
     */
    int insertWithCast(@Param("token") PasswordResetTokenEntity token);

    /**
     * 标记用户所有未使用的令牌为已使用（UUID类型安全更新）
     *
     * @param userId 用户ID
     * @return 影响行数
     */
    int markAllAsUsedByUserIdWithCast(@Param("userId") UUID userId);

    /**
     * 更新密码重置令牌（UUID类型安全更新）
     *
     * @param token 密码重置令牌实体
     * @return 影响的行数
     */
    int updateByIdWithCast(@Param("token") PasswordResetTokenEntity token);
}
