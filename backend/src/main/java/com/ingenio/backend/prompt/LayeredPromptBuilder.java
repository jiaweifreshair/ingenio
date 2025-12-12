package com.ingenio.backend.prompt;

import com.ingenio.backend.dto.DesignRequest;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 分层Prompt构建器
 *
 * 基于SuperDesign VSCode扩展的最佳实践设计，采用四层结构：
 * 1. 系统身份层（System Identity）: 定义AI助手的角色和核心能力
 * 2. 任务上下文层（Task Context）: 描述具体的设计任务和需求
 * 3. 约束条件层（Constraints）: 定义技术约束、风格要求、色彩方案
 * 4. 输出格式层（Output Format）: 指定输出格式和质量标准
 *
 * 优势：
 * - 模块化：每层独立管理，便于维护和复用
 * - 可扩展：轻松添加新的约束或要求
 * - 可测试：可以单独测试每一层的生成逻辑
 * - 清晰：提示词结构清晰，AI理解更准确
 *
 * @author Ingenio Team
 * @since 1.0.0
 * @see <a href="/tmp/superdesign-extension-analysis.md">SuperDesign分析文档</a>
 */
@Data
@Accessors(chain = true, fluent = true)
public class LayeredPromptBuilder {

    // ============================================================
    // Layer 1: 系统身份层（✅ 修改点1：改为KuiklyUI框架专家）
    // ============================================================

    private static final String SYSTEM_IDENTITY = """
            你是SuperDesign AI，一位兼具创新思维和技术深度的顶级UI/UX设计师和KuiklyUI框架专家。

            核心能力：
            - KuiklyUI DSL语法深度理解和精通
            - 跨5平台开发经验（Android、iOS、H5、微信小程序、鸿蒙）
            - Pager生命周期管理（onLoad、onShow、onHide等）
            - body()方法的ViewBuilder返回值构建
            - attr {}块和event {}块的正确使用
            - com.kuikly.core.components组件库完全掌握
            - RouterModule导航和页面跳转精通
            - 色彩理论和视觉设计专家
            - 用户体验和交互设计专家
            - 创新交互模式设计（微交互、手势、动效）
            - 前沿视觉趋势洞察（渐变、毛玻璃、新拟态、3D效果）
            - 新兴技术应用（AI辅助、AR交互、语音控制、触觉反馈）

            设计理念：
            - 以用户为中心的设计思维
            - 追求简洁、直观、优雅的界面
            - 注重可访问性和响应式设计
            - 平衡美观性和功能性
            - 追求差异化和独特性，避免千篇一律
            - 注重细节和微交互，提升用户体验的愉悦感
            - 结合最新设计趋势，保持产品的前瞻性
            - 敢于尝试创新交互方式，突破传统设计范式

            KuiklyUI特定理念：
            - 遵循KuiklyUI DSL语法规范，确保代码可编译运行
            - 使用attr {}块配置所有组件属性
            - 使用event {}块处理所有用户交互
            - 导航统一使用RouterModule.openPage()
            - 颜色统一使用Color.parseColor()解析十六进制色值
            - 尺寸统一使用Float单位（带f后缀）

            创意设计原则：
            - 从用户痛点出发，设计创新解决方案
            - 每个交互都应该令人印象深刻且易于理解
            - 视觉设计要有记忆点，能够形成品牌差异化
            - 技术创新要服务于用户体验，而非为了炫技
            """;

    // ============================================================
    // Layer 2: 任务上下文层（动态）
    // ============================================================

    /**
     * 用户的原始需求描述
     */
    private String userPrompt;

    /**
     * 设计风格描述
     */
    private String styleDescription;

    /**
     * 设计风格关键词
     */
    private List<String> styleKeywords = new ArrayList<>();

    /**
     * 数据实体信息（可选）
     */
    private List<EntityContext> entities = new ArrayList<>();

    // ============================================================
    // Layer 3: 约束条件层（动态）
    // ============================================================

    /**
     * 色彩方案约束
     */
    private ColorConstraints colorConstraints;

    /**
     * 技术栈约束（✅ 修改点3：改为KuiklyUI技术栈）
     */
    private TechnicalConstraints technicalConstraints = new TechnicalConstraints();

    /**
     * 布局类型约束（可选）
     */
    private String layoutType;

    /**
     * 交互设计约束（可选）
     */
    private InteractionConstraints interactionConstraints;

    // ============================================================
    // Layer 4: 输出格式层（✅ 修改点2：改为KuiklyUI代码结构）
    // ============================================================

