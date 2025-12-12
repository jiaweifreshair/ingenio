package com.ingenio.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ingenio.backend.entity.GenerationVersionEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 生成版本管理Mapper
 */
@Mapper
public interface GenerationVersionMapper extends BaseMapper<GenerationVersionEntity> {
}
