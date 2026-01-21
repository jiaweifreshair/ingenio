package com.ingenio.backend.service;

import com.ingenio.backend.dto.billing.CreditPackageDTO;
import com.ingenio.backend.dto.billing.UserCreditsDTO;
import com.ingenio.backend.entity.billing.CreditTransactionEntity;
import com.ingenio.backend.entity.billing.UserCreditsEntity;
import com.ingenio.backend.mapper.billing.CreditTransactionMapper;
import com.ingenio.backend.mapper.billing.UserCreditsMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * BillingService 单元测试
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BillingServiceTest {

    @Mock
    private UserCreditsMapper userCreditsMapper;

    @Mock
    private CreditTransactionMapper creditTransactionMapper;

    private BillingService billingService;

    private UUID testUserId;

    @BeforeEach
    void setUp() {
        billingService = new BillingService(userCreditsMapper, creditTransactionMapper);
        testUserId = UUID.randomUUID();
    }

    // ========== getUserCredits 测试 ==========

    @Test
    void getUserCredits_existingUser_returnsCredits() {
        // Given
        UserCreditsEntity entity = UserCreditsEntity.builder()
                .userId(testUserId)
                .totalCredits(100)
                .usedCredits(30)
                .build();
        when(userCreditsMapper.selectOne(any())).thenReturn(entity);

        // When
        UserCreditsDTO result = billingService.getUserCredits(testUserId);

        // Then
        assertEquals(100, result.getTotal());
        assertEquals(30, result.getUsed());
        assertEquals(70, result.getRemaining());
    }

    @Test
    void getUserCredits_newUser_createsAndReturnsZeroCredits() {
        // Given
        when(userCreditsMapper.selectOne(any())).thenReturn(null);
        when(userCreditsMapper.insert(any(UserCreditsEntity.class))).thenReturn(1);

        // When
        UserCreditsDTO result = billingService.getUserCredits(testUserId);

        // Then
        assertEquals(0, result.getTotal());
        assertEquals(0, result.getUsed());
        assertEquals(0, result.getRemaining());
        verify(userCreditsMapper).insert(any(UserCreditsEntity.class));
    }

    // ========== getPackages 测试 ==========

    @Test
    void getPackages_returnsAllPackages() {
        // When
        List<CreditPackageDTO> packages = billingService.getPackages();

        // Then
        assertEquals(3, packages.size());

        // 验证基础套餐
        CreditPackageDTO pack10 = packages.stream()
                .filter(p -> "PACK_10".equals(p.getCode()))
                .findFirst()
                .orElseThrow();
        assertEquals("基础套餐", pack10.getName());
        assertEquals(10, pack10.getCredits());

        // 验证标准套餐
        CreditPackageDTO pack30 = packages.stream()
                .filter(p -> "PACK_30".equals(p.getCode()))
                .findFirst()
                .orElseThrow();
        assertEquals("标准套餐", pack30.getName());
        assertEquals(30, pack30.getCredits());

        // 验证专业套餐
        CreditPackageDTO pack80 = packages.stream()
                .filter(p -> "PACK_80".equals(p.getCode()))
                .findFirst()
                .orElseThrow();
        assertEquals("专业套餐", pack80.getName());
        assertEquals(80, pack80.getCredits());
    }

    // ========== hasCredits 测试 ==========

    @Test
    void hasCredits_sufficientBalance_returnsTrue() {
        // Given
        UserCreditsEntity entity = UserCreditsEntity.builder()
                .userId(testUserId)
                .totalCredits(10)
                .usedCredits(5)
                .build();
        when(userCreditsMapper.selectOne(any())).thenReturn(entity);

        // When
        boolean result = billingService.hasCredits(testUserId, 5);

        // Then
        assertTrue(result);
    }

    @Test
    void hasCredits_insufficientBalance_returnsFalse() {
        // Given
        UserCreditsEntity entity = UserCreditsEntity.builder()
                .userId(testUserId)
                .totalCredits(10)
                .usedCredits(8)
                .build();
        when(userCreditsMapper.selectOne(any())).thenReturn(entity);

        // When
        boolean result = billingService.hasCredits(testUserId, 5);

        // Then
        assertFalse(result);
    }

    @Test
    void hasCredits_exactBalance_returnsTrue() {
        // Given
        UserCreditsEntity entity = UserCreditsEntity.builder()
                .userId(testUserId)
                .totalCredits(10)
                .usedCredits(5)
                .build();
        when(userCreditsMapper.selectOne(any())).thenReturn(entity);

        // When
        boolean result = billingService.hasCredits(testUserId, 5);

        // Then
        assertTrue(result);
    }

    // ========== consumeCredits 测试 ==========

    @Test
    void consumeCredits_sufficientBalance_consumesAndReturnsTrue() {
        // Given
        UserCreditsEntity entity = UserCreditsEntity.builder()
                .id(UUID.randomUUID())
                .userId(testUserId)
                .totalCredits(10)
                .usedCredits(3)
                .build();
        when(userCreditsMapper.selectOne(any())).thenReturn(entity);
        when(userCreditsMapper.updateById(any(UserCreditsEntity.class))).thenReturn(1);
        when(creditTransactionMapper.insert(any(CreditTransactionEntity.class))).thenReturn(1);

        // When
        boolean result = billingService.consumeCredits(testUserId, 2, "app-123", "测试消费");

        // Then
        assertTrue(result);
        assertEquals(5, entity.getUsedCredits()); // 3 + 2 = 5

        // 验证交易记录
        ArgumentCaptor<CreditTransactionEntity> captor = ArgumentCaptor.forClass(CreditTransactionEntity.class);
        verify(creditTransactionMapper).insert(captor.capture());
        CreditTransactionEntity transaction = captor.getValue();
        assertEquals("CONSUME", transaction.getType());
        assertEquals(-2, transaction.getCreditsChange());
        assertEquals(7, transaction.getCreditsBefore()); // 10 - 3 = 7
        assertEquals(5, transaction.getCreditsAfter()); // 7 - 2 = 5
    }

    @Test
    void consumeCredits_insufficientBalance_returnsFalse() {
        // Given
        UserCreditsEntity entity = UserCreditsEntity.builder()
                .userId(testUserId)
                .totalCredits(10)
                .usedCredits(9)
                .build();
        when(userCreditsMapper.selectOne(any())).thenReturn(entity);

        // When
        boolean result = billingService.consumeCredits(testUserId, 5, "app-123", "测试消费");

        // Then
        assertFalse(result);
        verify(userCreditsMapper, never()).updateById(any(UserCreditsEntity.class));
        verify(creditTransactionMapper, never()).insert(any(CreditTransactionEntity.class));
    }

    // ========== addCredits 测试 ==========

    @Test
    void addCredits_addsCreditsAndRecordsTransaction() {
        // Given
        UUID orderId = UUID.randomUUID();
        UserCreditsEntity entity = UserCreditsEntity.builder()
                .id(UUID.randomUUID())
                .userId(testUserId)
                .totalCredits(10)
                .usedCredits(5)
                .build();
        when(userCreditsMapper.selectOne(any())).thenReturn(entity);
        when(userCreditsMapper.updateById(any(UserCreditsEntity.class))).thenReturn(1);
        when(creditTransactionMapper.insert(any(CreditTransactionEntity.class))).thenReturn(1);

        // When
        billingService.addCredits(testUserId, orderId, 30, "购买标准套餐");

        // Then
        assertEquals(40, entity.getTotalCredits()); // 10 + 30 = 40

        // 验证交易记录
        ArgumentCaptor<CreditTransactionEntity> captor = ArgumentCaptor.forClass(CreditTransactionEntity.class);
        verify(creditTransactionMapper).insert(captor.capture());
        CreditTransactionEntity transaction = captor.getValue();
        assertEquals("PURCHASE", transaction.getType());
        assertEquals(30, transaction.getCreditsChange());
        assertEquals(5, transaction.getCreditsBefore()); // remaining = 10 - 5 = 5
        assertEquals(35, transaction.getCreditsAfter()); // 5 + 30 = 35
        assertEquals(orderId, transaction.getOrderId());
    }
}
