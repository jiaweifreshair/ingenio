package com.ingenio.backend.service;

import com.ingenio.backend.ai.AIProvider;
import com.ingenio.backend.ai.AIProviderFactory;
import com.ingenio.backend.common.exception.BusinessException;
import com.ingenio.backend.common.exception.ErrorCode;
import com.ingenio.backend.dto.DesignRequest;
import com.ingenio.backend.dto.DesignVariant;
import com.ingenio.backend.dto.request.Generate7StylesRequest;
import com.ingenio.backend.dto.response.Generate7StylesResponse;
import com.ingenio.backend.dto.response.StylePreviewResponse;
import com.ingenio.backend.enums.DesignStyle;
import com.ingenio.backend.prompt.LayeredPromptBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * SuperDesign AI多方案生成服务（KuiklyUI集成版）
 *
 * 核心功能：
 * 1. 并行生成7个不同风格的KuiklyUI设计方案（包含4种创新风格）
 * 2. 支持多AI提供商（七牛云Qiniu、阿里云DashScope）生成KuiklyUI DSL代码
 * 3. 自动提取色彩主题、布局类型、设计特点、交互创新点
 * 4. 智能降级策略（优先使用Qiniu，失败时降级到DashScope）
 * 5. 增强创意性和交互新颖性要求，每个方案都具备独特的视觉和交互特色
 * 6. KuiklyUI代码验证机制（确保生成代码符合框架规范）
 *
 * 七种设计风格：
 * 【传统风格】
 * - 方案A：现代极简风格（Material Design 3，大留白，卡片式，流畅动效）
 * - 方案B：活力时尚风格（渐变色彩，网格布局，圆角设计，弹性动效）
 * - 方案C：经典专业风格（信息密集，列表布局，商务配色，高效交互）
 * 【创新风格】
 * - 方案D：未来科技风格（赛博朋克，霓虹色，毛玻璃，光效动画）
 * - 方案E：沉浸式3D风格（卡片翻转，视差滚动，立体阴影，透视变换）
 * - 方案F：游戏化风格（关卡系统，徽章奖励，粒子特效，趣味反馈）
 * - 方案G：自然流动风格（有机曲线，流体动画，波浪效果，舒缓过渡）
 *
 * AI提供商配置：
 * - 优先级1：七牛云Qiniu（国内访问快，价格优惠）
 *   - 环境变量：QINIU_CLOUD_API_KEY 或 DEEPSEEK_API_KEY
 *   - 默认模型：Qwen/Qwen2.5-72B-Instruct
 * - 优先级2：阿里云DashScope（生态完整，企业级稳定）
 *   - 环境变量：DASHSCOPE_API_KEY
 *   - 默认模型：qwen-max
 *
 * @author Ingenio Team
 * @since 1.0.0
 * @see AIProviderFactory
 * @see LayeredPromptBuilder
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SuperDesignService {

    private final AIProviderFactory aiProviderFactory;
    private final StyleTemplateGenerator styleTemplateGenerator;

    /**
     * 并行生成7个设计方案（优化版：包含4种创新风格）
     * 
     * @deprecated V2.0已废弃并行生成，改为单风格极速生成。保留此方法仅为了兼容旧接口调用，实际上只生成默认风格。
     *
     * @param request 设计请求
     * @return 单个设计方案的列表
     */
    @Deprecated
    public List<DesignVariant> generateVariants(DesignRequest request) {
        log.info("生成设计方案（兼容模式）: taskId={}", request.getTaskId());

        // 默认生成现代极简风格
        StylePrompt defaultStyle = new StylePrompt(
                "A",
                "现代极简",
                "现代极简风格，大留白，卡片式布局，Material Design 3，清爽配色，圆角矩形，微妙阴影，流畅过渡动画",
                Arrays.asList("现代", "极简", "卡片式", "留白", "清爽", "流畅动效"),
                "#6200EE", "#03DAC6", "#FFFFFF", "#000000", "card"
        );

        try {
            DesignVariant variant = generateSingleVariant(request, defaultStyle);
            return Collections.singletonList(variant);
        } catch (Exception e) {
            log.error("生成设计方案失败", e);
            return Collections.emptyList();
        }
    }

    /**
     * 生成单个设计方案
     *
     * 使用AI Provider抽象层生成设计代码，支持多提供商切换和智能降级。
     *
     * @param request 设计请求
     * @param stylePrompt 风格提示词配置
     * @return 生成的设计方案
     * @throws AIProvider.AIException 当AI生成失败时抛出
     */
    private DesignVariant generateSingleVariant(DesignRequest request, StylePrompt stylePrompt) {
        log.info("生成设计方案{}: style={}", stylePrompt.variantId, stylePrompt.style);

        // 获取AI提供商（自动选择可用的提供商）
        AIProvider provider = aiProviderFactory.getProvider();
        log.debug("使用AI提供商: {} ({})",
                provider.getProviderDisplayName(),
                provider.getProviderName());

        // 构建完整的提示词
        String fullPrompt = buildPrompt(request, stylePrompt);

        // 调用AI Provider生成代码
        AIProvider.AIRequest aiRequest = AIProvider.AIRequest.builder()
                .temperature(0.7)
                .maxTokens(4096)
                .build();

        AIProvider.AIResponse aiResponse = provider.generate(fullPrompt, aiRequest);

        log.debug("AI生成完成: provider={}, model={}, tokens={}, duration={}ms",
                aiResponse.provider(),
                aiResponse.model(),
                aiResponse.totalTokens(),
                aiResponse.durationMs());

        // 提取生成的代码
        String code = extractCode(aiResponse.content());

        // 验证生成的代码是否符合KuiklyUI格式
        validateKuiklyUICode(code);

        // 构建色彩主题
        DesignVariant.ColorTheme colorTheme = DesignVariant.ColorTheme.builder()
                .primaryColor(stylePrompt.primaryColor)
                .secondaryColor(stylePrompt.secondaryColor)
                .backgroundColor(stylePrompt.backgroundColor)
                .textColor(stylePrompt.textColor)
                .accentColor(stylePrompt.accentColor())
                .darkMode(false)
                .build();

        // 生成KuiklyUI标准路径
        String taskIdCapitalized = capitalizeFirst(request.getTaskId().toString());
        String codePath = String.format("core/src/commonMain/kotlin/pages/%s_%sPage.kt",
                taskIdCapitalized, stylePrompt.variantId);

        return DesignVariant.builder()
                .variantId(stylePrompt.variantId)
                .style(stylePrompt.style)
                .styleKeywords(stylePrompt.keywords)
                .code(code)
                .codePath(codePath)  // ✅ 修改为KuiklyUI路径
                .preview("https://placeholder.superdesign.dev/" + stylePrompt.variantId + ".png")
                .colorTheme(colorTheme)
                .layoutType(stylePrompt.layoutType)
                .componentLibrary("kuiklyui")  // ✅ 修改为KuiklyUI组件库
                .features(stylePrompt.keywords)
                .rawResponse(aiResponse.rawResponse())
                .build();
    }

    /**
     * 验证生成的代码是否符合KuiklyUI格式
     *
     * 检查必需的KuiklyUI元素和禁止的Compose元素
     *
     * @param code 生成的代码
     * @throws BusinessException 当代码验证失败时抛出
     */
    private void validateKuiklyUICode(String code) {
        List<String> errors = new ArrayList<>();

        // 1. 检查必需的import
        if (!code.contains("import com.kuikly.core.Pager")) {
            errors.add("缺少Pager导入");
        }
        if (!code.contains("import com.kuikly.core.annotations.Page")) {
            errors.add("缺少@Page注解导入");
        }

        // 2. 检查@Page注解
        if (!code.contains("@Page(")) {
            errors.add("缺少@Page注解");
        }

        // 3. 检查Pager继承
        if (!code.contains(": Pager()")) {
            errors.add("未继承Pager基类");
        }

        // 4. 检查body方法
        if (!code.contains("override fun body(): ViewBuilder")) {
            errors.add("未实现body()方法");
        }

        // 5. 检查是否使用了禁止的Compose组件
        if (code.contains("@Composable")) {
            errors.add("包含@Composable注解，应使用KuiklyUI DSL");
        }
        if (code.contains("import androidx.compose")) {
            errors.add("包含androidx.compose导入，应使用KuiklyUI组件");
        }

        if (!errors.isEmpty()) {
            String errorMessage = "KuiklyUI代码验证失败: " + String.join(", ", errors);
            log.warn(errorMessage);
            log.debug("生成的代码:\n{}", code);
            throw new BusinessException(ErrorCode.CODEGEN_FAILED, errorMessage);
        }

        log.debug("KuiklyUI代码验证通过");
    }

    /**
     * 构建AI提示词（使用分层Prompt构建器）
     *
     * 优化点：
     * 1. 使用LayeredPromptBuilder实现清晰的四层结构
     * 2. 支持更丰富的约束条件（色彩理念、布局类型等）
     * 3. 提高prompt的可维护性和可测试性
     * 4. 为多AI提供商支持打下基础
     *
     * @param request 设计请求
     * @param stylePrompt 风格提示词配置
     * @return 完整的分层prompt字符串
     */
    private String buildPrompt(DesignRequest request, StylePrompt stylePrompt) {
        // 使用LayeredPromptBuilder构建四层Prompt
        LayeredPromptBuilder builder = LayeredPromptBuilder.fromRequest(request)
                // Layer 2: 任务上下文层
                .withStyle(stylePrompt.styleDescription, stylePrompt.keywords)
                // Layer 3: 约束条件层
                .withColors(
                        stylePrompt.primaryColor,
                        stylePrompt.secondaryColor,
                        stylePrompt.backgroundColor,
                        stylePrompt.textColor,
                        stylePrompt.accentColor()
                )
                .withLayoutType(stylePrompt.layoutType)
                // 根据风格添加色彩设计理念
                .withColorPhilosophy(getColorPhilosophy(stylePrompt.variantId));

        // 验证必填字段
        builder.validate();

        // 记录prompt长度，用于性能监控
        String prompt = builder.build();
        log.debug("生成Prompt长度: {} 字符（方案{}）", prompt.length(), stylePrompt.variantId);

        return prompt;
    }

    /**
     * 根据设计方案ID获取对应的色彩设计理念
     *
     * @param variantId 设计方案ID (A/B/C/D/E/F/G)
     * @return 色彩设计理念描述
     */
    private String getColorPhilosophy(String variantId) {
        return switch (variantId) {
            case "A" -> "使用Material Design 3的色彩系统，主色调鲜明但不刺眼，整体配色传递现代、专业、可信赖的品牌形象。";
            case "B" -> "采用活力四射的渐变色彩，营造年轻化、富有创意的视觉体验，吸引年轻用户群体的注意力。";
            case "C" -> "选择低饱和度的商务配色，营造稳重、专业的氛围，适合企业级应用和严肃场景。";
            case "D" -> "使用赛博朋克霓虹色系，荧光蓝和电光红营造未来科技感，深色背景增强对比度和神秘感。";
            case "E" -> "采用紫色渐变色系，配合立体阴影和透视效果，营造沉浸式3D视觉体验。";
            case "F" -> "使用高饱和度活力色彩，橙色和红色组合带来游戏化的趣味感和激励氛围。";
            case "G" -> "选用天空蓝和森林绿等自然色系，营造舒缓放松的视觉体验，传递和谐自然的设计理念。";
            default -> "平衡色彩的美观性和功能性，确保良好的可读性和用户体验。";
        };
    }

    /**
     * 从AI响应中提取代码
     */
    private String extractCode(String aiResponse) {
        if (aiResponse == null || aiResponse.isEmpty()) {
            return "// 未生成代码";
        }

        // 提取代码块（```kotlin ... ```）
        int startIndex = aiResponse.indexOf("```kotlin");
        if (startIndex == -1) {
            startIndex = aiResponse.indexOf("```");
        }

        if (startIndex == -1) {
            // 如果没有代码块标记，返回整个响应
            return aiResponse;
        }

        int codeStart = aiResponse.indexOf("\n", startIndex) + 1;
        int codeEnd = aiResponse.indexOf("```", codeStart);

        if (codeEnd == -1) {
            return aiResponse.substring(codeStart);
        }

        return aiResponse.substring(codeStart, codeEnd).trim();
    }

    /**
     * 首字母大写
     */
    private String capitalizeFirst(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }

    /**
     * 风格提示词配置
     */
    private record StylePrompt(
            String variantId,
            String style,
            String styleDescription,
            List<String> keywords,
            String primaryColor,
            String secondaryColor,
            String backgroundColor,
            String textColor,
            String layoutType
    ) {
        // 强调色默认使用次要色
        public String accentColor() {
            return secondaryColor;
        }
    }


    /**
     * 生成7种风格的HTML预览（V2.0 SuperDesign集成版）
     * 
     * @deprecated V2.0已废弃并行生成。此方法仅为了兼容旧接口保留，实际上只生成 Modern Minimal 风格。
     *
     * @param request 7风格生成请求
     * @return 包含单风格预览的响应
     */
    @Deprecated
    public Generate7StylesResponse generate7StyleHTMLPreviews(Generate7StylesRequest request) {
        log.info("生成风格预览（兼容模式）: requirement={}", request.getUserRequirement());

        long startTime = System.currentTimeMillis();
        List<String> warnings = new ArrayList<>();

        try {
            // 提取应用信息
            String appName = extractAppNameFromRequirement(request.getUserRequirement());
            String appDescription = request.getUserRequirement();
            List<String> features = extractFeaturesFromRequirement(appDescription);

            // 仅生成默认风格
            DesignStyle defaultStyle = DesignStyle.MODERN_MINIMAL;
            
            StylePreviewResponse preview = generateSingleStyleWithAI(
                    defaultStyle,
                    appName,
                    appDescription,
                    features
            );

            return Generate7StylesResponse.builder()
                    .success(true)
                    .styles(Collections.singletonList(preview))
                    .totalGenerationTime(System.currentTimeMillis() - startTime)
                    .warnings(warnings)
                    .build();

        } catch (Exception e) {
            log.error("风格生成失败", e);
            return Generate7StylesResponse.builder()
                    .success(false)
                    .styles(new ArrayList<>())
                    .totalGenerationTime(System.currentTimeMillis() - startTime)
                    .error(e.getMessage())
                    .warnings(warnings)
                    .build();
        }
    }

    /**
     * 为OpenLovable构建特定风格的设计Prompt（V2.0单风格极速版支持）
     *
     * 该方法将SuperDesign的高质量Prompt构建逻辑暴露给PlanRoutingService，
     * 以便直接生成单一最佳风格的原型，避免并行生成7种风格导致的超时。
     *
     * @param style 目标设计风格
     * @param appName 应用名称
     * @param appDescription 应用描述
     * @param features 功能列表
     * @return 构建好的完整Prompt
     */
    public String buildDesignPromptForOpenLovable(
            DesignStyle style,
            String appName,
            String appDescription,
            List<String> features) {
        
        log.info("构建OpenLovable设计Prompt - style: {}, appName: {}", style.getDisplayName(), appName);
        return buildSuperDesignPrompt(style, appName, appDescription, features);
    }

    /**
     * 使用AI生成单个风格的HTML设计（SuperDesign风格）
     *
     * 参考SuperDesign的设计规范：
     * - 使用Flowbite作为基础库
     * - 避免使用indigo/blue色系（除非明确要求）
     * - 必须生成响应式设计
     * - 使用Google Fonts（如Inter、Roboto、Poppins等）
     * - 提取设计规范供后续代码生成使用
     *
     * @param style 设计风格
     * @param appName 应用名称
     * @param appDescription 应用描述
     * @param features 功能列表
     * @return 风格预览响应
     */
    private StylePreviewResponse generateSingleStyleWithAI(
            DesignStyle style,
            String appName,
            String appDescription,
            List<String> features) {

        long startTime = System.currentTimeMillis();
        log.info("使用AI生成{}风格设计: appName={}", style.getDisplayName(), appName);

        // 获取AI提供商
        AIProvider provider = aiProviderFactory.getProvider();
        log.debug("使用AI提供商: {} ({})",
                provider.getProviderDisplayName(), provider.getProviderName());

        // 构建SuperDesign风格的Prompt
        String prompt = buildSuperDesignPrompt(style, appName, appDescription, features);

        // 调用AI生成HTML
        AIProvider.AIRequest aiRequest = AIProvider.AIRequest.builder()
                .temperature(0.8) // 提高创意性
                .maxTokens(8192) // 足够生成完整HTML
                .build();

        AIProvider.AIResponse aiResponse = provider.generate(prompt, aiRequest);

        log.debug("AI生成完成: provider={}, model={}, tokens={}, duration={}ms",
                aiResponse.provider(),
                aiResponse.model(),
                aiResponse.totalTokens(),
                aiResponse.durationMs());

        // 提取HTML内容
        String htmlContent = extractHTMLContent(aiResponse.content());

        // 提取设计规范（从HTML中解析CSS变量）
        Map<String, Object> designSpec = extractDesignSpec(htmlContent, style);

        long generationTime = System.currentTimeMillis() - startTime;
        log.info("{}风格AI生成完成: {}ms, HTML长度={}字符",
                style.getDisplayName(), generationTime, htmlContent.length());

        return StylePreviewResponse.builder()
                .style(style.getCode())
                .htmlContent(htmlContent)
                .cssContent("") // CSS已嵌入HTML中
                .generationTime(generationTime)
                .aiGenerated(true) // ⭐ 标记为AI生成
                .designSpec(designSpec) // ⭐ 设计规范（新增）
                .build();
    }

    /**
     * 构建SuperDesign风格的Prompt
     *
     * 参考SuperDesign extension的设计规则
     */
    private String buildSuperDesignPrompt(
            DesignStyle style,
            String appName,
            String appDescription,
            List<String> features) {

        StringBuilder prompt = new StringBuilder();

        // Layer 1: 角色定位（参考SuperDesign）
        prompt.append("# Role\n");
        prompt.append("You are superdesign, a senior frontend designer.\n");
        prompt.append("Your goal is to help generate amazing design using code.\n\n");

        // Layer 2: 任务描述
        prompt.append("# Task\n");
        prompt.append("Generate a complete, self-contained HTML page for a ").append(style.getDisplayName()).append(" style app landing page.\n\n");

        // Layer 3: 应用信息
        prompt.append("# App Information\n");
        prompt.append("- App Name: ").append(appName).append("\n");
        prompt.append("- Description: ").append(appDescription).append("\n");
        prompt.append("- Key Features:\n");
        features.forEach(feature -> prompt.append("  - ").append(feature).append("\n"));
        prompt.append("\n");

        // Layer 4: 设计约束（参考SuperDesign的Styling规则）
        prompt.append("# Design Requirements\n\n");
        prompt.append("## General Rules\n");
        prompt.append("1. Use Flowbite library as base (CDN: https://cdn.jsdelivr.net/npm/flowbite@2.5.2/dist/flowbite.min.js)\n");
        prompt.append("2. MUST generate responsive designs (mobile-first approach)\n");
        prompt.append("3. Use Google Fonts (one of: Inter, Roboto, Poppins, Montserrat, Outfit, Plus Jakarta Sans, DM Sans, Geist)\n");
        prompt.append("4. Avoid indigo or blue colors UNLESS specifically required by the style\n");
        prompt.append("5. Use CSS custom properties (CSS variables) for theming\n\n");

        // Layer 5: 风格特定约束
        prompt.append("## Style-Specific Requirements: ").append(style.getDisplayName()).append("\n");
        prompt.append(getStyleSpecificInstructions(style)).append("\n\n");

        // Layer 6: 输出格式要求
        prompt.append("# Output Format\n");
        prompt.append("Generate a complete HTML page with:\n");
        prompt.append("1. DOCTYPE and proper HTML5 structure\n");
        prompt.append("2. Embedded CSS in <style> tag (with CSS variables in :root)\n");
        prompt.append("3. Complete page structure: navbar, hero, features section, CTA, footer\n");
        prompt.append("4. Responsive design with media queries\n");
        prompt.append("5. No external dependencies except Flowbite CDN and Google Fonts\n\n");

        // Layer 7: 示例CSS变量结构
        prompt.append("# CSS Variables Structure (Example)\n");
        prompt.append("```css\n");
        prompt.append(":root {\n");
        prompt.append("  /* Colors */\n");
        prompt.append("  --primary: #...;\n");
        prompt.append("  --secondary: #...;\n");
        prompt.append("  --background: #...;\n");
        prompt.append("  --text: #...;\n");
        prompt.append("  --accent: #...;\n\n");
        prompt.append("  /* Typography */\n");
        prompt.append("  --font-family: '...', sans-serif;\n");
        prompt.append("  --font-size-base: 16px;\n\n");
        prompt.append("  /* Layout */\n");
        prompt.append("  --spacing-unit: 8px;\n");
        prompt.append("  --radius-small: 4px;\n");
        prompt.append("  --radius-medium: 8px;\n");
        prompt.append("  --radius-large: 16px;\n");
        prompt.append("}\n");
        prompt.append("```\n\n");

        prompt.append("Now, generate the complete HTML page following all requirements above.\n");
        prompt.append("Output ONLY the HTML code, wrapped in ```html and ``` markers.\n");

        return prompt.toString();
    }

    /**
     * 获取风格特定的设计指令
     */
    private String getStyleSpecificInstructions(DesignStyle style) {
        return switch (style) {
            case MODERN_MINIMAL -> """
                - Large whitespace and breathing room
                - Card-based layouts with subtle shadows
                - Clean sans-serif fonts (Inter or DM Sans recommended)
                - Neutral color palette (grays, whites, one accent color)
                - Smooth transitions and micro-interactions
                - Example colors: #1a1a1a (dark), #6200EE (accent), #FFFFFF (background)
                """;
            case VIBRANT_FASHION -> """
                - Bold gradient backgrounds
                - Vibrant color combinations (purples, pinks, oranges)
                - Large rounded corners (16px+)
                - Playful animations and bouncy transitions
                - Grid/masonry layouts for content
                - Example colors: #FF6B6B, #4ECDC4, #8b5cf6 (primary gradient)
                """;
            case CLASSIC_PROFESSIONAL -> """
                - Traditional business color palette (blues, grays)
                - Formal typography (Roboto or Open Sans)
                - Structured grid layouts
                - Minimalist approach with high information density
                - Conservative shadows and borders
                - Example colors: #1e40af (primary), #475569 (text), #f8fafc (background)
                """;
            case FUTURE_TECH -> """
                - Dark background with neon accents
                - Cyber-punk aesthetics (neon blues, pinks, greens)
                - Glassmorphism effects (backdrop-filter: blur)
                - Glowing borders and shadows
                - Futuristic fonts (Geist Mono or Space Grotesk)
                - Example colors: #0A0E27 (background), #00F5FF (neon accent), #E94560 (secondary)
                """;
            case IMMERSIVE_3D -> """
                - 3D card transforms and perspective effects
                - Parallax scrolling elements
                - Deep shadows for depth
                - Hover effects with scale and rotation
                - Gradient overlays for dimension
                - Example colors: #667EEA, #764BA2 (gradient), with 3D transforms
                """;
            case GAMIFIED -> """
                - Playful, game-like UI elements
                - Progress bars and achievement badges
                - Colorful buttons with animated hover effects
                - Point/score displays
                - Confetti or particle effects (CSS animations)
                - Example colors: #FFA500 (gold), #FF1744 (red), #FFFDE7 (light)
                """;
            case NATURAL_FLOW -> """
                - Organic curves and flowing shapes
                - Nature-inspired color palette (greens, blues, earth tones)
                - Soft gradients and transitions
                - Wave patterns and fluid animations
                - Relaxing, breathable layouts
                - Example colors: #56CCF2 (sky blue), #2F80ED (ocean), #F8FDFF (background)
                """;
        };
    }

    /**
     * 从AI响应中提取HTML内容
     */
    private String extractHTMLContent(String aiResponse) {
        if (aiResponse == null || aiResponse.isEmpty()) {
            return "<!-- AI生成失败：空响应 -->";
        }

        // 提取```html ... ```代码块
        int startIndex = aiResponse.indexOf("```html");
        if (startIndex == -1) {
            startIndex = aiResponse.indexOf("```");
        }

        if (startIndex == -1) {
            // 如果没有代码块标记，检查是否直接是HTML
            if (aiResponse.trim().startsWith("<!DOCTYPE") || aiResponse.trim().startsWith("<html")) {
                return aiResponse.trim();
            }
            return "<!-- AI生成失败：未找到HTML代码块 -->";
        }

        int htmlStart = aiResponse.indexOf("\n", startIndex) + 1;
        int htmlEnd = aiResponse.indexOf("```", htmlStart);

        if (htmlEnd == -1) {
            return aiResponse.substring(htmlStart).trim();
        }

        return aiResponse.substring(htmlStart, htmlEnd).trim();
    }

    /**
     * 从HTML中提取设计规范
     *
     * 解析CSS变量和设计元素，提取为结构化的设计规范
     */
    private Map<String, Object> extractDesignSpec(String htmlContent, DesignStyle style) {
        Map<String, Object> designSpec = new HashMap<>();

        try {
            // 提取CSS变量（从<style>标签中的:root）
            Map<String, String> cssVariables = extractCSSVariables(htmlContent);

            // 构建色彩主题
            Map<String, Object> colorTheme = new HashMap<>();
            colorTheme.put("primary", cssVariables.getOrDefault("--primary", "#6200EE"));
            colorTheme.put("secondary", cssVariables.getOrDefault("--secondary", "#03DAC6"));
            colorTheme.put("background", cssVariables.getOrDefault("--background", "#FFFFFF"));
            colorTheme.put("text", cssVariables.getOrDefault("--text", "#000000"));
            colorTheme.put("accent", cssVariables.getOrDefault("--accent", cssVariables.get("--secondary")));
            designSpec.put("colorTheme", colorTheme);

            // 构建typography
            Map<String, Object> typography = new HashMap<>();
            typography.put("fontFamily", cssVariables.getOrDefault("--font-family", "Inter, sans-serif"));
            typography.put("fontSize", cssVariables.getOrDefault("--font-size-base", "16px"));
            typography.put("lineHeight", cssVariables.getOrDefault("--line-height", "1.6"));
            designSpec.put("typography", typography);

            // 构建layout
            Map<String, Object> layout = new HashMap<>();
            layout.put("type", style == DesignStyle.CLASSIC_PROFESSIONAL ? "list" : "card");
            layout.put("spacing", cssVariables.getOrDefault("--spacing-unit", "8px"));
            layout.put("borderRadius", cssVariables.getOrDefault("--radius-medium", "12px"));
            designSpec.put("layout", layout);

            // 组件列表（根据HTML结构推断）
            List<String> components = new ArrayList<>();
            if (htmlContent.contains("class=\"navbar\"") || htmlContent.contains("<nav")) {
                components.add("Navbar");
            }
            if (htmlContent.contains("class=\"hero\"")) {
                components.add("Hero");
            }
            if (htmlContent.contains("class=\"features\"") || htmlContent.contains("class=\"feature")) {
                components.add("FeatureGrid");
            }
            if (htmlContent.contains("class=\"cta\"")) {
                components.add("CTA");
            }
            if (htmlContent.contains("<footer")) {
                components.add("Footer");
            }
            designSpec.put("components", components);

            log.debug("提取设计规范成功: colorTheme={}, typography={}, components={}",
                    colorTheme.get("primary"), typography.get("fontFamily"), components.size());

        } catch (Exception e) {
            log.warn("提取设计规范失败，使用默认值: {}", e.getMessage());
            // 返回默认设计规范
            designSpec.put("colorTheme", Map.of(
                    "primary", "#6200EE",
                    "secondary", "#03DAC6",
                    "background", "#FFFFFF",
                    "text", "#000000"
            ));
            designSpec.put("typography", Map.of(
                    "fontFamily", "Inter, sans-serif",
                    "fontSize", "16px"
            ));
            designSpec.put("layout", Map.of(
                    "type", "card",
                    "spacing", "8px",
                    "borderRadius", "12px"
            ));
            designSpec.put("components", List.of("Hero", "Features", "CTA"));
        }

        return designSpec;
    }

    /**
     * 从HTML中提取CSS变量
     */
    private Map<String, String> extractCSSVariables(String htmlContent) {
        Map<String, String> variables = new HashMap<>();

        try {
            // 查找:root {...}块
            int rootStart = htmlContent.indexOf(":root");
            if (rootStart == -1) {
                return variables;
            }

            int braceStart = htmlContent.indexOf("{", rootStart);
            int braceEnd = htmlContent.indexOf("}", braceStart);

            if (braceStart == -1 || braceEnd == -1) {
                return variables;
            }

            String rootContent = htmlContent.substring(braceStart + 1, braceEnd);

            // 解析CSS变量（--variable: value;）
            String[] lines = rootContent.split("\n");
            for (String line : lines) {
                line = line.trim();
                if (line.startsWith("--") && line.contains(":")) {
                    String[] parts = line.split(":", 2);
                    if (parts.length == 2) {
                        String varName = parts[0].trim();
                        String varValue = parts[1].replaceAll(";", "").trim();
                        variables.put(varName, varValue);
                    }
                }
            }

        } catch (Exception e) {
            log.warn("解析CSS变量失败: {}", e.getMessage());
        }

        return variables;
    }

    /**
     * 从用户需求中提取应用名称
     * 简单实现：取第一句话的主语或使用默认名称
     */
    private String extractAppNameFromRequirement(String requirement) {
        if (requirement == null || requirement.isEmpty()) {
            return "我的应用";
        }

        // 尝试提取"创建XXX"、"开发XXX"、"构建XXX"等模式
        String[] patterns = {"创建", "开发", "构建", "设计", "实现"};
        for (String pattern : patterns) {
            int index = requirement.indexOf(pattern);
            if (index != -1) {
                String afterPattern = requirement.substring(index + pattern.length()).trim();
                String[] words = afterPattern.split("[，。、,\\s]");
                if (words.length > 0 && !words[0].isEmpty() && words[0].length() < 20) {
                    return words[0];
                }
            }
        }

        // 默认名称
        return "我的应用";
    }

    /**
     * 从用户需求中提取功能列表
     * 简单实现：提取包含"管理"、"查询"、"展示"等动词的短语
     */
    private List<String> extractFeaturesFromRequirement(String requirement) {
        if (requirement == null || requirement.isEmpty()) {
            return List.of("数据管理", "信息展示", "用户交互");
        }

        List<String> features = new ArrayList<>();

        // 提取常见功能关键词
        String[] keywords = {"管理", "查询", "展示", "编辑", "删除", "创建", "浏览",
                "搜索", "筛选", "统计", "分析", "导出", "导入", "预订", "支付"};

        for (String keyword : keywords) {
            if (requirement.contains(keyword) && features.size() < 6) {
                // 提取包含关键词的短语
                int index = requirement.indexOf(keyword);
                int start = Math.max(0, index - 3);
                int end = Math.min(requirement.length(), index + keyword.length() + 3);
                String phrase = requirement.substring(start, end).trim();

                // 清理短语
                phrase = phrase.replaceAll("[，。、,\\s]+$", "")
                        .replaceAll("^[，。、,\\s]+", "");

                if (phrase.length() >= 2 && phrase.length() <= 10) {
                    features.add(phrase);
                }
            }
        }

        // 如果提取不到功能，使用默认功能
        if (features.isEmpty()) {
            features.add("数据管理");
            features.add("信息展示");
            features.add("用户交互");
        }

        // 限制最多6个功能
        return features.size() > 6 ? features.subList(0, 6) : features;
    }
}
