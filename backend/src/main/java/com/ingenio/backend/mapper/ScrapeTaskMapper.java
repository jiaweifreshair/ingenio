package com.ingenio.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ingenio.backend.entity.ScrapeTaskEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 网站抓取任务Mapper接口
 * 从现有网站抓取内容生成AppSpec的数据访问
 */
@Mapper
public interface ScrapeTaskMapper extends BaseMapper<ScrapeTaskEntity> {
    // 基础CRUD操作由BaseMapper提供
    // 复杂查询在Service层使用QueryWrapper实现
}
