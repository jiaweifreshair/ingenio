package com.ingenio.backend.service;

import java.time.Instant;
import com.ingenio.backend.entity.MultimodalInputEntity;
import com.ingenio.backend.mapper.MultimodalInputMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 多模态输入异步处理服务
 * 负责异步处理语音转文字和图像分析任务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MultimodalProcessingService {

    private final MultimodalInputMapper multimodalInputMapper;
    private final VoiceTranscriptionService voiceTranscriptionService;
    private final ImageAnalysisService imageAnalysisService;

    /**
     * 异步处理语音输入（转文字）
     */
    @Async
    @Transactional(rollbackFor = Exception.class)
    public void processVoiceAsync(String inputId, String audioUrl) {
        log.info("开始异步处理语音输入: inputId={}, audioUrl={}", inputId, audioUrl);

        MultimodalInputEntity entity = multimodalInputMapper.selectById(UUID.fromString(inputId));
        if (entity == null) {
            log.error("输入记录不存在: inputId={}", inputId);
            return;
        }

        try {
            // 1. 更新状态为processing
            entity.setProcessingStatus("processing");
            entity.setUpdatedAt(Instant.now());
            multimodalInputMapper.updateById(entity);

            // 2. 调用语音转文字服务
            String transcript = voiceTranscriptionService.transcribe(audioUrl);

            // 3. 更新状态为completed并保存转录结果
            entity.setTranscript(transcript);
            entity.setProcessingStatus("completed");
            entity.setUpdatedAt(Instant.now());
            multimodalInputMapper.updateById(entity);

            log.info("语音输入处理完成: inputId={}, transcriptLength={}", 
                    inputId, transcript.length());

        } catch (Exception e) {
            log.error("语音输入处理失败: inputId={}", inputId, e);

            // 更新状态为failed并保存错误信息
            entity.setProcessingStatus("failed");
            entity.setErrorMessage(e.getMessage());
            entity.setUpdatedAt(Instant.now());
            multimodalInputMapper.updateById(entity);
        }
    }

    /**
     * 异步处理图像输入（OCR+Vision分析）
     */
    @Async
    @Transactional(rollbackFor = Exception.class)
    public void processImageAsync(String inputId, String imageUrl) {
        log.info("开始异步处理图像输入: inputId={}, imageUrl={}", inputId, imageUrl);

        MultimodalInputEntity entity = multimodalInputMapper.selectById(UUID.fromString(inputId));
        if (entity == null) {
            log.error("输入记录不存在: inputId={}", inputId);
            return;
        }

        try {
            // 1. 更新状态为processing
            entity.setProcessingStatus("processing");
            entity.setUpdatedAt(Instant.now());
            multimodalInputMapper.updateById(entity);

            // 2. 调用图像分析服务（QianwenVL）
            ImageAnalysisService.ImageAnalysisResult result = imageAnalysisService.analyze(imageUrl);

            // 3. 更新状态为completed并保存分析结果
            entity.setTranscript(result.getOcrText()); // OCR识别的文字存入transcript
            entity.setAnalysisResult(result.toMap());  // 完整分析结果存入analysis_result（Map格式）
            entity.setProcessingStatus("completed");
            entity.setUpdatedAt(Instant.now());
            multimodalInputMapper.updateById(entity);

            log.info("图像输入处理完成: inputId={}, ocrTextLength={}, uiElementsCount={}",
                    inputId,
                    result.getOcrText() != null ? result.getOcrText().length() : 0,
                    result.getUiElements() != null ? result.getUiElements().size() : 0);

        } catch (Exception e) {
            log.error("图像输入处理失败: inputId={}", inputId, e);

            // 更新状态为failed并保存错误信息
            entity.setProcessingStatus("failed");
            entity.setErrorMessage(e.getMessage());
            entity.setUpdatedAt(Instant.now());
            multimodalInputMapper.updateById(entity);
        }
    }
}
