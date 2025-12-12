package com.ingenio.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ingenio.backend.entity.MCPAuthConfigEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * MCP认证配置Mapper接口
 * 存储多数据源认证信息的数据访问
 */
@Mapper
public interface MCPAuthConfigMapper extends BaseMapper<MCPAuthConfigEntity> {
    // 基础CRUD操作由BaseMapper提供
    // 复杂查询在Service层使用QueryWrapper实现
}
