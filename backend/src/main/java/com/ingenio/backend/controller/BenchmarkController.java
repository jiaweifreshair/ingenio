package com.ingenio.backend.controller;

import com.ingenio.backend.common.Result;
import com.ingenio.backend.service.BenchmarkService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 基准页面（Benchmarks）API
 *
 * 目标：
 * - 让前端能够通过后端接口获取“挑战赛标杆 HTML”的列表与内容；
 * - 作为产品最小验收基准：前端页面能力、对照链路、E2E 测试均可复用该接口。
 */
@RestController
@RequestMapping("/v1/benchmarks")
@RequiredArgsConstructor
@Tag(name = "Benchmarks API", description = "挑战赛标杆页面（HTML）读取与展示")
public class BenchmarkController {

    private final BenchmarkService benchmarkService;

    @GetMapping
    @Operation(summary = "列出可用基准页面")
    public Result<List<BenchmarkService.BenchmarkSummary>> list() {
        return Result.success(benchmarkService.listBenchmarks());
    }

    @GetMapping(value = "/{id}", produces = MediaType.TEXT_HTML_VALUE)
    @Operation(summary = "获取基准页面（text/html，可直接在浏览器渲染）")
    public ResponseEntity<String> html(@PathVariable String id) {
        String html = benchmarkService.getBenchmarkHtml(id);
        return ResponseEntity.ok()
                .contentType(new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8))
                .body(html);
    }

    @GetMapping(value = "/{id}/raw", produces = MediaType.TEXT_PLAIN_VALUE)
    @Operation(summary = "获取基准页面原文（text/plain，便于前端做代码对照）")
    public ResponseEntity<String> raw(@PathVariable String id) {
        String html = benchmarkService.getBenchmarkHtml(id);
        return ResponseEntity.ok()
                .contentType(new MediaType(MediaType.TEXT_PLAIN, StandardCharsets.UTF_8))
                .body(html);
    }
}

