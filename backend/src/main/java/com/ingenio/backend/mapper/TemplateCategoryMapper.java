package com.ingenio.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ingenio.backend.entity.TemplateCategoryEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 模板分类Mapper接口
 * 支持层级分类的数据访问
 */
@Mapper
public interface TemplateCategoryMapper extends BaseMapper<TemplateCategoryEntity> {
    // 基础CRUD操作由BaseMapper提供
    // 复杂查询在Service层使用QueryWrapper实现
}
