package com.ingenio.backend.service;

import com.ingenio.backend.ai.JeecgBootMultiModalClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 语音转文字服务
 * 通过JeecgBoot网关调用七牛云多模态AI服务
 *
 * 架构说明：Ingenio -> JeecgBoot -> 七牛云AI (Whisper模型)
 *
 * 功能：
 * - 语音识别（ASR）：将音频文件转换为文字
 * - 支持多种音频格式（MP3、WAV、FLAC等）
 * - 支持多语言识别（中文、英文等）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VoiceTranscriptionService {

    private final JeecgBootMultiModalClient jeecgBootClient;

    /**
     * 语音转文字（从URL）
     *
     * 通过JeecgBoot网关调用七牛云ASR API进行语音识别。
     * 不提供降级方案，JeecgBoot服务不可用时直接抛出异常。
     *
     * @param audioUrl MinIO中的音频文件URL
     * @return 转录文本
     * @throws RuntimeException 如果JeecgBoot服务不可用或识别失败
     */
    public String transcribe(String audioUrl) {
        log.info("开始语音转文字: audioUrl={}", audioUrl);

        try {
            // 调用JeecgBoot ASR API进行语音识别
            JeecgBootMultiModalClient.SpeechRecognitionResult result =
                    jeecgBootClient.transcribeAudio(audioUrl);

            if (!result.isSuccess()) {
                throw new RuntimeException("JeecgBoot ASR API调用失败: " + result.getErrorMessage());
            }

            String transcript = result.getText();

            log.info("语音转文字完成: audioUrl={}, transcriptLength={}, language={}, durationMs={}",
                    audioUrl,
                    transcript != null ? transcript.length() : 0,
                    result.getLanguage(),
                    result.getDurationMs());

            return transcript;

        } catch (Exception e) {
            log.error("语音转文字失败: audioUrl={}", audioUrl, e);
            throw new RuntimeException("语音转文字失败: " + e.getMessage(), e);
        }
    }

    /**
     * 语音转文字（带详细结果）
     *
     * 返回包含语言信息和耗时的完整识别结果
     *
     * @param audioUrl MinIO中的音频文件URL
     * @return 语音识别结果（包含文本、语言、耗时等信息）
     * @throws RuntimeException 如果识别失败
     */
    public TranscriptionResult transcribeWithDetails(String audioUrl) {
        log.info("开始语音转文字（详细模式）: audioUrl={}", audioUrl);

        try {
            JeecgBootMultiModalClient.SpeechRecognitionResult result =
                    jeecgBootClient.transcribeAudio(audioUrl);

            if (!result.isSuccess()) {
                throw new RuntimeException("JeecgBoot ASR API调用失败: " + result.getErrorMessage());
            }

            TranscriptionResult transcriptionResult = new TranscriptionResult();
            transcriptionResult.setText(result.getText());
            transcriptionResult.setLanguage(result.getLanguage());
            transcriptionResult.setDurationMs(result.getDurationMs());
            transcriptionResult.setSuccess(true);

            log.info("语音转文字完成: audioUrl={}, result={}", audioUrl, transcriptionResult);

            return transcriptionResult;

        } catch (Exception e) {
            log.error("语音转文字失败: audioUrl={}", audioUrl, e);

            TranscriptionResult errorResult = new TranscriptionResult();
            errorResult.setSuccess(false);
            errorResult.setErrorMessage(e.getMessage());

            return errorResult;
        }
    }

    /**
     * 检查服务是否可用
     *
     * 检查JeecgBoot多模态服务是否可用
     *
     * @return true如果服务可用
     */
    public boolean isConfigured() {
        return jeecgBootClient.isAvailable();
    }

    /**
     * 语音识别结果
     */
    public static class TranscriptionResult {
        private String text;           // 识别的文本
        private String language;       // 识别的语言
        private long durationMs;       // 处理耗时（毫秒）
        private boolean success;       // 是否成功
        private String errorMessage;   // 错误信息（失败时）

        // Getters and Setters
        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }

        public String getLanguage() {
            return language;
        }

        public void setLanguage(String language) {
            this.language = language;
        }

        public long getDurationMs() {
            return durationMs;
        }

        public void setDurationMs(long durationMs) {
            this.durationMs = durationMs;
        }

        public boolean isSuccess() {
            return success;
        }

        public void setSuccess(boolean success) {
            this.success = success;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public void setErrorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
        }

        @Override
        public String toString() {
            return "TranscriptionResult{" +
                    "text='" + (text != null ? text.substring(0, Math.min(text.length(), 50)) + "..." : "null") + '\'' +
                    ", language='" + language + '\'' +
                    ", durationMs=" + durationMs +
                    ", success=" + success +
                    '}';
        }
    }
}