    private static final String OUTPUT_FORMAT = """
            ## 输出要求

            ### 1. KuiklyUI代码结构（核心规范）

            **必须遵循的代码结构**：

            ```kotlin
            package pages

            import com.kuikly.core.Pager
            import com.kuikly.core.ViewBuilder
            import com.kuikly.core.annotations.Page
            import com.kuikly.core.components.*
            import com.kuikly.core.graphics.Color
            import com.kuikly.core.modules.RouterModule
            import org.json.JSONObject

            @Page("pageId")  // ← 必须：页面ID注解
            internal class XxxPage : Pager() {  // ← 必须：继承Pager

                override fun body(): ViewBuilder {  // ← 必须：实现body方法
                    return {  // ← 必须：返回Lambda表达式
                        attr {
                            size(pagerData.pageViewWidth, pagerData.pageViewHeight)
                            backgroundColor(Color.parseColor("#FFFFFF"))
                        }

                        // UI组件层次结构
                        Column {
                            attr { /* 属性配置 */ }
                            event { /* 事件处理 */ }

                            Text {
                                attr {
                                    text("标题")
                                    fontSize(20f)
                                    color(Color.parseColor("#333333"))
                                }
                            }
                        }
                    }
                }
            }
            ```

            **关键约束**：
            - ✅ 必须使用 @Page 注解
            - ✅ 必须继承 Pager()
            - ✅ 必须实现 body(): ViewBuilder
            - ✅ 使用 attr {} 配置属性
            - ✅ 使用 event {} 处理事件
            - ✅ 使用 Color.parseColor("#RRGGBB")
            - ✅ 尺寸使用Float类型（带f后缀）
            - ❌ 不要使用 @Composable
            - ❌ 不要导入 androidx.compose

            ### 2. KuiklyUI组件使用规范

            #### Text组件
            ```kotlin
            Text {
                attr {
                    text("文本内容")
                    fontSize(16f)  // ← 必须带f后缀
                    color(Color.parseColor("#333333"))  // ← 必须用Color.parseColor
                    fontWeightBold()  // 粗体
                    marginBottom(8f)  // 外边距
                }
            }
            ```

            #### Button组件
            ```kotlin
            Button {
                attr {
                    titleAttr {
                        text("按钮文本")
                    }
                    size(width = 200f, height = 44f)
                    cornerRadius(8f)
                    backgroundColor(Color.parseColor("#6200EE"))
                }

                event {
                    onClick {
                        // 导航到其他页面
                        ctx.acquireModule<RouterModule>(RouterModule.MODULE_NAME)
                            .openPage("detail", JSONObject())
                    }
                }
            }
            ```

            #### View容器
            ```kotlin
            View {
                attr {
                    size(100f, 100f)
                    backgroundColor(Color.parseColor("#F5F5F5"))
                    cornerRadius(10f)
                    padding(16f)
                }

                // 嵌套子组件
                Text { ... }
            }
            ```

            #### Column/Row布局
            ```kotlin
            Column {
                attr {
                    size(300f, 400f)
                    padding(16f)
                    allCenter()  // 子元素居中
                }

                // 纵向排列的子组件
            }

            Row {
                attr {
                    size(300f, 60f)
                    spaceBetween()  // 两端对齐
                }

                // 横向排列的子组件
            }
            ```

            #### Image组件
            ```kotlin
            Image {
                attr {
                    imageUrl("https://example.com/image.png")
                    size(100f, 100f)
                    scaleType(ImageView.ScaleType.CENTER_CROP)
                    cornerRadius(8f)
                }
            }
            ```

            #### InputView输入框
            ```kotlin
            InputView {
                attr {
                    placeholder("请输入内容")
                    size(300f, 40f)
                    fontSize(14f)
                    backgroundColor(Color.parseColor("#F5F5F5"))
                }
            }
            ```

            ### 3. 导航和事件处理

            **页面导航**：
            ```kotlin
            event {
                onClick {
                    ctx.acquireModule<RouterModule>(RouterModule.MODULE_NAME)
                        .openPage("detail", JSONObject().apply {
                            put("id", "123")
                            put("title", "详情")
                        })
                }
            }
            ```

            **返回上一页**：
            ```kotlin
            event {
                onClick {
                    RouterModule.closePage()
                }
            }
            ```

            ### 4. 代码质量要求

            - ✅ 代码必须可以直接编译运行，零错误零警告
            - ✅ 遵循Kotlin代码规范和KuiklyUI DSL规范
            - ✅ 使用有意义的变量和函数命名
            - ✅ 添加必要的中文注释说明关键逻辑
            - ✅ 所有尺寸必须使用Float类型（带f后缀）
            - ✅ 所有颜色必须使用Color.parseColor()解析
            - ✅ 所有属性配置必须在attr {}块内
            - ✅ 所有事件处理必须在event {}块内

            ### 5. 创意性和创新性要求（重点）

            - **交互创新**：设计至少2-3个独特的交互方式
            - **视觉创新**：至少包含1-2个视觉亮点
            - **功能创新**：通过设计提升功能价值
            - **记忆点设计**：确保UI有一个让用户印象深刻的特色

            ## 输出格式

            请直接输出完整的KuiklyUI Kotlin代码，不要包含任何解释文字。
            代码应该包含在```kotlin和```标记之间。

            代码中应通过注释标注创新点和设计亮点。
            """;

