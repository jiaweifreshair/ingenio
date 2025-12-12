package com.ingenio.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ingenio.backend.entity.IndustryTemplateEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 行业模板库 Mapper 接口
 *
 * Phase X.4: 行业模板库功能 - 数据访问层
 *
 * 基础操作（由 MyBatis-Plus BaseMapper 自动提供）：
 * - insert(entity)           : 插入单条记录
 * - updateById(entity)       : 根据ID更新记录
 * - deleteById(id)           : 根据ID删除记录
 * - selectById(id)           : 根据ID查询记录
 * - selectList(queryWrapper) : 条件查询（支持复杂查询）
 * - selectPage(page, wrapper): 分页查询
 *
 * 复杂查询实现方式：
 * 1. 基于关键词匹配：在 Service 层使用 LambdaQueryWrapper + JSONB 查询
 * 2. 基于分类浏览：使用 QueryWrapper 的 eq() 方法
 * 3. 热门模板排序：使用 OrderByDesc(usageCount)
 * 4. 优质模板排序：使用 OrderByDesc(rating)
 *
 * 示例查询（在 Service 层实现）：
 * <pre>
 * // 1. 关键词匹配查询（使用 GIN 索引）
 * LambdaQueryWrapper<IndustryTemplateEntity> wrapper = new LambdaQueryWrapper<>();
 * wrapper.apply("keywords @> CAST('[\"民宿\", \"预订\"]' AS JSONB)")
 *        .eq(IndustryTemplateEntity::getIsActive, true)
 *        .orderByDesc(IndustryTemplateEntity::getUsageCount);
 * List<IndustryTemplateEntity> templates = templateMapper.selectList(wrapper);
 *
 * // 2. 分类浏览
 * LambdaQueryWrapper<IndustryTemplateEntity> wrapper = new LambdaQueryWrapper<>();
 * wrapper.eq(IndustryTemplateEntity::getCategory, "生活服务类")
 *        .eq(IndustryTemplateEntity::getSubcategory, "民宿预订")
 *        .eq(IndustryTemplateEntity::getIsActive, true);
 *
 * // 3. 热门模板（Top 10）
 * LambdaQueryWrapper<IndustryTemplateEntity> wrapper = new LambdaQueryWrapper<>();
 * wrapper.eq(IndustryTemplateEntity::getIsActive, true)
 *        .orderByDesc(IndustryTemplateEntity::getUsageCount)
 *        .last("LIMIT 10");
 *
 * // 4. 优质模板（评分 >= 4.0）
 * LambdaQueryWrapper<IndustryTemplateEntity> wrapper = new LambdaQueryWrapper<>();
 * wrapper.eq(IndustryTemplateEntity::getIsActive, true)
 *        .ge(IndustryTemplateEntity::getRating, new BigDecimal("4.0"))
 *        .orderByDesc(IndustryTemplateEntity::getRating);
 * </pre>
 *
 * 性能优化建议：
 * 1. 关键词查询使用 GIN 索引 (idx_industry_templates_keywords)
 * 2. 分类查询使用复合索引 (idx_industry_templates_category)
 * 3. 排序查询使用专用索引 (idx_industry_templates_usage_count, idx_industry_templates_rating)
 * 4. 分页查询使用 MyBatis-Plus 内置的 Page 对象
 *
 * @author Claude
 * @since 2025-11-16 (Phase X.4 行业模板库开发)
 */
@Mapper
public interface IndustryTemplateMapper extends BaseMapper<IndustryTemplateEntity> {
    // 基础CRUD操作由 MyBatis-Plus BaseMapper 提供
    // 复杂查询在 Service 层使用 QueryWrapper 实现
    // 自定义SQL查询（如果需要）可在此接口添加 @Select/@Update 注解的方法
}
