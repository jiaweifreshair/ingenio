package com.ingenio.backend.controller;

import com.ingenio.backend.common.Result;
import com.ingenio.backend.dto.multimodal.*;
import com.ingenio.backend.service.MultimodalInputService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 多模态输入控制器
 * 支持文本、语音、图像三种输入方式
 */
@Slf4j
@RestController
@RequestMapping("/v1/multimodal")
@RequiredArgsConstructor
@Tag(name = "多模态输入", description = "支持文本、语音、图像三种输入方式")
public class MultimodalInputController {

    private final MultimodalInputService multimodalInputService;

    /**
     * 文本输入
     */
    @PostMapping("/text")
    @Operation(summary = "文本输入", description = "直接输入文本需求")
    public Result<MultimodalInputResponse> textInput(@Valid @RequestBody TextInputRequest request) {
        try {
            MultimodalInputResponse response = multimodalInputService.processTextInput(request);
            return Result.success(response);
        } catch (Exception e) {
            log.error("文本输入处理失败", e);
            return Result.error(500, e.getMessage());
        }
    }

    /**
     * 语音输入
     */
    @PostMapping("/voice")
    @Operation(summary = "语音输入", description = "上传语音文件，自动转文字")
    public Result<MultimodalInputResponse> voiceInput(@Valid @RequestBody VoiceInputRequest request) {
        try {
            MultimodalInputResponse response = multimodalInputService.processVoiceInput(request);
            return Result.success(response);
        } catch (Exception e) {
            log.error("语音输入处理失败", e);
            return Result.error(500, e.getMessage());
        }
    }

    /**
     * 图像输入
     */
    @PostMapping("/image")
    @Operation(summary = "图像输入", description = "上传图片，OCR识别+UI分析")
    public Result<MultimodalInputResponse> imageInput(@Valid @RequestBody ImageInputRequest request) {
        try {
            MultimodalInputResponse response = multimodalInputService.processImageInput(request);
            return Result.success(response);
        } catch (Exception e) {
            log.error("图像输入处理失败", e);
            return Result.error(500, e.getMessage());
        }
    }

    /**
     * 查询输入记录状态
     */
    @GetMapping("/{inputId}")
    @Operation(summary = "查询输入状态", description = "查询语音/图像输入的处理状态")
    public Result<MultimodalInputResponse> getInputStatus(@PathVariable String inputId) {
        try {
            MultimodalInputResponse response = multimodalInputService.getInputById(inputId);
            return Result.success(response);
        } catch (Exception e) {
            log.error("查询输入状态失败: inputId={}", inputId, e);
            return Result.error(404, e.getMessage());
        }
    }

    /**
     * 健康检查
     */
    @GetMapping("/health")
    @Operation(summary = "健康检查", description = "检查多模态输入服务是否正常")
    public Result<String> health() {
        return Result.success("Multimodal input service is running");
    }
}
