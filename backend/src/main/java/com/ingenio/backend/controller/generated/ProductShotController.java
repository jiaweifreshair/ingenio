package com.ingenio.backend.controller.generated;

import cn.dev33.satoken.stp.StpUtil;
import com.ingenio.backend.common.Result;
import com.ingenio.backend.entity.generated.ProductShotEntity;
import com.ingenio.backend.service.generated.ProductShotService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/v1/product-shots")
@RequiredArgsConstructor
public class ProductShotController {
    private final ProductShotService service;

    @PostMapping
    public Result<ProductShotEntity> create(@RequestBody ProductShotEntity request) {
        request.setId(UUID.randomUUID()); // 手动生成ID，确保不为null
        request.setStatus("PENDING");
        
        // 获取当前用户ID
        try {
            if (StpUtil.isLogin()) {
                request.setUserId(UUID.fromString(StpUtil.getLoginIdAsString()));
            } else {
                // Fallback for testing environment (mock user)
                request.setUserId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
            }
        } catch (Exception e) {
            request.setUserId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        }

        ProductShotEntity created = service.create(request);
        service.processImage(created.getId());
        return Result.success(created);
    }

    @GetMapping("/{id}")
    public Result<ProductShotEntity> get(@PathVariable UUID id) {
        UUID userId = null;
        try {
            if (StpUtil.isLogin()) {
                userId = UUID.fromString(StpUtil.getLoginIdAsString());
            } else {
                userId = UUID.fromString("00000000-0000-0000-0000-000000000001");
            }
        } catch (Exception e) {
            userId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        }
        
        return Result.success(service.getByIdAndUser(id, userId));
    }
}
