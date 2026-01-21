package com.ingenio.backend.mapper.billing;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ingenio.backend.entity.billing.PayOrderEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 支付订单 Mapper
 */
@Mapper
public interface PayOrderMapper extends BaseMapper<PayOrderEntity> {
}
