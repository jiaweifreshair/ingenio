package com.ingenio.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ingenio.backend.entity.JeecgCapabilityEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * JeecgBoot能力清单 Mapper 接口
 *
 * G3引擎JeecgBoot能力集成 - 数据访问层
 *
 * 基础操作（由 MyBatis-Plus BaseMapper 自动提供）：
 * - insert(entity)           : 插入单条记录
 * - updateById(entity)       : 根据ID更新记录
 * - deleteById(id)           : 根据ID删除记录
 * - selectById(id)           : 根据ID查询记录
 * - selectList(queryWrapper) : 条件查询
 * - selectPage(page, wrapper): 分页查询
 *
 * 常用查询示例（在 Service 层实现）：
 * <pre>
 * // 1. 按分类查询启用的能力
 * LambdaQueryWrapper<JeecgCapabilityEntity> wrapper = new LambdaQueryWrapper<>();
 * wrapper.eq(JeecgCapabilityEntity::getCategory, "infrastructure")
 *        .eq(JeecgCapabilityEntity::getIsActive, true)
 *        .orderByAsc(JeecgCapabilityEntity::getSortOrder);
 *
 * // 2. 按代码查询单个能力
 * LambdaQueryWrapper<JeecgCapabilityEntity> wrapper = new LambdaQueryWrapper<>();
 * wrapper.eq(JeecgCapabilityEntity::getCode, "auth")
 *        .eq(JeecgCapabilityEntity::getIsActive, true);
 * </pre>
 *
 * @author Claude
 * @since 2025-01-08 (G3引擎JeecgBoot能力集成)
 */
@Mapper
public interface JeecgCapabilityMapper extends BaseMapper<JeecgCapabilityEntity> {
    // 基础CRUD操作由 MyBatis-Plus BaseMapper 提供
}
