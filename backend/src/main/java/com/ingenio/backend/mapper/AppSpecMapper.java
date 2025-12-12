package com.ingenio.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.ingenio.backend.entity.AppSpecEntity;
import com.ingenio.backend.config.UUIDv8TypeHandler;
import org.apache.ibatis.annotations.*;
import org.apache.ibatis.type.JdbcType;

import java.util.List;
import java.util.UUID;

/**
 * AppSpec Mapper接口
 * 提供AppSpec规范表的数据库访问操作
 *
 * 注意：由于使用了注解式@Select而非XML，需要显式配置ResultMap
 * 特别是JSONB类型字段（specContent, metadata）需要指定JacksonTypeHandler
 */
@Mapper
public interface AppSpecMapper extends BaseMapper<AppSpecEntity> {

    /**
     * AppSpec完整ResultMap配置
     * 显式指定UUID和JSONB字段的TypeHandler
     *
     * 背景：@TableName(autoResultMap = true)仅对XML Mapper生效，
     * 对注解式Mapper需要手动配置ResultMap
     */
    @Results(id = "AppSpecResultMap", value = {
        @Result(column = "id", property = "id", jdbcType = JdbcType.OTHER, typeHandler = UUIDv8TypeHandler.class),
        @Result(column = "tenant_id", property = "tenantId", jdbcType = JdbcType.OTHER, typeHandler = UUIDv8TypeHandler.class),
        @Result(column = "created_by_user_id", property = "createdByUserId", jdbcType = JdbcType.OTHER, typeHandler = UUIDv8TypeHandler.class),
        @Result(column = "parent_version_id", property = "parentVersionId", jdbcType = JdbcType.OTHER, typeHandler = UUIDv8TypeHandler.class),
        @Result(column = "spec_content", property = "specContent", jdbcType = JdbcType.OTHER, typeHandler = JacksonTypeHandler.class),
        @Result(column = "metadata", property = "metadata", jdbcType = JdbcType.OTHER, typeHandler = JacksonTypeHandler.class),
        @Result(column = "version", property = "version"),
        @Result(column = "status", property = "status"),
        @Result(column = "quality_score", property = "qualityScore"),
        @Result(column = "created_at", property = "createdAt"),
        @Result(column = "updated_at", property = "updatedAt")
    })
    @Select("SELECT * FROM app_specs LIMIT 0")
    AppSpecEntity __resultMapDefinition();

    /**
     * 根据租户ID和用户ID分页查询AppSpec列表
     *
     * @param page 分页对象
     * @param tenantId 租户ID
     * @param userId 用户ID
     * @return 分页结果
     */
    @ResultMap("AppSpecResultMap")
    @Select("SELECT * FROM app_specs WHERE tenant_id = #{tenantId} AND created_by_user_id = #{userId} ORDER BY created_at DESC")
    IPage<AppSpecEntity> selectPageByTenantIdAndUserId(
        Page<AppSpecEntity> page,
        @Param("tenantId") UUID tenantId,
        @Param("userId") UUID userId
    );

    /**
     * 根据租户ID和状态查询AppSpec列表
     *
     * @param tenantId 租户ID
     * @param status 状态
     * @return AppSpec列表
     */
    @ResultMap("AppSpecResultMap")
    @Select("SELECT * FROM app_specs WHERE tenant_id = #{tenantId} AND status = #{status} ORDER BY created_at DESC")
    List<AppSpecEntity> findByTenantIdAndStatus(
        @Param("tenantId") UUID tenantId,
        @Param("status") String status
    );

    /**
     * 根据ID和租户ID查询AppSpec（租户隔离）
     *
     * @param id AppSpec ID
     * @param tenantId 租户ID
     * @return AppSpec实体
     */
    @ResultMap("AppSpecResultMap")
    @Select("SELECT * FROM app_specs WHERE id = #{id} AND tenant_id = #{tenantId} LIMIT 1")
    AppSpecEntity findByIdAndTenantId(
        @Param("id") UUID id,
        @Param("tenantId") UUID tenantId
    );
}
