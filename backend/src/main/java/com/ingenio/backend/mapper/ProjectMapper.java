package com.ingenio.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ingenio.backend.entity.ProjectEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.UUID;

/**
 * 项目Mapper接口
 * 提供项目表的数据库访问操作
 */
@Mapper
public interface ProjectMapper extends BaseMapper<ProjectEntity> {

    /**
     * 根据用户ID分页查询项目列表
     *
     * @param page 分页对象
     * @param userId 用户ID
     * @return 分页结果
     */
    @Select("SELECT * FROM projects WHERE user_id = #{userId} ORDER BY created_at DESC")
    IPage<ProjectEntity> selectPageByUserId(
        Page<ProjectEntity> page,
        @Param("userId") UUID userId
    );

    /**
     * 根据可见性和状态分页查询公开项目（社区广场）
     *
     * @param page 分页对象
     * @param visibility 可见性
     * @param status 状态
     * @return 分页结果
     */
    @Select("SELECT * FROM projects WHERE visibility = #{visibility} AND status = #{status} ORDER BY created_at DESC")
    IPage<ProjectEntity> selectPageByVisibilityAndStatus(
        Page<ProjectEntity> page,
        @Param("visibility") String visibility,
        @Param("status") String status
    );

    /**
     * 根据年龄分组和可见性查询项目列表
     *
     * @param ageGroup 年龄分组
     * @param visibility 可见性
     * @return 项目列表
     */
    @Select("SELECT * FROM projects WHERE age_group = #{ageGroup} AND visibility = #{visibility} AND status = 'published' ORDER BY like_count DESC LIMIT 20")
    List<ProjectEntity> findTopByAgeGroupAndVisibility(
        @Param("ageGroup") String ageGroup,
        @Param("visibility") String visibility
    );

    /**
     * 全文搜索项目（按名称和描述）
     *
     * @param page 分页对象
     * @param searchQuery 搜索关键词
     * @return 分页结果
     */
    @Select("SELECT * FROM projects WHERE " +
            "(to_tsvector('simple', name) @@ plainto_tsquery('simple', #{searchQuery}) OR " +
            "to_tsvector('simple', description) @@ plainto_tsquery('simple', #{searchQuery})) " +
            "AND visibility = 'public' AND status = 'published' " +
            "ORDER BY created_at DESC")
    IPage<ProjectEntity> searchProjects(
        Page<ProjectEntity> page,
        @Param("searchQuery") String searchQuery
    );

    /**
     * 增加项目浏览次数
     *
     * @param projectId 项目ID
     * @return 影响行数
     */
    @Update("UPDATE projects SET view_count = view_count + 1 WHERE id = #{projectId}")
    int incrementViewCount(@Param("projectId") UUID projectId);

    /**
     * 增加项目点赞数
     *
     * @param projectId 项目ID
     * @return 影响行数
     */
    @Update("UPDATE projects SET like_count = like_count + 1 WHERE id = #{projectId}")
    int incrementLikeCount(@Param("projectId") UUID projectId);

    /**
     * 减少项目点赞数
     *
     * @param projectId 项目ID
     * @return 影响行数
     */
    @Update("UPDATE projects SET like_count = like_count - 1 WHERE id = #{projectId} AND like_count > 0")
    int decrementLikeCount(@Param("projectId") UUID projectId);

    /**
     * 增加项目派生数
     *
     * @param projectId 项目ID
     * @return 影响行数
     */
    @Update("UPDATE projects SET fork_count = fork_count + 1 WHERE id = #{projectId}")
    int incrementForkCount(@Param("projectId") UUID projectId);
}
