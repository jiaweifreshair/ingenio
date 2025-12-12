package com.ingenio.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ingenio.backend.entity.TemplateUsageEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 模板使用记录Mapper接口
 * 统计模板使用情况和用户反馈的数据访问
 */
@Mapper
public interface TemplateUsageMapper extends BaseMapper<TemplateUsageEntity> {
    // 基础CRUD操作由BaseMapper提供
    // 复杂查询在Service层使用QueryWrapper实现
}
