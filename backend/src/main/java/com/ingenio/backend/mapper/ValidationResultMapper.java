package com.ingenio.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ingenio.backend.entity.ValidationResultEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

/**
 * V2.0 验证结果Mapper
 *
 * @author Ingenio Team
 * @since 2.0.0 Phase 3
 */
@Mapper
public interface ValidationResultMapper extends BaseMapper<ValidationResultEntity> {

    /**
     * 查询指定AppSpec的所有验证结果
     *
     * @param appSpecId AppSpec ID
     * @return 验证结果列表
     */
    List<ValidationResultEntity> selectByAppSpecId(@Param("appSpecId") UUID appSpecId);

    /**
     * 查询指定AppSpec的最新验证结果
     *
     * @param appSpecId AppSpec ID
     * @return 最新的验证结果
     */
    ValidationResultEntity selectLatestByAppSpecId(@Param("appSpecId") UUID appSpecId);

    /**
     * 查询指定类型的验证结果
     *
     * @param appSpecId AppSpec ID
     * @param validationType 验证类型
     * @return 验证结果
     */
    ValidationResultEntity selectByTypeAndAppSpecId(
            @Param("appSpecId") UUID appSpecId,
            @Param("validationType") String validationType
    );

    /**
     * 统计指定AppSpec的验证通过率
     *
     * @param appSpecId AppSpec ID
     * @return 通过率（0.0-1.0）
     */
    Double calculatePassRate(@Param("appSpecId") UUID appSpecId);
}
