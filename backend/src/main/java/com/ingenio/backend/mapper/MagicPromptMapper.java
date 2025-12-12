package com.ingenio.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ingenio.backend.entity.MagicPromptEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 魔法提示词Mapper接口
 * 提供魔法提示词模板表的数据库访问操作
 */
@Mapper
public interface MagicPromptMapper extends BaseMapper<MagicPromptEntity> {

    /**
     * 根据年龄分组和分类查询魔法提示词列表
     *
     * @param ageGroup 年龄分组
     * @param category 分类
     * @return 魔法提示词列表
     */
    @Select("SELECT * FROM magic_prompts WHERE age_group = #{ageGroup} AND category = #{category} AND status = 'active' ORDER BY usage_count DESC")
    List<MagicPromptEntity> findByAgeGroupAndCategory(
        @Param("ageGroup") String ageGroup,
        @Param("category") String category
    );

    /**
     * 根据年龄分组查询热门魔法提示词（按使用次数排序）
     *
     * @param ageGroup 年龄分组
     * @param limit 限制数量
     * @return 魔法提示词列表
     */
    @Select("SELECT * FROM magic_prompts WHERE age_group = #{ageGroup} AND status = 'active' ORDER BY usage_count DESC LIMIT #{limit}")
    List<MagicPromptEntity> findTopByAgeGroup(
        @Param("ageGroup") String ageGroup,
        @Param("limit") int limit
    );

    /**
     * 根据难度级别分页查询魔法提示词
     *
     * @param page 分页对象
     * @param difficultyLevel 难度级别
     * @return 分页结果
     */
    @Select("SELECT * FROM magic_prompts WHERE difficulty_level = #{difficultyLevel} AND status = 'active' ORDER BY created_at DESC")
    IPage<MagicPromptEntity> selectPageByDifficultyLevel(
        Page<MagicPromptEntity> page,
        @Param("difficultyLevel") String difficultyLevel
    );

    /**
     * 全文搜索魔法提示词（按标题和描述）
     *
     * @param page 分页对象
     * @param searchQuery 搜索关键词
     * @return 分页结果
     */
    @Select("SELECT * FROM magic_prompts WHERE " +
            "(to_tsvector('simple', title) @@ plainto_tsquery('simple', #{searchQuery}) OR " +
            "to_tsvector('simple', description) @@ plainto_tsquery('simple', #{searchQuery})) " +
            "AND status = 'active' " +
            "ORDER BY usage_count DESC")
    IPage<MagicPromptEntity> searchMagicPrompts(
        Page<MagicPromptEntity> page,
        @Param("searchQuery") String searchQuery
    );

    /**
     * 增加魔法提示词使用次数
     *
     * @param id 魔法提示词ID
     * @return 影响行数
     */
    @Update("UPDATE magic_prompts SET usage_count = usage_count + 1 WHERE id = #{id}")
    int incrementUsageCount(@Param("id") java.util.UUID id);

    /**
     * 增加魔法提示词点赞数
     *
     * @param id 魔法提示词ID
     * @return 影响行数
     */
    @Update("UPDATE magic_prompts SET like_count = like_count + 1 WHERE id = #{id}")
    int incrementLikeCount(@Param("id") java.util.UUID id);

    /**
     * 减少魔法提示词点赞数
     *
     * @param id 魔法提示词ID
     * @return 影响行数
     */
    @Update("UPDATE magic_prompts SET like_count = like_count - 1 WHERE id = #{id} AND like_count > 0")
    int decrementLikeCount(@Param("id") java.util.UUID id);
}
