package com.ingenio.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ingenio.backend.entity.AuditLogEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 审计日志 Mapper 接口
 *
 * @author Claude
 * @since 2025-12-31 (JeecgBoot 集成)
 */
@Mapper
public interface AuditLogMapper extends BaseMapper<AuditLogEntity> {
}