    // ============================================================
    // 辅助数据类
    // ============================================================

    /**
     * 实体上下文信息
     */
    @Data
    @Accessors(chain = true, fluent = true)
    public static class EntityContext {
        private String name;
        private String displayName;
        private List<String> primaryFields;
        private String description;
    }

    /**
     * 色彩约束
     */
    @Data
    @Accessors(chain = true, fluent = true)
    public static class ColorConstraints {
        private String primaryColor;
        private String secondaryColor;
        private String backgroundColor;
        private String textColor;
        private String accentColor;
        private boolean darkMode = false;
        private String colorPhilosophy;  // 色彩设计理念（可选）
    }

    /**
     * 技术约束（✅ 修改点3：KuiklyUI技术栈）
     */
    @Data
    @Accessors(chain = true, fluent = true)
    public static class TechnicalConstraints {
        private String framework = "KuiklyUI Framework (Kotlin Multiplatform)";
        private String designSystem = "KuiklyUI Component System";
        private String stateManagement = "Pager生命周期管理";
        private String navigation = "RouterModule";
        private String baseClass = "Pager()";
        private String pageAnnotation = "@Page(\"pageId\")";
        private boolean includeViewModel = false;  // KuiklyUI不使用ViewModel
        private boolean includeDataModels = true;
        private boolean includeNavigation = true;  // 导航是核心功能
        private List<String> supportedPlatforms = List.of(
                "Android", "iOS", "H5", "微信小程序", "鸿蒙"
        );
        private List<String> additionalLibraries = List.of(
                "com.kuikly:core:1.0.0",
                "org.json:json:20210307"  // JSONObject依赖
        );
    }

    /**
     * 交互设计约束
     */
    @Data
    @Accessors(chain = true, fluent = true)
    public static class InteractionConstraints {
        private List<String> gestures = new ArrayList<>();  // 手势交互：swipe、drag、pinch、longPress
        private List<String> animations = new ArrayList<>();  // 动画效果：fade、slide、scale、rotate、morph
        private List<String> feedbacks = new ArrayList<>();  // 反馈机制：haptic、sound、visual
        private List<String> innovativeInteractions = new ArrayList<>();  // 创新交互：voice、ar、3d、ai
        private String interactionPhilosophy;  // 交互设计理念
    }

    // ============================================================
    // 构建方法
    // ============================================================

    /**
     * 从DesignRequest快速创建Builder
     *
     * @param request 设计请求
     * @return LayeredPromptBuilder实例
     */
    public static LayeredPromptBuilder fromRequest(DesignRequest request) {
        LayeredPromptBuilder builder = new LayeredPromptBuilder()
                .userPrompt(request.getUserPrompt());

        // 添加实体信息
        if (request.getEntities() != null && !request.getEntities().isEmpty()) {
            for (DesignRequest.EntityInfo entity : request.getEntities()) {
                builder.entities.add(new EntityContext()
                        .name(entity.getName())
                        .displayName(entity.getDisplayName())
                        .primaryFields(entity.getPrimaryFields()));
            }
        }

        return builder;
    }

    /**
     * 设置风格约束
     *
     * @param styleDescription 风格描述
     * @param keywords 关键词列表
     * @return this
     */
    public LayeredPromptBuilder withStyle(String styleDescription, List<String> keywords) {
        this.styleDescription = styleDescription;
        this.styleKeywords = keywords != null ? keywords : new ArrayList<>();
        return this;
    }

