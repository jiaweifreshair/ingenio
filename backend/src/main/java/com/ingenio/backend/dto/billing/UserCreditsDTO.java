package com.ingenio.backend.dto.billing;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户余额 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserCreditsDTO {

    /**
     * 总购买次数
     */
    private Integer total;

    /**
     * 已使用次数
     */
    private Integer used;

    /**
     * 剩余次数
     */
    private Integer remaining;
}
