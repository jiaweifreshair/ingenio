package com.ingenio.backend.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.ingenio.backend.common.Result;
import com.ingenio.backend.dto.DesignRequest;
import com.ingenio.backend.dto.DesignVariant;
import com.ingenio.backend.dto.request.Generate7StylesRequest;
import com.ingenio.backend.dto.response.Generate7StylesResponse;
import com.ingenio.backend.service.SuperDesignService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;

/**
 * SuperDesign AI设计生成控制器
 *
 * 功能：
 * 1. 并行生成3个不同风格的UI设计方案
 * 2. 获取设计方案详情
 * 3. 选择最终使用的设计方案
 *
 * 所有接口需要登录
 */
@RestController
@RequestMapping("/v1/superdesign")
@RequiredArgsConstructor
public class SuperDesignController {

    private final SuperDesignService superDesignService;

    /**
     * 生成3个设计方案
     *
     * POST /v1/superdesign/generate
     *
     * @param request 设计请求
     * @return 3个不同风格的设计方案（A/B/C）
     */
    @PostMapping("/generate")
    @SaCheckLogin
    public Result<List<DesignVariant>> generateVariants(@RequestBody DesignRequest request) {
        List<DesignVariant> variants = superDesignService.generateVariants(request);
        return Result.success(variants);
    }

    /**
     * 获取设计方案示例
     *
     * GET /v1/superdesign/example
     *
     * @return 示例设计请求
     */
    @GetMapping("/example")
    public Result<DesignRequest> getExample() {
        DesignRequest example = DesignRequest.builder()
                .userPrompt("构建一个图书管理系统，包含图书列表、图书详情、借阅管理功能")
                .entities(List.of(
                        DesignRequest.EntityInfo.builder()
                                .name("book")
                                .displayName("图书")
                                .primaryFields(List.of("title", "author", "isbn"))
                                .viewType("list")
                                .build(),
                        DesignRequest.EntityInfo.builder()
                                .name("borrow")
                                .displayName("借阅记录")
                                .primaryFields(List.of("bookTitle", "userName", "borrowDate"))
                                .viewType("list")
                                .build()
                ))
                .targetPlatform("android")
                .uiFramework("compose_multiplatform")
                .colorScheme("light")
                .includeAssets(true)
                .build();

        return Result.success(example);
    }

    /**
     * 生成7种风格的HTML预览（V2.0新增）
     *
     * POST /v1/superdesign/preview/generate
     *
     * @param request 7风格生成请求
     * @return 包含7个风格预览的完整响应
     */
    @PostMapping("/preview/generate")
    @SaCheckLogin
    public Result<Generate7StylesResponse> generate7StylePreviews(
            @Valid @RequestBody Generate7StylesRequest request
    ) {
        Generate7StylesResponse response = superDesignService.generate7StyleHTMLPreviews(request);
        return Result.success(response);
    }
}
