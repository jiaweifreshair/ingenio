package com.ingenio.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ingenio.backend.client.JeecgPayClient;
import com.ingenio.backend.dto.billing.CreateOrderRequest;
import com.ingenio.backend.dto.billing.CreateOrderResponse;
import com.ingenio.backend.dto.billing.PayOrderDTO;
import com.ingenio.backend.entity.billing.PayOrderEntity;
import com.ingenio.backend.enums.CreditPackage;
import com.ingenio.backend.enums.PayOrderStatus;
import com.ingenio.backend.mapper.billing.PayOrderMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 支付订单服务
 * 管理订单创建、查询、支付回调
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PayOrderService {

    private final PayOrderMapper payOrderMapper;
    private final BillingService billingService;
    private final JeecgPayClient jeecgPayClient;

    /**
     * 创建支付订单
     */
    @Transactional
    public CreateOrderResponse createOrder(UUID userId, CreateOrderRequest request) {
        // 获取套餐信息
        CreditPackage pkg = CreditPackage.fromCode(request.getPackageCode());

        // 生成订单号
        String orderNo = generateOrderNo();

        // 创建订单实体
        PayOrderEntity order = PayOrderEntity.builder()
                .orderNo(orderNo)
                .userId(userId)
                .packageCode(pkg.getCode())
                .packageName(pkg.getName())
                .creditsAmount(pkg.getCredits())
                .amount(pkg.getPrice())
                .currency("CNY")
                .payChannel(request.getPayChannel())
                .status(PayOrderStatus.PENDING.getCode())
                .expireTime(Instant.now().plus(30, ChronoUnit.MINUTES))
                .build();

        payOrderMapper.insert(order);
        log.info("创建支付订单: orderNo={}, userId={}, package={}", orderNo, userId, pkg.getCode());

        // 调用 JeecgBoot 获取支付参数
        Map<String, Object> payResult = jeecgPayClient.getAlipayParams(
                orderNo,
                pkg.getPrice(),
                pkg.getName(),
                request.getPayChannel()
        );

        // 更新订单支付数据
        order.setPayDataType((String) payResult.get("payDataType"));
        order.setPayData((String) payResult.get("payData"));
        payOrderMapper.updateById(order);

        return CreateOrderResponse.builder()
                .orderNo(orderNo)
                .payDataType(order.getPayDataType())
                .payData(order.getPayData())
                .expireTime(order.getExpireTime().toString())
                .build();
    }

    /**
     * 查询订单
     */
    public PayOrderDTO getOrder(UUID userId, String orderNo) {
        LambdaQueryWrapper<PayOrderEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PayOrderEntity::getUserId, userId)
                .eq(PayOrderEntity::getOrderNo, orderNo);
        PayOrderEntity order = payOrderMapper.selectOne(wrapper);

        if (order == null) {
            throw new RuntimeException("订单不存在");
        }

        return toDTO(order);
    }

    /**
     * 获取订单列表
     */
    public List<PayOrderDTO> getOrders(UUID userId, int page, int size) {
        LambdaQueryWrapper<PayOrderEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PayOrderEntity::getUserId, userId)
                .orderByDesc(PayOrderEntity::getCreatedAt);

        Page<PayOrderEntity> pageResult = payOrderMapper.selectPage(new Page<>(page, size), wrapper);

        return pageResult.getRecords().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * 处理支付宝回调
     */
    @Transactional
    public String handleAlipayNotify(HttpServletRequest request) {
        // 获取回调参数
        String orderNo = request.getParameter("out_trade_no");
        String tradeNo = request.getParameter("trade_no");
        String tradeStatus = request.getParameter("trade_status");

        log.info("收到支付宝回调: orderNo={}, tradeNo={}, status={}", orderNo, tradeNo, tradeStatus);

        // 查询订单
        LambdaQueryWrapper<PayOrderEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PayOrderEntity::getOrderNo, orderNo);
        PayOrderEntity order = payOrderMapper.selectOne(wrapper);

        if (order == null) {
            log.error("订单不存在: {}", orderNo);
            return "fail";
        }

        // 已处理过的订单直接返回成功
        if (PayOrderStatus.PAID.getCode().equals(order.getStatus())) {
            log.info("订单已处理: {}", orderNo);
            return "success";
        }

        // 处理支付成功
        if ("TRADE_SUCCESS".equals(tradeStatus) || "TRADE_FINISHED".equals(tradeStatus)) {
            order.setStatus(PayOrderStatus.PAID.getCode());
            order.setTransactionId(tradeNo);
            order.setPayTime(Instant.now());
            payOrderMapper.updateById(order);

            // 充值余额
            billingService.addCredits(
                    order.getUserId(),
                    order.getId(),
                    order.getCreditsAmount(),
                    "购买" + order.getPackageName()
            );

            log.info("订单支付成功: orderNo={}, credits={}", orderNo, order.getCreditsAmount());
            return "success";
        }

        return "fail";
    }

    /**
     * 生成订单号
     */
    private String generateOrderNo() {
        return "PAY" + System.currentTimeMillis() + String.format("%04d", (int) (Math.random() * 10000));
    }

    /**
     * 转换为 DTO
     */
    private PayOrderDTO toDTO(PayOrderEntity entity) {
        return PayOrderDTO.builder()
                .orderNo(entity.getOrderNo())
                .packageName(entity.getPackageName())
                .creditsAmount(entity.getCreditsAmount())
                .amount(entity.getAmount())
                .status(entity.getStatus())
                .createdAt(entity.getCreatedAt() != null ? entity.getCreatedAt().toString() : null)
                .payTime(entity.getPayTime() != null ? entity.getPayTime().toString() : null)
                .build();
    }
}