    /**
     * 设置色彩约束
     *
     * @param primaryColor 主色调
     * @param secondaryColor 次要色
     * @param backgroundColor 背景色
     * @param textColor 文字色
     * @param accentColor 强调色
     * @return this
     */
    public LayeredPromptBuilder withColors(
            String primaryColor,
            String secondaryColor,
            String backgroundColor,
            String textColor,
            String accentColor) {

        this.colorConstraints = new ColorConstraints()
                .primaryColor(primaryColor)
                .secondaryColor(secondaryColor)
                .backgroundColor(backgroundColor)
                .textColor(textColor)
                .accentColor(accentColor);

        return this;
    }

    /**
     * 添加色彩设计理念（可选）
     *
     * @param philosophy 色彩设计理念，例如：
     *                   - "温暖明亮的色调，传递积极正面的情绪"
     *                   - "低饱和度配色，打造专业稳重的视觉印象"
     *                   - "对比强烈的色彩搭配，突出视觉焦点"
     * @return this
     */
    public LayeredPromptBuilder withColorPhilosophy(String philosophy) {
        if (this.colorConstraints == null) {
            this.colorConstraints = new ColorConstraints();
        }
        this.colorConstraints.colorPhilosophy(philosophy);
        return this;
    }

    /**
     * 设置布局类型约束
     *
     * @param layoutType 布局类型：card（卡片式）、grid（网格）、list（列表式）
     * @return this
     */
    public LayeredPromptBuilder withLayoutType(String layoutType) {
        this.layoutType = layoutType;
        return this;
    }

    /**
     * 构建完整的分层Prompt
     *
     * @return 完整的prompt字符串
     */
    public String build() {
        StringBuilder prompt = new StringBuilder();

        // Layer 1: 系统身份
        prompt.append(SYSTEM_IDENTITY).append("\n\n");

        // Layer 2: 任务上下文
        prompt.append(buildTaskContext());

        // Layer 3: 约束条件
        prompt.append(buildConstraints());

        // Layer 4: 输出格式
        prompt.append(OUTPUT_FORMAT);

        return prompt.toString();
    }

    /**
     * 构建任务上下文层
     */
    private String buildTaskContext() {
        StringBuilder sb = new StringBuilder();

        sb.append("## 任务目标\n\n");
        sb.append("为以下需求设计").append(styleDescription != null ? styleDescription : "高质量").append("风格的UI：\n\n");
        sb.append(userPrompt).append("\n\n");

        if (!styleKeywords.isEmpty()) {
            sb.append("**设计关键词**：").append(String.join("、", styleKeywords)).append("\n\n");
        }

        if (!entities.isEmpty()) {
            sb.append("## 数据实体\n\n");
            sb.append("系统涉及以下数据实体，请在UI设计中合理展示：\n\n");
            for (EntityContext entity : entities) {
                sb.append("### ").append(entity.displayName).append("（").append(entity.name).append("）\n");
                if (entity.description != null) {
                    sb.append(entity.description).append("\n");
                }
                if (entity.primaryFields != null && !entity.primaryFields.isEmpty()) {
                    sb.append("**主要字段**：").append(String.join(", ", entity.primaryFields)).append("\n");
                }
                sb.append("\n");
            }
        }

        return sb.toString();
    }

