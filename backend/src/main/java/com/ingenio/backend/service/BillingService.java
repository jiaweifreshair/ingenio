package com.ingenio.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ingenio.backend.dto.billing.CreditPackageDTO;
import com.ingenio.backend.dto.billing.UserCreditsDTO;
import com.ingenio.backend.entity.billing.CreditTransactionEntity;
import com.ingenio.backend.entity.billing.UserCreditsEntity;
import com.ingenio.backend.enums.CreditPackage;
import com.ingenio.backend.mapper.billing.CreditTransactionMapper;
import com.ingenio.backend.mapper.billing.UserCreditsMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 计费服务
 * 管理用户余额、套餐查询
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BillingService {

    private final UserCreditsMapper userCreditsMapper;
    private final CreditTransactionMapper creditTransactionMapper;

    /**
     * 获取用户余额
     */
    public UserCreditsDTO getUserCredits(UUID userId) {
        UserCreditsEntity credits = getOrCreateCredits(userId);
        return UserCreditsDTO.builder()
                .total(credits.getTotalCredits())
                .used(credits.getUsedCredits())
                .remaining(credits.getRemainingCredits())
                .build();
    }

    /**
     * 获取套餐列表
     */
    public List<CreditPackageDTO> getPackages() {
        return Arrays.stream(CreditPackage.values())
                .map(pkg -> CreditPackageDTO.builder()
                        .code(pkg.getCode())
                        .name(pkg.getName())
                        .credits(pkg.getCredits())
                        .price(pkg.getPrice())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * 检查用户是否有足够余额
     */
    public boolean hasCredits(UUID userId, int required) {
        UserCreditsEntity credits = getOrCreateCredits(userId);
        return credits.getRemainingCredits() >= required;
    }

    /**
     * 消费余额（生成代码时调用）
     *
     * @param userId      用户ID
     * @param amount      消费数量
     * @param referenceId 关联业务ID（如appSpecId）
     * @param description 描述
     * @return 是否成功
     */
    @Transactional
    public boolean consumeCredits(UUID userId, int amount, String referenceId, String description) {
        UserCreditsEntity credits = getOrCreateCredits(userId);

        if (credits.getRemainingCredits() < amount) {
            log.warn("用户 {} 余额不足，需要 {} 次，剩余 {} 次", userId, amount, credits.getRemainingCredits());
            return false;
        }

        int before = credits.getRemainingCredits();
        credits.setUsedCredits(credits.getUsedCredits() + amount);
        userCreditsMapper.updateById(credits);

        // 记录变动
        CreditTransactionEntity transaction = CreditTransactionEntity.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .type("CONSUME")
                .creditsChange(-amount)
                .creditsBefore(before)
                .creditsAfter(before - amount)
                .referenceId(referenceId)
                .description(description)
                .build();
        creditTransactionMapper.insert(transaction);

        log.info("用户 {} 消费 {} 次，剩余 {} 次", userId, amount, before - amount);
        return true;
    }

    /**
     * 充值余额（支付成功后调用）
     */
    @Transactional
    public void addCredits(UUID userId, UUID orderId, int amount, String description) {
        UserCreditsEntity credits = getOrCreateCredits(userId);

        int before = credits.getRemainingCredits();
        credits.setTotalCredits(credits.getTotalCredits() + amount);
        userCreditsMapper.updateById(credits);

        // 记录变动
        CreditTransactionEntity transaction = CreditTransactionEntity.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .orderId(orderId)
                .type("PURCHASE")
                .creditsChange(amount)
                .creditsBefore(before)
                .creditsAfter(before + amount)
                .description(description)
                .build();
        creditTransactionMapper.insert(transaction);

        log.info("用户 {} 充值 {} 次，当前余额 {} 次", userId, amount, before + amount);
    }

    /**
     * 获取或创建用户余额记录
     */
    private UserCreditsEntity getOrCreateCredits(UUID userId) {
        LambdaQueryWrapper<UserCreditsEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserCreditsEntity::getUserId, userId);
        UserCreditsEntity credits = userCreditsMapper.selectOne(wrapper);

        if (credits == null) {
            credits = UserCreditsEntity.builder()
                    .userId(userId)
                    .totalCredits(0)
                    .usedCredits(0)
                    .build();
            userCreditsMapper.insert(credits);
            log.info("为用户 {} 创建余额记录", userId);
        }

        return credits;
    }
}
