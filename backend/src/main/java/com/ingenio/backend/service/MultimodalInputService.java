package com.ingenio.backend.service;

import java.time.Instant;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ingenio.backend.dto.multimodal.*;
import com.ingenio.backend.entity.MultimodalInputEntity;
import com.ingenio.backend.mapper.MultimodalInputMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 多模态输入服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MultimodalInputService {

    private final MultimodalInputMapper multimodalInputMapper;
    private final MultimodalProcessingService processingService;
    private final ObjectMapper objectMapper;

    /**
     * 处理文本输入
     */
    @Transactional(rollbackFor = Exception.class)
    public MultimodalInputResponse processTextInput(TextInputRequest request) {
        log.info("处理文本输入: userId={}, textLength={}", 
                request.getUserId(), request.getText().length());

        // 1. 创建输入记录
        MultimodalInputEntity entity = new MultimodalInputEntity();
        entity.setId(UUID.randomUUID());
        entity.setTenantId(request.getTenantId() != null ? UUID.fromString(request.getTenantId()) : null);
        entity.setUserId(request.getUserId() != null ? UUID.fromString(request.getUserId()) : null);
        entity.setInputType("text");
        entity.setFileUrl(null); // 文本输入无文件
        entity.setTranscript(request.getText()); // 直接使用文本
        entity.setProcessingStatus("completed"); // 文本输入立即完成
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());

        // 2. 保存到数据库
        int inserted = multimodalInputMapper.insert(entity);
        if (inserted == 0) {
            throw new RuntimeException("保存输入记录失败");
        }

        log.info("文本输入处理完成: inputId={}", entity.getId());

        // 3. 构造响应
        return buildResponse(entity);
    }

    /**
     * 处理语音输入（异步）
     */
    @Transactional(rollbackFor = Exception.class)
    public MultimodalInputResponse processVoiceInput(VoiceInputRequest request) {
        log.info("处理语音输入: userId={}, audioUrl={}", 
                request.getUserId(), request.getAudioUrl());

        // 1. 创建输入记录（状态为pending）
        MultimodalInputEntity entity = new MultimodalInputEntity();
        entity.setId(UUID.randomUUID());
        entity.setTenantId(request.getTenantId() != null ? UUID.fromString(request.getTenantId()) : null);
        entity.setUserId(request.getUserId() != null ? UUID.fromString(request.getUserId()) : null);
        entity.setInputType("voice");
        entity.setFileUrl(request.getAudioUrl());
        entity.setProcessingStatus("pending"); // 待处理
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());

        // 2. 保存到数据库
        multimodalInputMapper.insert(entity);

        log.info("语音输入记录已创建: inputId={}, 状态=pending", entity.getId());

        // 3. 触发异步处理
        processingService.processVoiceAsync(entity.getId().toString(), request.getAudioUrl());

        // 4. 返回响应（实际转录在后续异步处理）
        return buildResponse(entity);
    }

    /**
     * 处理图像输入（异步）
     */
    @Transactional(rollbackFor = Exception.class)
    public MultimodalInputResponse processImageInput(ImageInputRequest request) {
        log.info("处理图像输入: userId={}, imageUrl={}", 
                request.getUserId(), request.getImageUrl());

        // 1. 创建输入记录（状态为pending）
        MultimodalInputEntity entity = new MultimodalInputEntity();
        entity.setId(UUID.randomUUID());
        entity.setTenantId(request.getTenantId() != null ? UUID.fromString(request.getTenantId()) : null);
        entity.setUserId(request.getUserId() != null ? UUID.fromString(request.getUserId()) : null);
        entity.setInputType("image");
        entity.setFileUrl(request.getImageUrl());
        entity.setProcessingStatus("pending"); // 待处理
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());

        // 2. 保存到数据库
        multimodalInputMapper.insert(entity);

        log.info("图像输入记录已创建: inputId={}, 状态=pending", entity.getId());

        // 3. 触发异步处理
        processingService.processImageAsync(entity.getId().toString(), request.getImageUrl());

        // 4. 返回响应（实际分析在后续异步处理）
        return buildResponse(entity);
    }

    /**
     * 查询输入记录
     */
    public MultimodalInputResponse getInputById(String inputId) {
        MultimodalInputEntity entity = multimodalInputMapper.selectById(UUID.fromString(inputId));
        if (entity == null) {
            throw new RuntimeException("输入记录不存在");
        }
        return buildResponse(entity);
    }

    /**
     * 构造响应DTO
     */
    private MultimodalInputResponse buildResponse(MultimodalInputEntity entity) {
        return MultimodalInputResponse.builder()
                .inputId(entity.getId().toString())
                .type(entity.getInputType())
                .content(entity.getFileUrl() != null ? entity.getFileUrl() : entity.getTranscript())
                .transcript(entity.getTranscript())
                .analysisResult(entity.getAnalysisResult())
                .status(entity.getProcessingStatus())
                .errorMessage(entity.getErrorMessage())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