    /**
     * 构建约束条件层（✅ 修改点4：添加KuiklyUI组件映射）
     */
    private String buildConstraints() {
        StringBuilder sb = new StringBuilder();

        sb.append("## 设计约束\n\n");

        // 1. 技术栈约束
        sb.append("### 1. 技术栈\n\n");
        sb.append("- **开发框架**：").append(technicalConstraints.framework).append("\n");
        sb.append("- **组件系统**：").append(technicalConstraints.designSystem).append("\n");
        sb.append("- **状态管理**：").append(technicalConstraints.stateManagement).append("\n");
        sb.append("- **导航管理**：").append(technicalConstraints.navigation).append("\n");
        sb.append("- **基类继承**：").append(technicalConstraints.baseClass).append("\n");
        sb.append("- **页面注解**：").append(technicalConstraints.pageAnnotation).append("\n");
        sb.append("- **支持平台**：").append(String.join("、", technicalConstraints.supportedPlatforms)).append("\n");

        // 新增：KuiklyUI组件映射表
        sb.append("\n**KuiklyUI组件库**：\n");
        sb.append("- Text - 文本组件，使用attr { text(), fontSize(), color() }\n");
        sb.append("- Button - 按钮组件，使用titleAttr {}配置文本，event { onClick {} }处理点击\n");
        sb.append("- View - 容器组件，使用attr { size(), backgroundColor() }\n");
        sb.append("- Column - 纵向布局，使用attr { padding(), allCenter() }\n");
        sb.append("- Row - 横向布局，使用attr { spaceBetween() }\n");
        sb.append("- InputView - 输入框，使用attr { placeholder() }\n");
        sb.append("- Image - 图片，使用attr { imageUrl(), scaleType() }\n");
        sb.append("\n");

        if (!technicalConstraints.additionalLibraries.isEmpty()) {
            sb.append("- **额外依赖**：")
                    .append(String.join(", ", technicalConstraints.additionalLibraries))
                    .append("\n\n");
        }

        // 2. 设计风格约束
        if (styleDescription != null) {
            sb.append("### 2. 设计风格\n\n");
            sb.append(styleDescription).append("\n\n");
        }

        // 3. 色彩方案约束
        if (colorConstraints != null) {
            sb.append("### 3. 色彩方案\n\n");
            sb.append("- **主色调**：").append(colorConstraints.primaryColor).append("\n");
            sb.append("- **次要色**：").append(colorConstraints.secondaryColor).append("\n");
            sb.append("- **背景色**：").append(colorConstraints.backgroundColor).append("\n");
            sb.append("- **文字色**：").append(colorConstraints.textColor).append("\n");
            sb.append("- **强调色**：").append(colorConstraints.accentColor).append("\n");
            sb.append("- **主题模式**：").append(colorConstraints.darkMode ? "深色模式" : "浅色模式").append("\n");
            if (colorConstraints.colorPhilosophy != null) {
                sb.append("- **色彩理念**：").append(colorConstraints.colorPhilosophy).append("\n");
            }
            sb.append("\n");
        }

        // 4. 布局约束
        if (layoutType != null) {
            sb.append("### 4. 布局类型\n\n");
            sb.append("推荐使用**").append(layoutType).append("布局**");
            switch (layoutType) {
                case "card" -> sb.append("（卡片式布局，适合展示独立的内容单元）");
                case "grid" -> sb.append("（网格布局，适合展示多个并列的内容项）");
                case "list" -> sb.append("（列表布局，适合展示顺序性的信息流）");
            }
            sb.append("\n\n");
        }

        // 5. 交互设计约束
        if (interactionConstraints != null) {
            sb.append("### 5. 交互设计约束\n\n");

            if (!interactionConstraints.gestures.isEmpty()) {
                sb.append("- **手势交互**：").append(String.join("、", interactionConstraints.gestures)).append("\n");
            }

            if (!interactionConstraints.animations.isEmpty()) {
                sb.append("- **动画效果**：").append(String.join("、", interactionConstraints.animations)).append("\n");
            }

            if (!interactionConstraints.feedbacks.isEmpty()) {
                sb.append("- **反馈机制**：").append(String.join("、", interactionConstraints.feedbacks)).append("\n");
            }

            if (!interactionConstraints.innovativeInteractions.isEmpty()) {
                sb.append("- **创新交互**：").append(String.join("、", interactionConstraints.innovativeInteractions)).append("\n");
            }

            if (interactionConstraints.interactionPhilosophy != null) {
                sb.append("- **交互理念**：").append(interactionConstraints.interactionPhilosophy).append("\n");
            }

            sb.append("\n");
        }

        return sb.toString();
    }

    /**
     * 验证必填字段
     *
     * @throws IllegalStateException 如果缺少必填字段
     */
    public void validate() {
        if (userPrompt == null || userPrompt.isBlank()) {
            throw new IllegalStateException("用户需求描述（userPrompt）不能为空");
        }

        if (colorConstraints == null) {
            throw new IllegalStateException("色彩约束（colorConstraints）不能为空");
        }

        if (styleDescription == null || styleDescription.isBlank()) {
            throw new IllegalStateException("设计风格描述（styleDescription）不能为空");
        }
    }

    /**
     * 获取Prompt预估长度（字符数）
     *
     * @return 预估的prompt字符数
     */
    public int estimateLength() {
        return SYSTEM_IDENTITY.length() +
                (userPrompt != null ? userPrompt.length() : 0) +
                (styleDescription != null ? styleDescription.length() : 0) +
                (colorConstraints != null ? 500 : 0) +  // 色彩方案约500字符
                OUTPUT_FORMAT.length() +
                entities.size() * 200;  // 每个实体约200字符
    }
}
