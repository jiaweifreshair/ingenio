package com.ingenio.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ingenio.backend.entity.SocialInteractionEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 社交互动Mapper接口
 * 提供社交互动表的数据库访问操作
 */
@Mapper
public interface SocialInteractionMapper extends BaseMapper<SocialInteractionEntity> {

    /**
     * 查询用户是否已点赞/收藏某项目
     *
     * @param userId 用户ID
     * @param projectId 项目ID
     * @param interactionType 互动类型
     * @return 互动记录（Optional）
     */
    @Select("SELECT * FROM social_interactions WHERE user_id = #{userId} AND project_id = #{projectId} AND interaction_type = #{interactionType} LIMIT 1")
    Optional<SocialInteractionEntity> findByUserIdAndProjectIdAndType(
        @Param("userId") UUID userId,
        @Param("projectId") UUID projectId,
        @Param("interactionType") String interactionType
    );

    /**
     * 根据项目ID和互动类型分页查询互动记录（用于评论列表）
     *
     * @param page 分页对象
     * @param projectId 项目ID
     * @param interactionType 互动类型
     * @return 分页结果
     */
    @Select("SELECT * FROM social_interactions WHERE project_id = #{projectId} AND interaction_type = #{interactionType} ORDER BY created_at DESC")
    IPage<SocialInteractionEntity> selectPageByProjectIdAndType(
        Page<SocialInteractionEntity> page,
        @Param("projectId") UUID projectId,
        @Param("interactionType") String interactionType
    );

    /**
     * 根据用户ID和互动类型查询互动记录列表
     *
     * @param userId 用户ID
     * @param interactionType 互动类型
     * @return 互动记录列表
     */
    @Select("SELECT * FROM social_interactions WHERE user_id = #{userId} AND interaction_type = #{interactionType} ORDER BY created_at DESC")
    List<SocialInteractionEntity> findByUserIdAndType(
        @Param("userId") UUID userId,
        @Param("interactionType") String interactionType
    );

    /**
     * 删除用户对某项目的互动记录（用于取消点赞/收藏）
     *
     * @param userId 用户ID
     * @param projectId 项目ID
     * @param interactionType 互动类型
     * @return 影响行数
     */
    @Delete("DELETE FROM social_interactions WHERE user_id = #{userId} AND project_id = #{projectId} AND interaction_type = #{interactionType}")
    int deleteByUserIdAndProjectIdAndType(
        @Param("userId") UUID userId,
        @Param("projectId") UUID projectId,
        @Param("interactionType") String interactionType
    );

    /**
     * 统计项目的某类互动数量
     *
     * @param projectId 项目ID
     * @param interactionType 互动类型
     * @return 互动数量
     */
    @Select("SELECT COUNT(*) FROM social_interactions WHERE project_id = #{projectId} AND interaction_type = #{interactionType}")
    long countByProjectIdAndType(
        @Param("projectId") UUID projectId,
        @Param("interactionType") String interactionType
    );
}
