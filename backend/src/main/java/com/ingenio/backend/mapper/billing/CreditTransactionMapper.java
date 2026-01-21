package com.ingenio.backend.mapper.billing;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ingenio.backend.entity.billing.CreditTransactionEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 余额变动记录 Mapper
 */
@Mapper
public interface CreditTransactionMapper extends BaseMapper<CreditTransactionEntity> {
}
