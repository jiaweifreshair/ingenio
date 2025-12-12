package com.ingenio.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ingenio.backend.entity.UserDeviceEntity;
import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 用户设备Mapper接口
 * 提供用户设备表的数据库访问操作
 */
@Mapper
public interface UserDeviceMapper extends BaseMapper<UserDeviceEntity> {

    /**
     * 根据用户ID查询所有设备
     *
     * @param userId 用户ID
     * @return 设备列表
     */
    @Select("SELECT * FROM user_devices WHERE user_id = #{userId} ORDER BY last_active_at DESC")
    List<UserDeviceEntity> findByUserId(@Param("userId") UUID userId);

    /**
     * 根据TokenId查找设备
     *
     * @param tokenId SaToken的TokenId
     * @return 设备实体（Optional）
     */
    @Select("SELECT * FROM user_devices WHERE token_id = #{tokenId} LIMIT 1")
    Optional<UserDeviceEntity> findByTokenId(@Param("tokenId") String tokenId);

    /**
     * 清除用户的当前设备标记
     *
     * @param userId 用户ID
     * @return 影响行数
     */
    @Update("UPDATE user_devices SET is_current = FALSE WHERE user_id = #{userId}")
    int clearCurrentDeviceFlag(@Param("userId") UUID userId);

    /**
     * 删除指定设备
     *
     * @param deviceId 设备ID
     * @param userId   用户ID（防止越权）
     * @return 影响行数
     */
    @Delete("DELETE FROM user_devices WHERE id = #{deviceId} AND user_id = #{userId}")
    int deleteByIdAndUserId(@Param("deviceId") UUID deviceId, @Param("userId") UUID userId);
}
