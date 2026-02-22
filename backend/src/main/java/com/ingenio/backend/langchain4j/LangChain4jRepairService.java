package com.ingenio.backend.langchain4j;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

/**
 * LangChain4j自动修复服务
 *
 * 功能：使用LangChain4j框架实现代码错误的自动检测和修复
 * 支持的错误类型：
 * - 前端语法错误（Babel/Vite）
 * - Java编译错误
 * - TypeScript类型错误
 *
 * @author Ingenio Team
 * @since 2.0.0
 */
@Slf4j
@Service
public class LangChain4jRepairService {

    @Value("${langchain4j.base-url:http://127.0.0.1:8045/v1}")
    private String baseUrl;

    @Value("${langchain4j.api-key:}")
    private String apiKey;

    @Value("${langchain4j.model:claude-opus-4-5-thinking}")
    private String model;

    @Autowired
    private FrontendErrorParser frontendErrorParser;

    /**
     * 自动修复前端代码错误
     *
     * @param errorOutput 错误输出
     * @param fileContent 文件内容
     * @return 修复后的文件内容，如果无法修复则返回null
     */
    public String autoRepairFrontendError(String errorOutput, String fileContent) {
        // 1. 解析错误信息
        FrontendErrorParser.ParsedError parsedError = frontendErrorParser.parse(errorOutput);
        if (parsedError == null) {
            log.warn("无法解析错误信息，跳过自动修复");
            return null;
        }

        log.info("开始自动修复: file={}, line={}, error={}",
                parsedError.getFilePath(), parsedError.getLine(), parsedError.getMessage());

        // 2. 构建修复提示词
        String systemPrompt = buildSystemPrompt();
        String userPrompt = buildUserPrompt(parsedError, fileContent);

        // 3. 调用AI生成修复建议
        try {
            ChatLanguageModel chatModel = createChatModel();

            AiMessage response = chatModel.generate(
                    List.of(
                            SystemMessage.from(systemPrompt),
                            UserMessage.from(userPrompt)
                    )
            ).content();

            String fixedContent = extractFixedCode(response.text());

            if (fixedContent != null && !fixedContent.equals(fileContent)) {
                log.info("自动修复成功: file={}", parsedError.getFilePath());
                return fixedContent;
            } else {
                log.warn("AI未能生成有效的修复代码");
                return null;
            }

        } catch (Exception e) {
            log.error("自动修复失败", e);
            return null;
        }
    }

    /**
     * 创建ChatLanguageModel
     */
    private ChatLanguageModel createChatModel() {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("langchain4j.api-key 未配置，请通过环境变量或配置文件注入");
        }

        return OpenAiChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(model)
                .temperature(0.1)
                .timeout(Duration.ofSeconds(30))
                .build();
    }

    /**
     * 构建系统提示词
     */
    private String buildSystemPrompt() {
        return """
                你是一个专业的代码修复助手。你的任务是修复代码中的语法错误。

                要求：
                1. 只修复错误，不要添加额外功能
                2. 保持代码风格一致
                3. 返回完整的修复后的代码
                4. 使用```javascript或```jsx包裹代码
                5. 不要添加任何解释，只返回代码
                """;
    }

    /**
     * 构建用户提示词
     */
    private String buildUserPrompt(FrontendErrorParser.ParsedError error, String fileContent) {
        return String.format("""
                请修复以下代码中的错误：

                错误类型：%s
                错误位置：第%d行，第%d列
                错误信息：%s

                原始代码：
                ```javascript
                %s
                ```

                请返回修复后的完整代码。
                """,
                error.getErrorType(),
                error.getLine(),
                error.getColumn(),
                error.getMessage(),
                fileContent
        );
    }

    /**
     * 从AI响应中提取修复后的代码
     */
    private String extractFixedCode(String aiResponse) {
        if (aiResponse == null || aiResponse.isBlank()) {
            return null;
        }

        // 提取代码块
        String codeBlockPattern = "```(?:javascript|jsx|typescript|tsx)?\\s*\\n([\\s\\S]*?)\\n```";
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(codeBlockPattern);
        java.util.regex.Matcher matcher = pattern.matcher(aiResponse);

        if (matcher.find()) {
            return matcher.group(1).trim();
        }

        // 如果没有代码块，返回整个响应
        return aiResponse.trim();
    }
}
