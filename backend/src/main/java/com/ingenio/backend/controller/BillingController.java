package com.ingenio.backend.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import com.ingenio.backend.common.Result;
import com.ingenio.backend.dto.billing.*;
import com.ingenio.backend.service.BillingService;
import com.ingenio.backend.service.PayOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * 计费管理控制器
 * 用户余额、套餐购买、订单管理
 */
@Slf4j
@RestController
@RequestMapping("/v1/billing")
@RequiredArgsConstructor
@Tag(name = "计费管理", description = "用户余额、套餐购买、订单管理")
public class BillingController {

    private final BillingService billingService;
    private final PayOrderService payOrderService;

    /**
     * 获取用户余额
     */
    @GetMapping("/credits")
    @SaCheckLogin
    @Operation(summary = "获取用户余额")
    public Result<UserCreditsDTO> getUserCredits() {
        UUID userId = UUID.fromString(StpUtil.getLoginIdAsString());
        return Result.success(billingService.getUserCredits(userId));
    }

    /**
     * 获取套餐列表
     */
    @GetMapping("/packages")
    @Operation(summary = "获取套餐列表")
    public Result<List<CreditPackageDTO>> getPackages() {
        return Result.success(billingService.getPackages());
    }

    /**
     * 创建支付订单
     */
    @PostMapping("/orders")
    @SaCheckLogin
    @Operation(summary = "创建支付订单")
    public Result<CreateOrderResponse> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        UUID userId = UUID.fromString(StpUtil.getLoginIdAsString());
        return Result.success(payOrderService.createOrder(userId, request));
    }

    /**
     * 查询订单状态
     */
    @GetMapping("/orders/{orderNo}")
    @SaCheckLogin
    @Operation(summary = "查询订单状态")
    public Result<PayOrderDTO> getOrder(@PathVariable String orderNo) {
        UUID userId = UUID.fromString(StpUtil.getLoginIdAsString());
        return Result.success(payOrderService.getOrder(userId, orderNo));
    }

    /**
     * 获取订单列表
     */
    @GetMapping("/orders")
    @SaCheckLogin
    @Operation(summary = "获取订单列表")
    public Result<List<PayOrderDTO>> getOrders(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        UUID userId = UUID.fromString(StpUtil.getLoginIdAsString());
        return Result.success(payOrderService.getOrders(userId, page, size));
    }

    /**
     * 支付宝异步回调
     */
    @PostMapping("/notify/alipay")
    @Operation(summary = "支付宝回调")
    public String alipayNotify(HttpServletRequest request) {
        return payOrderService.handleAlipayNotify(request);
    }
}
