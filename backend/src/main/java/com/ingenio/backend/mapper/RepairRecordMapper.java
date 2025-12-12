package com.ingenio.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ingenio.backend.entity.RepairRecordEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

/**
 * V2.0 修复记录Mapper
 *
 * @author Ingenio Team
 * @since 2.0.0 Phase 4
 */
@Mapper
public interface RepairRecordMapper extends BaseMapper<RepairRecordEntity> {

    /**
     * 查询指定AppSpec的所有修复记录
     *
     * @param appSpecId AppSpec ID
     * @return 修复记录列表（按创建时间倒序）
     */
    List<RepairRecordEntity> selectByAppSpecId(@Param("appSpecId") UUID appSpecId);

    /**
     * 查询指定AppSpec的最新修复记录
     *
     * @param appSpecId AppSpec ID
     * @return 最新的修复记录
     */
    RepairRecordEntity selectLatestByAppSpecId(@Param("appSpecId") UUID appSpecId);

    /**
     * 查询正在进行的修复记录
     *
     * @param appSpecId AppSpec ID
     * @return 正在进行的修复记录
     */
    RepairRecordEntity selectActiveRepair(@Param("appSpecId") UUID appSpecId);

    /**
     * 统计指定AppSpec的修复成功率
     *
     * @param appSpecId AppSpec ID
     * @return 成功率（0.0-1.0）
     */
    Double calculateSuccessRate(@Param("appSpecId") UUID appSpecId);
}
