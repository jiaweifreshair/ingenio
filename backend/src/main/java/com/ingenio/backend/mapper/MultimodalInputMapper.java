package com.ingenio.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ingenio.backend.entity.MultimodalInputEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 多模态输入Mapper接口
 * 支持文本/语音/视频/图像输入的数据访问
 */
@Mapper
public interface MultimodalInputMapper extends BaseMapper<MultimodalInputEntity> {
    // 基础CRUD操作由BaseMapper提供
    // 复杂查询在Service层使用QueryWrapper实现
}
