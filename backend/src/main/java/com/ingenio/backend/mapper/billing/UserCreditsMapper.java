package com.ingenio.backend.mapper.billing;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ingenio.backend.entity.billing.UserCreditsEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户余额 Mapper
 */
@Mapper
public interface UserCreditsMapper extends BaseMapper<UserCreditsEntity> {
}
