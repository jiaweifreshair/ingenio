package com.ingenio.backend.controller.admin;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Admin端点，用于状态检查和基本管理操作。
 * 所有在此控制器下的端点都应通过服务间JWT进行保护。
 */
@RestController
@RequestMapping({"/admin", "/api/admin"})
public class AdminStatusController {

    /**
     * 提供一个基本的健康检查端点，用于验证Admin API的可访问性和认证状态。
     * @return 一个表示成功的JSON响应
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, String>> getStatus() {
        return ResponseEntity.ok(Map.of("status", "Admin API is operational"));
    }
}
