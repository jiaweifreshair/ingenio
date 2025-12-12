package com.ingenio.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ingenio.backend.entity.UserEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Optional;
import java.util.UUID;

/**
 * 用户Mapper接口
 * 提供用户表的数据库访问操作
 */
@Mapper
public interface UserMapper extends BaseMapper<UserEntity> {

    /**
     * 根据用户名查找用户
     *
     * @param username 用户名
     * @return 用户实体（Optional）
     */
    @Select("SELECT * FROM users WHERE username = #{username} LIMIT 1")
    Optional<UserEntity> findByUsername(@Param("username") String username);

    /**
     * 根据邮箱查找用户
     *
     * @param email 邮箱
     * @return 用户实体（Optional）
     */
    @Select("SELECT * FROM users WHERE email = #{email} LIMIT 1")
    Optional<UserEntity> findByEmail(@Param("email") String email);

    /**
     * 根据租户ID和用户名查找用户
     *
     * @param tenantId 租户ID
     * @param username 用户名
     * @return 用户实体（Optional）
     */
    @Select("SELECT * FROM users WHERE tenant_id = #{tenantId} AND username = #{username} LIMIT 1")
    Optional<UserEntity> findByTenantIdAndUsername(
        @Param("tenantId") String tenantId,
        @Param("username") String username
    );

    /**
     * 根据用户ID查找用户（UUID类型安全查询）
     *
     * 背景：PostgreSQL的UUID类型需要显式CAST转换，否则会报错：
     * "ERROR: operator does not exist: uuid = character varying"
     *
     * @param userId 用户ID字符串（将被转换为UUID）
     * @return 用户实体（Optional）
     */
    @Select("SELECT * FROM users WHERE id = CAST(#{userId} AS UUID) LIMIT 1")
    Optional<UserEntity> findByIdWithCast(@Param("userId") String userId);

    /**
     * 根据ID更新用户信息（UUID类型安全更新）
     *
     * 使用MyBatis动态SQL + UUID CAST解决类型匹配问题
     *
     * @param user 用户实体
     * @return 影响的行数
     */
    int updateByIdWithCast(@Param("user") UserEntity user);
}
