package com.ingenio.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ingenio.backend.entity.ApiKeyEntity;
import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * API密钥Mapper接口
 * 提供API密钥表的数据库访问操作
 */
@Mapper
public interface ApiKeyMapper extends BaseMapper<ApiKeyEntity> {

    /**
     * 根据用户ID查询所有密钥
     *
     * @param userId 用户ID
     * @return 密钥列表
     */
    @Select("SELECT * FROM api_keys WHERE user_id = #{userId} ORDER BY created_at DESC")
    List<ApiKeyEntity> findByUserId(@Param("userId") UUID userId);

    /**
     * 根据密钥值查找
     *
     * @param keyValue 密钥值（哈希）
     * @return 密钥实体（Optional）
     */
    @Select("SELECT * FROM api_keys WHERE key_value = #{keyValue} LIMIT 1")
    Optional<ApiKeyEntity> findByKeyValue(@Param("keyValue") String keyValue);

    /**
     * 根据密钥前缀查找
     *
     * @param keyPrefix 密钥前缀
     * @return 密钥实体（Optional）
     */
    @Select("SELECT * FROM api_keys WHERE key_prefix = #{keyPrefix} LIMIT 1")
    Optional<ApiKeyEntity> findByKeyPrefix(@Param("keyPrefix") String keyPrefix);

    /**
     * 删除指定密钥
     *
     * @param keyId  密钥ID
     * @param userId 用户ID（防止越权）
     * @return 影响行数
     */
    @Delete("DELETE FROM api_keys WHERE id = #{keyId} AND user_id = #{userId}")
    int deleteByIdAndUserId(@Param("keyId") UUID keyId, @Param("userId") UUID userId);

    /**
     * 更新密钥最后使用时间和IP
     *
     * @param keyId  密钥ID
     * @param ip     IP地址
     * @return 影响行数
     */
    @Update("UPDATE api_keys SET last_used_at = NOW(), last_used_ip = #{ip}, usage_count = usage_count + 1 WHERE id = #{keyId}")
    int updateLastUsed(@Param("keyId") UUID keyId, @Param("ip") String ip);
}
