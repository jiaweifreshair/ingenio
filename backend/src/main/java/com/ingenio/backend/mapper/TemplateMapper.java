package com.ingenio.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ingenio.backend.entity.TemplateEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 模板Mapper接口
 * 官方和社区应用模板的数据访问
 */
@Mapper
public interface TemplateMapper extends BaseMapper<TemplateEntity> {
    // 基础CRUD操作由BaseMapper提供
    // 复杂查询在Service层使用QueryWrapper实现
}
