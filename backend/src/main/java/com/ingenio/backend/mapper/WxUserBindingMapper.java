package com.ingenio.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ingenio.backend.entity.WxUserBindingEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 微信用户绑定Mapper接口
 * 提供微信用户绑定表的数据库访问操作
 *
 * @author Ingenio Team
 * @since 1.0.0
 */
@Mapper
public interface WxUserBindingMapper extends BaseMapper<WxUserBindingEntity> {

    /**
     * 根据微信OpenID查找绑定记录
     *
     * @param wxOpenid 微信OpenID
     * @return 绑定实体（Optional）
     */
    @Select("SELECT * FROM wx_user_bindings WHERE wx_openid = #{wxOpenid} LIMIT 1")
    Optional<WxUserBindingEntity> findByWxOpenid(@Param("wxOpenid") String wxOpenid);

    /**
     * 根据微信UnionID查找绑定记录
     *
     * @param wxUnionid 微信UnionID
     * @return 绑定实体（Optional）
     */
    @Select("SELECT * FROM wx_user_bindings WHERE wx_unionid = #{wxUnionid} LIMIT 1")
    Optional<WxUserBindingEntity> findByWxUnionid(@Param("wxUnionid") String wxUnionid);

    /**
     * 根据用户ID查找绑定记录
     *
     * @param userId 用户ID
     * @return 绑定实体（Optional）
     */
    @Select("SELECT * FROM wx_user_bindings WHERE user_id = #{userId} LIMIT 1")
    Optional<WxUserBindingEntity> findByUserId(@Param("userId") UUID userId);

    /**
     * 根据用户ID查找所有绑定记录（支持一个用户绑定多个微信号）
     *
     * @param userId 用户ID
     * @return 绑定实体列表
     */
    @Select("SELECT * FROM wx_user_bindings WHERE user_id = #{userId} ORDER BY last_login_time DESC")
    List<WxUserBindingEntity> findAllByUserId(@Param("userId") UUID userId);

    /**
     * 更新最后登录时间和登录次数
     *
     * @param wxOpenid      微信OpenID
     * @param lastLoginTime 最后登录时间
     * @return 影响的行数
     */
    @Update("UPDATE wx_user_bindings " +
            "SET last_login_time = #{lastLoginTime}, " +
            "    login_count = login_count + 1, " +
            "    updated_at = NOW() " +
            "WHERE wx_openid = #{wxOpenid}")
    int updateLastLoginTime(@Param("wxOpenid") String wxOpenid,
                            @Param("lastLoginTime") Instant lastLoginTime);

    /**
     * 更新微信用户信息（昵称、头像等）
     *
     * @param wxOpenid    微信OpenID
     * @param wxNickname  微信昵称
     * @param wxAvatarUrl 微信头像URL
     * @return 影响的行数
     */
    @Update("UPDATE wx_user_bindings " +
            "SET wx_nickname = #{wxNickname}, " +
            "    wx_avatar_url = #{wxAvatarUrl}, " +
            "    updated_at = NOW() " +
            "WHERE wx_openid = #{wxOpenid}")
    int updateWxUserInfo(@Param("wxOpenid") String wxOpenid,
                         @Param("wxNickname") String wxNickname,
                         @Param("wxAvatarUrl") String wxAvatarUrl);

    /**
     * 检查OpenID是否已存在
     *
     * @param wxOpenid 微信OpenID
     * @return 是否存在
     */
    @Select("SELECT COUNT(1) FROM wx_user_bindings WHERE wx_openid = #{wxOpenid}")
    boolean existsByWxOpenid(@Param("wxOpenid") String wxOpenid);

    /**
     * 检查用户是否已绑定微信
     *
     * @param userId 用户ID
     * @return 是否已绑定
     */
    @Select("SELECT COUNT(1) FROM wx_user_bindings WHERE user_id = #{userId}")
    boolean existsByUserId(@Param("userId") UUID userId);
}
