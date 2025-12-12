package com.ingenio.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ingenio.backend.entity.UserLogEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.UUID;

/**
 * 用户日志Mapper接口
 * 提供用户日志表的数据库访问操作
 */
@Mapper
public interface UserLogMapper extends BaseMapper<UserLogEntity> {

    /**
     * 分页查询用户日志
     *
     * @param page   分页对象
     * @param userId 用户ID
     * @return 分页结果
     */
    @Select("SELECT * FROM user_logs WHERE user_id = #{userId} ORDER BY created_at DESC")
    IPage<UserLogEntity> findByUserIdWithPage(Page<UserLogEntity> page, @Param("userId") UUID userId);

    /**
     * 根据操作分类分页查询
     *
     * @param page     分页对象
     * @param userId   用户ID
     * @param category 操作分类
     * @return 分页结果
     */
    @Select("SELECT * FROM user_logs WHERE user_id = #{userId} AND action_category = #{category} ORDER BY created_at DESC")
    IPage<UserLogEntity> findByUserIdAndCategoryWithPage(
            Page<UserLogEntity> page,
            @Param("userId") UUID userId,
            @Param("category") String category
    );

    /**
     * 根据操作状态分页查询
     *
     * @param page   分页对象
     * @param userId 用户ID
     * @param status 操作状态
     * @return 分页结果
     */
    @Select("SELECT * FROM user_logs WHERE user_id = #{userId} AND status = #{status} ORDER BY created_at DESC")
    IPage<UserLogEntity> findByUserIdAndStatusWithPage(
            Page<UserLogEntity> page,
            @Param("userId") UUID userId,
            @Param("status") String status
    );
}
