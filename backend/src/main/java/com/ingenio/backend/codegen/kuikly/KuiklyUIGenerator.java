package com.ingenio.backend.codegen.kuikly;

import com.ingenio.backend.codegen.schema.Entity;
import com.ingenio.backend.codegen.schema.Field;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * KuiklyUI多端代码生成器（重构版）
 *
 * <p>基于腾讯开源的KuiklyUI框架（https://github.com/Tencent-TDS/KuiklyUI）生成跨平台代码</p>
 *
 * <p>KuiklyUI核心特性：</p>
 * <ul>
 *   <li>基于Kotlin Multiplatform，一套代码多端运行</li>
 *   <li>原生UI渲染，非WebView/Skia渲染</li>
 *   <li>支持声明式DSL和Compose DSL两种语法</li>
 *   <li>极小包体积：Android AOT ~300KB，iOS AOT ~1.2MB</li>
 *   <li>支持动态化能力（热更新）</li>
 * </ul>
 *
 * <p>支持平台：</p>
 * <ul>
 *   <li>Android 5.0+</li>
 *   <li>iOS 12.0+</li>
 *   <li>HarmonyOS Next 5.0.0+</li>
 *   <li>Web (Beta)</li>
 *   <li>微信小程序 (Beta)</li>
 * </ul>
 *
 * <p>KuiklyUI组件系统：</p>
 * <ul>
 *   <li>布局组件：View, Scroller, Pager</li>
 *   <li>输入组件：Input, TextArea, DatePicker, Switch</li>
 *   <li>展示组件：Text, RichText, Image, Canvas</li>
 *   <li>列表组件：List, WaterFallList, PageList</li>
 *   <li>交互组件：Button, Modal, AlertDialog, Tabs</li>
 * </ul>
 *
 * @author Justin
 * @since 2025-12-22 KuiklyUI框架适配重构
 * @see <a href="https://github.com/Tencent-TDS/KuiklyUI">KuiklyUI GitHub</a>
 * @see <a href="https://kuikly.tds.qq.com">KuiklyUI官方文档</a>
 */
@Slf4j
@Component
public class KuiklyUIGenerator {

    /**
     * 支持的平台枚举
     *
     * <p>KuiklyUI采用"共享代码+平台壳"架构：</p>
     * <ul>
     *   <li>shared模块：Kotlin DSL共享UI代码，所有平台复用</li>
     *   <li>平台壳：各平台的入口应用（androidApp, iosApp等）</li>
     * </ul>
     */
    public enum Platform {
        /** 共享模块 - KuiklyUI Kotlin DSL代码 */
        SHARED("Shared", "Kotlin DSL (KuiklyUI)"),
        /** Android应用壳 */
        ANDROID("Android", "Kotlin + KuiklyUI SDK"),
        /** iOS应用壳 */
        IOS("iOS", "Swift + KuiklyUI SDK"),
        /** HarmonyOS应用壳 */
        HARMONYOS("HarmonyOS", "ArkTS + KuiklyUI SDK"),
        /** Web应用壳 */
        WEB("Web", "TypeScript + KuiklyUI SDK");

        private final String displayName;
        private final String techStack;

        Platform(String displayName, String techStack) {
            this.displayName = displayName;
            this.techStack = techStack;
        }

        public String getDisplayName() { return displayName; }
        public String getTechStack() { return techStack; }
    }

    /**
     * 多端生成结果
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MultiPlatformResult {
        /** 生成是否成功 */
        private boolean success;
        /** 各平台代码 */
        private Map<Platform, PlatformCode> platformCodes;
        /** 生成耗时(ms) */
        private long durationMs;
        /** 生成的实体数量 */
        private int entityCount;
        /** 错误信息 */
        private String errorMessage;
    }

    /**
     * 单平台代码
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PlatformCode {
        /** 平台 */
        private Platform platform;
        /** 生成的文件 */
        private Map<String, String> files;
        /** 入口文件路径 */
        private String entryPoint;
        /** 依赖配置 */
        private String dependencies;
    }

    /**
     * 生成KuiklyUI多端代码
     *
     * <p>生成策略：</p>
     * <ol>
     *   <li>生成shared模块：Kotlin DSL共享UI代码</li>
     *   <li>生成androidApp：Android平台入口</li>
     *   <li>生成iosApp：iOS平台入口</li>
     *   <li>生成ohosApp：HarmonyOS平台入口</li>
     *   <li>生成h5App：Web平台入口</li>
     * </ol>
     *
     * @param entities 实体列表
     * @param appName 应用名称
     * @return 多端代码生成结果
     */
    public MultiPlatformResult generateMultiPlatform(List<Entity> entities, String appName) {
        long startTime = System.currentTimeMillis();

        // 参数校验
        if (entities == null) {
            log.warn("[KuiklyUI] 实体列表为null，返回错误结果");
            return MultiPlatformResult.builder()
                .success(false)
                .errorMessage("实体列表不能为null")
                .durationMs(System.currentTimeMillis() - startTime)
                .build();
        }

        log.info("[KuiklyUI] 开始生成KuiklyUI多端代码: appName={}, entityCount={}", appName, entities.size());

        try {
            Map<Platform, PlatformCode> platformCodes = new LinkedHashMap<>();

            // Step 1: 生成共享模块（核心KuiklyUI DSL代码）
            PlatformCode sharedCode = generateSharedModule(entities, appName);
            platformCodes.put(Platform.SHARED, sharedCode);
            log.info("[KuiklyUI] ✅ 共享模块生成完成: {} 文件", sharedCode.getFiles().size());

            // Step 2: 生成各平台壳应用
            platformCodes.put(Platform.ANDROID, generateAndroidShell(appName));
            platformCodes.put(Platform.IOS, generateIOSShell(appName));
            platformCodes.put(Platform.HARMONYOS, generateHarmonyOSShell(appName));
            platformCodes.put(Platform.WEB, generateWebShell(appName));

            long durationMs = System.currentTimeMillis() - startTime;
            log.info("[KuiklyUI] ✅ KuiklyUI多端代码生成完成: 耗时={}ms, 平台数={}", durationMs, platformCodes.size());

            return MultiPlatformResult.builder()
                .success(true)
                .platformCodes(platformCodes)
                .durationMs(durationMs)
                .entityCount(entities.size())
                .build();

        } catch (Exception e) {
            log.error("[KuiklyUI] ❌ KuiklyUI代码生成失败: {}", e.getMessage(), e);
            return MultiPlatformResult.builder()
                .success(false)
                .errorMessage(e.getMessage())
                .durationMs(System.currentTimeMillis() - startTime)
                .build();
        }
    }

    // ==================== 共享模块 (KuiklyUI Kotlin DSL) ====================

    /**
     * 生成KuiklyUI共享模块
     *
     * <p>共享模块是KuiklyUI的核心，包含所有UI逻辑，各平台复用</p>
     */
    private PlatformCode generateSharedModule(List<Entity> entities, String appName) {
        Map<String, String> files = new LinkedHashMap<>();
        String packageName = "com." + appName.toLowerCase().replaceAll("[^a-z0-9]", "");

        // 1. 生成Pager页面入口
        files.put("shared/src/commonMain/kotlin/" + packageToPath(packageName) + "/pages/MainPager.kt",
            generateMainPager(entities, packageName, appName));

        // 2. 生成各实体的列表页面
        for (Entity entity : entities) {
            files.put("shared/src/commonMain/kotlin/" + packageToPath(packageName) + "/pages/" + entity.getName() + "ListPager.kt",
                generateEntityListPager(entity, packageName));
            files.put("shared/src/commonMain/kotlin/" + packageToPath(packageName) + "/pages/" + entity.getName() + "DetailPager.kt",
                generateEntityDetailPager(entity, packageName));
            files.put("shared/src/commonMain/kotlin/" + packageToPath(packageName) + "/pages/" + entity.getName() + "FormPager.kt",
                generateEntityFormPager(entity, packageName));
        }

        // 3. 生成数据模型
        for (Entity entity : entities) {
            files.put("shared/src/commonMain/kotlin/" + packageToPath(packageName) + "/model/" + entity.getName() + ".kt",
                generateKotlinDataClass(entity, packageName));
        }

        // 4. 生成API服务
        files.put("shared/src/commonMain/kotlin/" + packageToPath(packageName) + "/api/ApiService.kt",
            generateApiService(entities, packageName));

        // 5. 生成主题配置
        files.put("shared/src/commonMain/kotlin/" + packageToPath(packageName) + "/theme/AppTheme.kt",
            generateAppTheme(packageName, appName));

        // 6. 生成组件库
        files.put("shared/src/commonMain/kotlin/" + packageToPath(packageName) + "/components/CommonComponents.kt",
            generateCommonComponents(packageName));

        // 7. 生成build.gradle.kts
        files.put("shared/build.gradle.kts", generateSharedBuildGradle(packageName));

        return PlatformCode.builder()
            .platform(Platform.SHARED)
            .files(files)
            .entryPoint("shared/src/commonMain/kotlin/" + packageToPath(packageName) + "/pages/MainPager.kt")
            .dependencies(generateSharedDependencies())
            .build();
    }

    /**
     * 生成主页面Pager
     *
     * <p>KuiklyUI的Pager是页面入口，类似于Android的Activity/Fragment</p>
     */
    private String generateMainPager(List<Entity> entities, String packageName, String appName) {
        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(packageName).append(".pages\n\n");

        // KuiklyUI imports
        sb.append("import com.tencent.kuikly.core.annotations.Page\n");
        sb.append("import com.tencent.kuikly.core.base.ComposeView\n");
        sb.append("import com.tencent.kuikly.core.reactive.handler.*\n");
        sb.append("import com.tencent.kuikly.core.views.*\n");
        sb.append("import ").append(packageName).append(".theme.AppTheme\n\n");

        sb.append("/**\n");
        sb.append(" * 主页面 - 应用入口\n");
        sb.append(" *\n");
        sb.append(" * KuiklyUI页面使用@Page注解标记，框架自动注册\n");
        sb.append(" */\n");
        sb.append("@Page(\"main\")\n");
        sb.append("class MainPager : ComposeView() {\n\n");

        sb.append("    override fun body(): ViewBuilder {\n");
        sb.append("        return VStack {\n");
        sb.append("            // 顶部导航栏\n");
        sb.append("            HStack {\n");
        sb.append("                Text(\"").append(appName).append("\")\n");
        sb.append("                    .fontSize(24)\n");
        sb.append("                    .fontWeight(\"bold\")\n");
        sb.append("                    .color(AppTheme.primaryColor)\n");
        sb.append("            }\n");
        sb.append("            .padding(16)\n");
        sb.append("            .backgroundColor(AppTheme.backgroundColor)\n\n");

        sb.append("            // 功能菜单列表\n");
        sb.append("            List {\n");

        for (Entity entity : entities) {
            String entityName = entity.getName();
            String description = entity.getDescription() != null ? entity.getDescription() : entityName + "管理";
            sb.append("                ListItem {\n");
            sb.append("                    HStack {\n");
            sb.append("                        VStack {\n");
            sb.append("                            Text(\"").append(entityName).append("\")\n");
            sb.append("                                .fontSize(18)\n");
            sb.append("                                .fontWeight(\"medium\")\n");
            sb.append("                            Text(\"").append(description).append("\")\n");
            sb.append("                                .fontSize(14)\n");
            sb.append("                                .color(AppTheme.textSecondaryColor)\n");
            sb.append("                        }\n");
            sb.append("                        Spacer()\n");
            sb.append("                        Image(\"arrow_right\")\n");
            sb.append("                            .size(20, 20)\n");
            sb.append("                    }\n");
            sb.append("                    .padding(16)\n");
            sb.append("                }\n");
            sb.append("                .onClick { navigator.push(\"").append(entityName.toLowerCase()).append("_list\") }\n\n");
        }

        sb.append("            }\n");
        sb.append("            .flex(1)\n");
        sb.append("        }\n");
        sb.append("        .backgroundColor(AppTheme.backgroundColor)\n");
        sb.append("    }\n");
        sb.append("}\n");

        return sb.toString();
    }

    /**
     * 生成实体列表页面Pager
     */
    private String generateEntityListPager(Entity entity, String packageName) {
        String name = entity.getName();
        String nameLower = name.toLowerCase();

        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(packageName).append(".pages\n\n");

        sb.append("import com.tencent.kuikly.core.annotations.Page\n");
        sb.append("import com.tencent.kuikly.core.base.ComposeView\n");
        sb.append("import com.tencent.kuikly.core.reactive.handler.*\n");
        sb.append("import com.tencent.kuikly.core.views.*\n");
        sb.append("import ").append(packageName).append(".model.").append(name).append("\n");
        sb.append("import ").append(packageName).append(".api.ApiService\n");
        sb.append("import ").append(packageName).append(".theme.AppTheme\n\n");

        sb.append("/**\n");
        sb.append(" * ").append(name).append("列表页面\n");
        sb.append(" */\n");
        sb.append("@Page(\"").append(nameLower).append("_list\")\n");
        sb.append("class ").append(name).append("ListPager : ComposeView() {\n\n");

        sb.append("    // 响应式数据\n");
        sb.append("    private val items = mutableStateListOf<").append(name).append(">()\n");
        sb.append("    private val isLoading = mutableStateOf(true)\n");
        sb.append("    private val errorMessage = mutableStateOf<String?>(null)\n\n");

        sb.append("    override fun onPageLoad() {\n");
        sb.append("        loadData()\n");
        sb.append("    }\n\n");

        sb.append("    private fun loadData() {\n");
        sb.append("        isLoading.value = true\n");
        sb.append("        ApiService.get").append(name).append("List { result ->\n");
        sb.append("            result.onSuccess { data ->\n");
        sb.append("                items.clear()\n");
        sb.append("                items.addAll(data)\n");
        sb.append("                isLoading.value = false\n");
        sb.append("            }.onFailure { error ->\n");
        sb.append("                errorMessage.value = error.message\n");
        sb.append("                isLoading.value = false\n");
        sb.append("            }\n");
        sb.append("        }\n");
        sb.append("    }\n\n");

        sb.append("    override fun body(): ViewBuilder {\n");
        sb.append("        return VStack {\n");
        sb.append("            // 顶部导航栏\n");
        sb.append("            NavigationBar {\n");
        sb.append("                title(\"").append(name).append("列表\")\n");
        sb.append("                leftButton(\"返回\") { navigator.pop() }\n");
        sb.append("                rightButton(\"添加\") { navigator.push(\"").append(nameLower).append("_form\") }\n");
        sb.append("            }\n\n");

        sb.append("            // 内容区域\n");
        sb.append("            if (isLoading.value) {\n");
        sb.append("                ActivityIndicator()\n");
        sb.append("                    .center()\n");
        sb.append("            } else if (errorMessage.value != null) {\n");
        sb.append("                Text(errorMessage.value!!)\n");
        sb.append("                    .color(AppTheme.errorColor)\n");
        sb.append("                    .center()\n");
        sb.append("            } else {\n");
        sb.append("                List {\n");
        sb.append("                    items.forEach { item ->\n");
        sb.append("                        ").append(name).append("ListItem(item)\n");
        sb.append("                            .onClick { navigator.push(\"").append(nameLower).append("_detail\", mapOf(\"id\" to item.id)) }\n");
        sb.append("                    }\n");
        sb.append("                }\n");
        sb.append("                .flex(1)\n");
        sb.append("                .refreshable { loadData() }\n");
        sb.append("            }\n");
        sb.append("        }\n");
        sb.append("        .backgroundColor(AppTheme.backgroundColor)\n");
        sb.append("    }\n\n");

        sb.append("    @Composable\n");
        sb.append("    private fun ").append(name).append("ListItem(item: ").append(name).append("): ViewBuilder {\n");
        sb.append("        return HStack {\n");
        sb.append("            VStack {\n");

        // 显示前3个字段
        if (entity.getFields() != null) {
            int count = 0;
            for (Field field : entity.getFields()) {
                if (count >= 3) break;
                if (!"id".equalsIgnoreCase(field.getName())) {
                    sb.append("                Text(item.").append(field.getName()).append(".toString())\n");
                    if (count == 0) {
                        sb.append("                    .fontSize(16)\n");
                        sb.append("                    .fontWeight(\"medium\")\n");
                    } else {
                        sb.append("                    .fontSize(14)\n");
                        sb.append("                    .color(AppTheme.textSecondaryColor)\n");
                    }
                    count++;
                }
            }
        }

        sb.append("            }\n");
        sb.append("            .flex(1)\n");
        sb.append("            Image(\"arrow_right\")\n");
        sb.append("                .size(16, 16)\n");
        sb.append("        }\n");
        sb.append("        .padding(16)\n");
        sb.append("        .backgroundColor(AppTheme.cardBackgroundColor)\n");
        sb.append("        .cornerRadius(8)\n");
        sb.append("        .margin(horizontal = 16, vertical = 4)\n");
        sb.append("    }\n");
        sb.append("}\n");

        return sb.toString();
    }

    /**
     * 生成实体详情页面Pager
     */
    private String generateEntityDetailPager(Entity entity, String packageName) {
        String name = entity.getName();
        String nameLower = name.toLowerCase();

        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(packageName).append(".pages\n\n");

        sb.append("import com.tencent.kuikly.core.annotations.Page\n");
        sb.append("import com.tencent.kuikly.core.base.ComposeView\n");
        sb.append("import com.tencent.kuikly.core.reactive.handler.*\n");
        sb.append("import com.tencent.kuikly.core.views.*\n");
        sb.append("import ").append(packageName).append(".model.").append(name).append("\n");
        sb.append("import ").append(packageName).append(".api.ApiService\n");
        sb.append("import ").append(packageName).append(".theme.AppTheme\n\n");

        sb.append("/**\n");
        sb.append(" * ").append(name).append("详情页面\n");
        sb.append(" */\n");
        sb.append("@Page(\"").append(nameLower).append("_detail\")\n");
        sb.append("class ").append(name).append("DetailPager : ComposeView() {\n\n");

        sb.append("    private val item = mutableStateOf<").append(name).append("?>(null)\n");
        sb.append("    private val isLoading = mutableStateOf(true)\n\n");

        sb.append("    override fun onPageLoad() {\n");
        sb.append("        val id = pageParams[\"id\"] as? String ?: return\n");
        sb.append("        ApiService.get").append(name).append("(id) { result ->\n");
        sb.append("            result.onSuccess { data ->\n");
        sb.append("                item.value = data\n");
        sb.append("                isLoading.value = false\n");
        sb.append("            }\n");
        sb.append("        }\n");
        sb.append("    }\n\n");

        sb.append("    override fun body(): ViewBuilder {\n");
        sb.append("        return VStack {\n");
        sb.append("            NavigationBar {\n");
        sb.append("                title(\"").append(name).append("详情\")\n");
        sb.append("                leftButton(\"返回\") { navigator.pop() }\n");
        sb.append("                rightButton(\"编辑\") {\n");
        sb.append("                    item.value?.let { navigator.push(\"").append(nameLower).append("_form\", mapOf(\"id\" to it.id)) }\n");
        sb.append("                }\n");
        sb.append("            }\n\n");

        sb.append("            if (isLoading.value) {\n");
        sb.append("                ActivityIndicator().center()\n");
        sb.append("            } else {\n");
        sb.append("                Scroller {\n");
        sb.append("                    VStack {\n");

        if (entity.getFields() != null) {
            for (Field field : entity.getFields()) {
                String fieldName = field.getName();
                String label = field.getDescription() != null ? field.getDescription() : fieldName;
                sb.append("                        DetailRow(\"").append(label).append("\", item.value?.").append(fieldName).append(".toString() ?: \"\")\n");
            }
        }

        sb.append("                    }\n");
        sb.append("                    .padding(16)\n");
        sb.append("                }\n");
        sb.append("                .flex(1)\n");
        sb.append("            }\n\n");

        sb.append("            // 底部操作按钮\n");
        sb.append("            HStack {\n");
        sb.append("                Button(\"删除\") {\n");
        sb.append("                    showConfirmDialog(\"确认删除？\") {\n");
        sb.append("                        item.value?.let { ApiService.delete").append(name).append("(it.id) { navigator.pop() } }\n");
        sb.append("                    }\n");
        sb.append("                }\n");
        sb.append("                .backgroundColor(AppTheme.errorColor)\n");
        sb.append("                .flex(1)\n");
        sb.append("            }\n");
        sb.append("            .padding(16)\n");
        sb.append("        }\n");
        sb.append("        .backgroundColor(AppTheme.backgroundColor)\n");
        sb.append("    }\n\n");

        sb.append("    @Composable\n");
        sb.append("    private fun DetailRow(label: String, value: String): ViewBuilder {\n");
        sb.append("        return HStack {\n");
        sb.append("            Text(label)\n");
        sb.append("                .fontSize(14)\n");
        sb.append("                .color(AppTheme.textSecondaryColor)\n");
        sb.append("                .width(100)\n");
        sb.append("            Text(value)\n");
        sb.append("                .fontSize(16)\n");
        sb.append("                .flex(1)\n");
        sb.append("        }\n");
        sb.append("        .padding(vertical = 12)\n");
        sb.append("        .borderBottom(1, AppTheme.dividerColor)\n");
        sb.append("    }\n");
        sb.append("}\n");

        return sb.toString();
    }

    /**
     * 生成实体表单页面Pager
     */
    private String generateEntityFormPager(Entity entity, String packageName) {
        String name = entity.getName();
        String nameLower = name.toLowerCase();

        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(packageName).append(".pages\n\n");

        sb.append("import com.tencent.kuikly.core.annotations.Page\n");
        sb.append("import com.tencent.kuikly.core.base.ComposeView\n");
        sb.append("import com.tencent.kuikly.core.reactive.handler.*\n");
        sb.append("import com.tencent.kuikly.core.views.*\n");
        sb.append("import ").append(packageName).append(".model.").append(name).append("\n");
        sb.append("import ").append(packageName).append(".api.ApiService\n");
        sb.append("import ").append(packageName).append(".theme.AppTheme\n\n");

        sb.append("/**\n");
        sb.append(" * ").append(name).append("表单页面（新增/编辑）\n");
        sb.append(" */\n");
        sb.append("@Page(\"").append(nameLower).append("_form\")\n");
        sb.append("class ").append(name).append("FormPager : ComposeView() {\n\n");

        sb.append("    private val isEditMode = mutableStateOf(false)\n");
        sb.append("    private val isSubmitting = mutableStateOf(false)\n\n");

        // 为每个字段生成状态
        if (entity.getFields() != null) {
            for (Field field : entity.getFields()) {
                if (!"id".equalsIgnoreCase(field.getName())) {
                    sb.append("    private val ").append(field.getName()).append(" = mutableStateOf(\"\")\n");
                }
            }
        }
        sb.append("\n");

        sb.append("    override fun onPageLoad() {\n");
        sb.append("        val id = pageParams[\"id\"] as? String\n");
        sb.append("        if (id != null) {\n");
        sb.append("            isEditMode.value = true\n");
        sb.append("            ApiService.get").append(name).append("(id) { result ->\n");
        sb.append("                result.onSuccess { data ->\n");

        if (entity.getFields() != null) {
            for (Field field : entity.getFields()) {
                if (!"id".equalsIgnoreCase(field.getName())) {
                    sb.append("                    ").append(field.getName()).append(".value = data.").append(field.getName()).append(".toString()\n");
                }
            }
        }

        sb.append("                }\n");
        sb.append("            }\n");
        sb.append("        }\n");
        sb.append("    }\n\n");

        sb.append("    private fun submit() {\n");
        sb.append("        isSubmitting.value = true\n");
        sb.append("        val data = ").append(name).append("(\n");
        sb.append("            id = pageParams[\"id\"] as? String ?: \"\",\n");

        if (entity.getFields() != null) {
            List<Field> fields = entity.getFields();
            for (int i = 0; i < fields.size(); i++) {
                Field field = fields.get(i);
                if (!"id".equalsIgnoreCase(field.getName())) {
                    sb.append("            ").append(field.getName()).append(" = ").append(field.getName()).append(".value");
                    if (i < fields.size() - 1) sb.append(",");
                    sb.append("\n");
                }
            }
        }

        sb.append("        )\n");
        sb.append("        val api = if (isEditMode.value) ApiService::update").append(name).append(" else ApiService::create").append(name).append("\n");
        sb.append("        api(data) { result ->\n");
        sb.append("            isSubmitting.value = false\n");
        sb.append("            result.onSuccess { navigator.pop() }\n");
        sb.append("            result.onFailure { showToast(it.message ?: \"操作失败\") }\n");
        sb.append("        }\n");
        sb.append("    }\n\n");

        sb.append("    override fun body(): ViewBuilder {\n");
        sb.append("        return VStack {\n");
        sb.append("            NavigationBar {\n");
        sb.append("                title(if (isEditMode.value) \"编辑").append(name).append("\" else \"新增").append(name).append("\")\n");
        sb.append("                leftButton(\"取消\") { navigator.pop() }\n");
        sb.append("            }\n\n");

        sb.append("            Scroller {\n");
        sb.append("                VStack {\n");

        if (entity.getFields() != null) {
            for (Field field : entity.getFields()) {
                if (!"id".equalsIgnoreCase(field.getName())) {
                    String label = field.getDescription() != null ? field.getDescription() : field.getName();
                    sb.append("                    FormInput(\n");
                    sb.append("                        label = \"").append(label).append("\",\n");
                    sb.append("                        value = ").append(field.getName()).append(".value,\n");
                    sb.append("                        onValueChange = { ").append(field.getName()).append(".value = it },\n");
                    sb.append("                        placeholder = \"请输入").append(label).append("\"\n");
                    sb.append("                    )\n");
                }
            }
        }

        sb.append("                }\n");
        sb.append("                .padding(16)\n");
        sb.append("            }\n");
        sb.append("            .flex(1)\n\n");

        sb.append("            // 提交按钮\n");
        sb.append("            Button(if (isSubmitting.value) \"提交中...\" else \"保存\") {\n");
        sb.append("                if (!isSubmitting.value) submit()\n");
        sb.append("            }\n");
        sb.append("            .backgroundColor(AppTheme.primaryColor)\n");
        sb.append("            .disabled(isSubmitting.value)\n");
        sb.append("            .margin(16)\n");
        sb.append("        }\n");
        sb.append("        .backgroundColor(AppTheme.backgroundColor)\n");
        sb.append("    }\n\n");

        sb.append("    @Composable\n");
        sb.append("    private fun FormInput(\n");
        sb.append("        label: String,\n");
        sb.append("        value: String,\n");
        sb.append("        onValueChange: (String) -> Unit,\n");
        sb.append("        placeholder: String = \"\"\n");
        sb.append("    ): ViewBuilder {\n");
        sb.append("        return VStack {\n");
        sb.append("            Text(label)\n");
        sb.append("                .fontSize(14)\n");
        sb.append("                .color(AppTheme.textSecondaryColor)\n");
        sb.append("                .marginBottom(8)\n");
        sb.append("            Input(value)\n");
        sb.append("                .placeholder(placeholder)\n");
        sb.append("                .onTextChange(onValueChange)\n");
        sb.append("                .padding(12)\n");
        sb.append("                .backgroundColor(AppTheme.inputBackgroundColor)\n");
        sb.append("                .cornerRadius(8)\n");
        sb.append("        }\n");
        sb.append("        .marginBottom(16)\n");
        sb.append("    }\n");
        sb.append("}\n");

        return sb.toString();
    }

    /**
     * 生成Kotlin数据类
     */
    private String generateKotlinDataClass(Entity entity, String packageName) {
        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(packageName).append(".model\n\n");

        sb.append("import kotlinx.serialization.Serializable\n\n");

        sb.append("/**\n");
        sb.append(" * ").append(entity.getName());
        if (entity.getDescription() != null) {
            sb.append(" - ").append(entity.getDescription());
        }
        sb.append("\n */\n");
        sb.append("@Serializable\n");
        sb.append("data class ").append(entity.getName()).append("(\n");

        if (entity.getFields() != null) {
            List<Field> fields = entity.getFields();
            for (int i = 0; i < fields.size(); i++) {
                Field field = fields.get(i);
                String kotlinType = mapToKotlinType(field.getType().name());
                sb.append("    val ").append(field.getName()).append(": ").append(kotlinType);
                if (field.isNullable()) {
                    sb.append("? = null");
                }
                if (i < fields.size() - 1) sb.append(",");
                if (field.getDescription() != null) {
                    sb.append(" // ").append(field.getDescription());
                }
                sb.append("\n");
            }
        }

        sb.append(")\n");
        return sb.toString();
    }

    /**
     * 生成API服务
     */
    private String generateApiService(List<Entity> entities, String packageName) {
        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(packageName).append(".api\n\n");

        sb.append("import com.tencent.kuikly.core.network.HttpClient\n");
        sb.append("import com.tencent.kuikly.core.network.Request\n");
        sb.append("import kotlinx.serialization.json.Json\n");

        for (Entity entity : entities) {
            sb.append("import ").append(packageName).append(".model.").append(entity.getName()).append("\n");
        }
        sb.append("\n");

        sb.append("/**\n");
        sb.append(" * API服务 - 后端接口调用\n");
        sb.append(" *\n");
        sb.append(" * 使用KuiklyUI内置的HttpClient进行网络请求\n");
        sb.append(" */\n");
        sb.append("object ApiService {\n\n");

        sb.append("    private const val BASE_URL = \"http://localhost:8080/api/v1\"\n");
        sb.append("    private val json = Json { ignoreUnknownKeys = true }\n\n");

        for (Entity entity : entities) {
            String name = entity.getName();
            String nameLower = name.toLowerCase();
            String plural = nameLower + "s";

            // 获取列表
            sb.append("    /**\n");
            sb.append("     * 获取").append(name).append("列表\n");
            sb.append("     */\n");
            sb.append("    fun get").append(name).append("List(callback: (Result<List<").append(name).append(">>) -> Unit) {\n");
            sb.append("        HttpClient.get(\"$BASE_URL/").append(plural).append("\") { response ->\n");
            sb.append("            try {\n");
            sb.append("                val result = json.decodeFromString<ApiResponse<List<").append(name).append(">>>(response.body)\n");
            sb.append("                callback(Result.success(result.data))\n");
            sb.append("            } catch (e: Exception) {\n");
            sb.append("                callback(Result.failure(e))\n");
            sb.append("            }\n");
            sb.append("        }\n");
            sb.append("    }\n\n");

            // 获取单个
            sb.append("    /**\n");
            sb.append("     * 获取").append(name).append("详情\n");
            sb.append("     */\n");
            sb.append("    fun get").append(name).append("(id: String, callback: (Result<").append(name).append(">) -> Unit) {\n");
            sb.append("        HttpClient.get(\"$BASE_URL/").append(plural).append("/$id\") { response ->\n");
            sb.append("            try {\n");
            sb.append("                val result = json.decodeFromString<ApiResponse<").append(name).append(">>(response.body)\n");
            sb.append("                callback(Result.success(result.data))\n");
            sb.append("            } catch (e: Exception) {\n");
            sb.append("                callback(Result.failure(e))\n");
            sb.append("            }\n");
            sb.append("        }\n");
            sb.append("    }\n\n");

            // 创建
            sb.append("    /**\n");
            sb.append("     * 创建").append(name).append("\n");
            sb.append("     */\n");
            sb.append("    fun create").append(name).append("(data: ").append(name).append(", callback: (Result<").append(name).append(">) -> Unit) {\n");
            sb.append("        val body = json.encodeToString(").append(name).append(".serializer(), data)\n");
            sb.append("        HttpClient.post(\"$BASE_URL/").append(plural).append("\", body) { response ->\n");
            sb.append("            try {\n");
            sb.append("                val result = json.decodeFromString<ApiResponse<").append(name).append(">>(response.body)\n");
            sb.append("                callback(Result.success(result.data))\n");
            sb.append("            } catch (e: Exception) {\n");
            sb.append("                callback(Result.failure(e))\n");
            sb.append("            }\n");
            sb.append("        }\n");
            sb.append("    }\n\n");

            // 更新
            sb.append("    /**\n");
            sb.append("     * 更新").append(name).append("\n");
            sb.append("     */\n");
            sb.append("    fun update").append(name).append("(data: ").append(name).append(", callback: (Result<").append(name).append(">) -> Unit) {\n");
            sb.append("        val body = json.encodeToString(").append(name).append(".serializer(), data)\n");
            sb.append("        HttpClient.put(\"$BASE_URL/").append(plural).append("/${data.id}\", body) { response ->\n");
            sb.append("            try {\n");
            sb.append("                val result = json.decodeFromString<ApiResponse<").append(name).append(">>(response.body)\n");
            sb.append("                callback(Result.success(result.data))\n");
            sb.append("            } catch (e: Exception) {\n");
            sb.append("                callback(Result.failure(e))\n");
            sb.append("            }\n");
            sb.append("        }\n");
            sb.append("    }\n\n");

            // 删除
            sb.append("    /**\n");
            sb.append("     * 删除").append(name).append("\n");
            sb.append("     */\n");
            sb.append("    fun delete").append(name).append("(id: String, callback: (Result<Unit>) -> Unit) {\n");
            sb.append("        HttpClient.delete(\"$BASE_URL/").append(plural).append("/$id\") { response ->\n");
            sb.append("            if (response.statusCode == 200 || response.statusCode == 204) {\n");
            sb.append("                callback(Result.success(Unit))\n");
            sb.append("            } else {\n");
            sb.append("                callback(Result.failure(Exception(\"删除失败\")))\n");
            sb.append("            }\n");
            sb.append("        }\n");
            sb.append("    }\n\n");
        }

        sb.append("    /**\n");
        sb.append("     * API响应封装\n");
        sb.append("     */\n");
        sb.append("    @kotlinx.serialization.Serializable\n");
        sb.append("    data class ApiResponse<T>(\n");
        sb.append("        val code: Int,\n");
        sb.append("        val message: String,\n");
        sb.append("        val data: T\n");
        sb.append("    )\n");
        sb.append("}\n");

        return sb.toString();
    }

    /**
     * 生成主题配置
     */
    private String generateAppTheme(String packageName, String appName) {
        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(packageName).append(".theme\n\n");

        sb.append("/**\n");
        sb.append(" * ").append(appName).append(" 主题配置\n");
        sb.append(" *\n");
        sb.append(" * 统一管理应用的颜色、字体、间距等设计规范\n");
        sb.append(" */\n");
        sb.append("object AppTheme {\n");
        sb.append("    // 主色调\n");
        sb.append("    const val primaryColor = \"#3B82F6\"\n");
        sb.append("    const val primaryLightColor = \"#60A5FA\"\n");
        sb.append("    const val primaryDarkColor = \"#2563EB\"\n\n");

        sb.append("    // 背景色\n");
        sb.append("    const val backgroundColor = \"#F8FAFC\"\n");
        sb.append("    const val cardBackgroundColor = \"#FFFFFF\"\n");
        sb.append("    const val inputBackgroundColor = \"#F1F5F9\"\n\n");

        sb.append("    // 文字色\n");
        sb.append("    const val textPrimaryColor = \"#1E293B\"\n");
        sb.append("    const val textSecondaryColor = \"#64748B\"\n");
        sb.append("    const val textDisabledColor = \"#94A3B8\"\n\n");

        sb.append("    // 功能色\n");
        sb.append("    const val successColor = \"#22C55E\"\n");
        sb.append("    const val warningColor = \"#F59E0B\"\n");
        sb.append("    const val errorColor = \"#EF4444\"\n\n");

        sb.append("    // 边框色\n");
        sb.append("    const val borderColor = \"#E2E8F0\"\n");
        sb.append("    const val dividerColor = \"#F1F5F9\"\n\n");

        sb.append("    // 字体大小\n");
        sb.append("    const val fontSizeXs = 12\n");
        sb.append("    const val fontSizeSm = 14\n");
        sb.append("    const val fontSizeMd = 16\n");
        sb.append("    const val fontSizeLg = 18\n");
        sb.append("    const val fontSizeXl = 24\n\n");

        sb.append("    // 间距\n");
        sb.append("    const val spacingXs = 4\n");
        sb.append("    const val spacingSm = 8\n");
        sb.append("    const val spacingMd = 16\n");
        sb.append("    const val spacingLg = 24\n");
        sb.append("    const val spacingXl = 32\n\n");

        sb.append("    // 圆角\n");
        sb.append("    const val radiusSm = 4\n");
        sb.append("    const val radiusMd = 8\n");
        sb.append("    const val radiusLg = 16\n");
        sb.append("}\n");

        return sb.toString();
    }

    /**
     * 生成通用组件库
     */
    private String generateCommonComponents(String packageName) {
        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(packageName).append(".components\n\n");

        sb.append("import com.tencent.kuikly.core.base.ComposeView\n");
        sb.append("import com.tencent.kuikly.core.views.*\n");
        sb.append("import ").append(packageName).append(".theme.AppTheme\n\n");

        sb.append("/**\n");
        sb.append(" * 通用组件库\n");
        sb.append(" *\n");
        sb.append(" * 封装常用UI组件，统一样式和交互\n");
        sb.append(" */\n");
        sb.append("object CommonComponents {\n\n");

        sb.append("    /**\n");
        sb.append("     * 主按钮\n");
        sb.append("     */\n");
        sb.append("    @Composable\n");
        sb.append("    fun PrimaryButton(text: String, onClick: () -> Unit): ViewBuilder {\n");
        sb.append("        return Button(text) { onClick() }\n");
        sb.append("            .backgroundColor(AppTheme.primaryColor)\n");
        sb.append("            .textColor(\"#FFFFFF\")\n");
        sb.append("            .cornerRadius(AppTheme.radiusMd)\n");
        sb.append("            .padding(horizontal = 24, vertical = 12)\n");
        sb.append("    }\n\n");

        sb.append("    /**\n");
        sb.append("     * 次要按钮\n");
        sb.append("     */\n");
        sb.append("    @Composable\n");
        sb.append("    fun SecondaryButton(text: String, onClick: () -> Unit): ViewBuilder {\n");
        sb.append("        return Button(text) { onClick() }\n");
        sb.append("            .backgroundColor(\"transparent\")\n");
        sb.append("            .textColor(AppTheme.primaryColor)\n");
        sb.append("            .borderWidth(1)\n");
        sb.append("            .borderColor(AppTheme.primaryColor)\n");
        sb.append("            .cornerRadius(AppTheme.radiusMd)\n");
        sb.append("            .padding(horizontal = 24, vertical = 12)\n");
        sb.append("    }\n\n");

        sb.append("    /**\n");
        sb.append("     * 卡片容器\n");
        sb.append("     */\n");
        sb.append("    @Composable\n");
        sb.append("    fun Card(content: ViewBuilder.() -> Unit): ViewBuilder {\n");
        sb.append("        return VStack {\n");
        sb.append("            content()\n");
        sb.append("        }\n");
        sb.append("        .backgroundColor(AppTheme.cardBackgroundColor)\n");
        sb.append("        .cornerRadius(AppTheme.radiusLg)\n");
        sb.append("        .shadow(2, \"#00000010\")\n");
        sb.append("        .padding(AppTheme.spacingMd)\n");
        sb.append("    }\n\n");

        sb.append("    /**\n");
        sb.append("     * 空状态提示\n");
        sb.append("     */\n");
        sb.append("    @Composable\n");
        sb.append("    fun EmptyState(message: String = \"暂无数据\"): ViewBuilder {\n");
        sb.append("        return VStack {\n");
        sb.append("            Image(\"empty_state\")\n");
        sb.append("                .size(120, 120)\n");
        sb.append("            Text(message)\n");
        sb.append("                .fontSize(AppTheme.fontSizeMd)\n");
        sb.append("                .color(AppTheme.textSecondaryColor)\n");
        sb.append("                .marginTop(AppTheme.spacingMd)\n");
        sb.append("        }\n");
        sb.append("        .center()\n");
        sb.append("        .flex(1)\n");
        sb.append("    }\n\n");

        sb.append("    /**\n");
        sb.append("     * 加载中状态\n");
        sb.append("     */\n");
        sb.append("    @Composable\n");
        sb.append("    fun LoadingState(message: String = \"加载中...\"): ViewBuilder {\n");
        sb.append("        return VStack {\n");
        sb.append("            ActivityIndicator()\n");
        sb.append("            Text(message)\n");
        sb.append("                .fontSize(AppTheme.fontSizeSm)\n");
        sb.append("                .color(AppTheme.textSecondaryColor)\n");
        sb.append("                .marginTop(AppTheme.spacingSm)\n");
        sb.append("        }\n");
        sb.append("        .center()\n");
        sb.append("        .flex(1)\n");
        sb.append("    }\n");

        sb.append("}\n");

        return sb.toString();
    }

    /**
     * 生成shared模块的build.gradle.kts
     */
    private String generateSharedBuildGradle(String packageName) {
        return """
            plugins {
                alias(libs.plugins.kotlinMultiplatform)
                alias(libs.plugins.androidLibrary)
                alias(libs.plugins.kuikly)
                kotlin("plugin.serialization")
            }

            kotlin {
                androidTarget {
                    compilations.all {
                        kotlinOptions {
                            jvmTarget = "17"
                        }
                    }
                }

                listOf(
                    iosX64(),
                    iosArm64(),
                    iosSimulatorArm64()
                ).forEach {
                    it.binaries.framework {
                        baseName = "shared"
                    }
                }

                sourceSets {
                    commonMain.dependencies {
                        // KuiklyUI核心依赖
                        implementation("com.tencent.kuikly:core:1.0.0")
                        implementation("com.tencent.kuikly:compose:1.0.0")

                        // Kotlin序列化
                        implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")

                        // 协程
                        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
                    }

                    commonTest.dependencies {
                        implementation(kotlin("test"))
                    }

                    androidMain.dependencies {
                        implementation("com.tencent.kuikly:core-render-android:1.0.0")
                    }

                    iosMain.dependencies {
                        implementation("com.tencent.kuikly:core-render-ios:1.0.0")
                    }
                }
            }

            android {
                namespace = "%s"
                compileSdk = 34
                defaultConfig {
                    minSdk = 21
                }
                compileOptions {
                    sourceCompatibility = JavaVersion.VERSION_17
                    targetCompatibility = JavaVersion.VERSION_17
                }
            }
            """.formatted(packageName);
    }

    private String generateSharedDependencies() {
        return """
            # KuiklyUI Dependencies

            ## Core
            - com.tencent.kuikly:core:1.0.0
            - com.tencent.kuikly:compose:1.0.0

            ## Platform Renderers
            - com.tencent.kuikly:core-render-android:1.0.0
            - com.tencent.kuikly:core-render-ios:1.0.0
            - com.tencent.kuikly:core-render-ohos:1.0.0
            - com.tencent.kuikly:core-render-h5:1.0.0

            ## Kotlin
            - org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0
            - org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3
            """;
    }

    // ==================== 平台壳应用 ====================

    /**
     * 生成Android平台壳
     */
    private PlatformCode generateAndroidShell(String appName) {
        Map<String, String> files = new LinkedHashMap<>();
        String packageName = "com." + appName.toLowerCase().replaceAll("[^a-z0-9]", "");

        files.put("androidApp/src/main/java/" + packageToPath(packageName) + "/MainActivity.kt",
            generateAndroidMainActivity(packageName, appName));
        files.put("androidApp/src/main/AndroidManifest.xml",
            generateAndroidManifest(packageName, appName));
        files.put("androidApp/build.gradle.kts",
            generateAndroidBuildGradle(packageName));

        return PlatformCode.builder()
            .platform(Platform.ANDROID)
            .files(files)
            .entryPoint("androidApp/src/main/java/" + packageToPath(packageName) + "/MainActivity.kt")
            .dependencies("com.tencent.kuikly:core-render-android:1.0.0")
            .build();
    }

    private String generateAndroidMainActivity(String packageName, String appName) {
        return """
            package %s

            import android.os.Bundle
            import com.tencent.kuikly.android.KuiklyActivity
            import com.tencent.kuikly.core.Kuikly

            /**
             * %s Android入口
             *
             * 继承KuiklyActivity，自动初始化KuiklyUI渲染引擎
             */
            class MainActivity : KuiklyActivity() {

                override fun onCreate(savedInstanceState: Bundle?) {
                    super.onCreate(savedInstanceState)

                    // 初始化KuiklyUI
                    Kuikly.init(this) {
                        // 启用调试模式（开发环境）
                        debug = BuildConfig.DEBUG
                        // 设置首页
                        startPage = "main"
                    }
                }
            }
            """.formatted(packageName, appName);
    }

    private String generateAndroidManifest(String packageName, String appName) {
        return """
            <?xml version="1.0" encoding="utf-8"?>
            <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                package="%s">

                <uses-permission android:name="android.permission.INTERNET" />

                <application
                    android:name=".MainApplication"
                    android:allowBackup="true"
                    android:icon="@mipmap/ic_launcher"
                    android:label="%s"
                    android:theme="@style/Theme.AppCompat.Light.NoActionBar">

                    <activity
                        android:name=".MainActivity"
                        android:exported="true"
                        android:windowSoftInputMode="adjustResize">
                        <intent-filter>
                            <action android:name="android.intent.action.MAIN" />
                            <category android:name="android.intent.category.LAUNCHER" />
                        </intent-filter>
                    </activity>
                </application>
            </manifest>
            """.formatted(packageName, appName);
    }

    private String generateAndroidBuildGradle(String packageName) {
        return """
            plugins {
                alias(libs.plugins.androidApplication)
                alias(libs.plugins.kotlinAndroid)
                alias(libs.plugins.kuikly)
            }

            android {
                namespace = "%s"
                compileSdk = 34

                defaultConfig {
                    applicationId = "%s"
                    minSdk = 21
                    targetSdk = 34
                    versionCode = 1
                    versionName = "1.0.0"
                }

                buildTypes {
                    release {
                        isMinifyEnabled = true
                        proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
                    }
                }

                compileOptions {
                    sourceCompatibility = JavaVersion.VERSION_17
                    targetCompatibility = JavaVersion.VERSION_17
                }

                kotlinOptions {
                    jvmTarget = "17"
                }
            }

            dependencies {
                implementation(project(":shared"))
                implementation("com.tencent.kuikly:core-render-android:1.0.0")
                implementation("androidx.appcompat:appcompat:1.6.1")
            }
            """.formatted(packageName, packageName);
    }

    /**
     * 生成iOS平台壳
     */
    private PlatformCode generateIOSShell(String appName) {
        Map<String, String> files = new LinkedHashMap<>();

        files.put("iosApp/iosApp/" + appName + "App.swift",
            generateIOSAppEntry(appName));
        files.put("iosApp/iosApp/ContentView.swift",
            generateIOSContentView(appName));
        files.put("iosApp/iosApp/Info.plist",
            generateIOSInfoPlist(appName));

        return PlatformCode.builder()
            .platform(Platform.IOS)
            .files(files)
            .entryPoint("iosApp/iosApp/" + appName + "App.swift")
            .dependencies("KuiklyUI iOS SDK")
            .build();
    }

    private String generateIOSAppEntry(String appName) {
        return """
            import SwiftUI
            import shared
            import KuiklyiOS

            /**
             * %s iOS入口
             */
            @main
            struct %sApp: App {

                init() {
                    // 初始化KuiklyUI
                    Kuikly.shared.initialize { config in
                        config.debug = true
                        config.startPage = "main"
                    }
                }

                var body: some Scene {
                    WindowGroup {
                        KuiklyView()
                            .ignoresSafeArea()
                    }
                }
            }
            """.formatted(appName, appName);
    }

    private String generateIOSContentView(String appName) {
        return """
            import SwiftUI
            import KuiklyiOS

            /**
             * KuiklyUI容器视图
             */
            struct KuiklyView: UIViewControllerRepresentable {

                func makeUIViewController(context: Context) -> KuiklyViewController {
                    return KuiklyViewController()
                }

                func updateUIViewController(_ uiViewController: KuiklyViewController, context: Context) {
                    // 更新视图
                }
            }
            """;
    }

    private String generateIOSInfoPlist(String appName) {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
            <plist version="1.0">
            <dict>
                <key>CFBundleName</key>
                <string>%s</string>
                <key>CFBundleIdentifier</key>
                <string>$(PRODUCT_BUNDLE_IDENTIFIER)</string>
                <key>CFBundleVersion</key>
                <string>1</string>
                <key>CFBundleShortVersionString</key>
                <string>1.0</string>
                <key>UILaunchStoryboardName</key>
                <string>LaunchScreen</string>
                <key>UIRequiredDeviceCapabilities</key>
                <array>
                    <string>armv7</string>
                </array>
                <key>UISupportedInterfaceOrientations</key>
                <array>
                    <string>UIInterfaceOrientationPortrait</string>
                </array>
                <key>NSAppTransportSecurity</key>
                <dict>
                    <key>NSAllowsArbitraryLoads</key>
                    <true/>
                </dict>
            </dict>
            </plist>
            """.formatted(appName);
    }

    /**
     * 生成HarmonyOS平台壳
     */
    private PlatformCode generateHarmonyOSShell(String appName) {
        Map<String, String> files = new LinkedHashMap<>();

        files.put("ohosApp/entry/src/main/ets/entryability/EntryAbility.ets",
            generateHarmonyOSEntryAbility(appName));
        files.put("ohosApp/entry/src/main/ets/pages/Index.ets",
            generateHarmonyOSIndexPage(appName));
        files.put("ohosApp/entry/src/main/module.json5",
            generateHarmonyOSModuleJson(appName));

        return PlatformCode.builder()
            .platform(Platform.HARMONYOS)
            .files(files)
            .entryPoint("ohosApp/entry/src/main/ets/entryability/EntryAbility.ets")
            .dependencies("KuiklyUI HarmonyOS SDK")
            .build();
    }

    private String generateHarmonyOSEntryAbility(String appName) {
        return """
            import UIAbility from '@ohos.app.ability.UIAbility';
            import window from '@ohos.window';
            import { Kuikly } from '@anthropic/kuikly-ohos';

            /**
             * %s HarmonyOS入口Ability
             */
            export default class EntryAbility extends UIAbility {
                onCreate(want, launchParam) {
                    // 初始化KuiklyUI
                    Kuikly.init({
                        debug: true,
                        startPage: 'main'
                    });
                }

                onWindowStageCreate(windowStage: window.WindowStage) {
                    windowStage.loadContent('pages/Index', (err, data) => {
                        if (err.code) {
                            console.error('Failed to load content: ' + JSON.stringify(err));
                            return;
                        }
                    });
                }
            }
            """.formatted(appName);
    }

    private String generateHarmonyOSIndexPage(String appName) {
        return """
            import { KuiklyView } from '@anthropic/kuikly-ohos';

            /**
             * %s HarmonyOS主页面
             */
            @Entry
            @Component
            struct Index {
                build() {
                    Column() {
                        KuiklyView({
                            pageName: 'main'
                        })
                    }
                    .width('100%%')
                    .height('100%%')
                }
            }
            """.formatted(appName);
    }

    private String generateHarmonyOSModuleJson(String appName) {
        return """
            {
              "module": {
                "name": "entry",
                "type": "entry",
                "description": "%s HarmonyOS Module",
                "mainElement": "EntryAbility",
                "deviceTypes": ["phone", "tablet"],
                "deliveryWithInstall": true,
                "installationFree": false,
                "pages": "$profile:main_pages",
                "abilities": [
                  {
                    "name": "EntryAbility",
                    "srcEntry": "./ets/entryability/EntryAbility.ets",
                    "description": "Entry Ability",
                    "icon": "$media:icon",
                    "label": "%s",
                    "startWindowIcon": "$media:icon",
                    "startWindowBackground": "$color:start_window_background",
                    "exported": true,
                    "skills": [
                      {
                        "entities": ["entity.system.home"],
                        "actions": ["action.system.home"]
                      }
                    ]
                  }
                ]
              }
            }
            """.formatted(appName, appName);
    }

    /**
     * 生成Web平台壳
     */
    private PlatformCode generateWebShell(String appName) {
        Map<String, String> files = new LinkedHashMap<>();

        files.put("h5App/index.html", generateWebIndexHtml(appName));
        files.put("h5App/src/main.ts", generateWebMain(appName));
        files.put("h5App/package.json", generateWebPackageJson(appName));

        return PlatformCode.builder()
            .platform(Platform.WEB)
            .files(files)
            .entryPoint("h5App/index.html")
            .dependencies("@anthropic/kuikly-h5")
            .build();
    }

    private String generateWebIndexHtml(String appName) {
        return """
            <!DOCTYPE html>
            <html lang="zh-CN">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>%s</title>
                <style>
                    * { margin: 0; padding: 0; box-sizing: border-box; }
                    html, body, #app { width: 100%%; height: 100%%; }
                </style>
            </head>
            <body>
                <div id="app"></div>
                <script type="module" src="/src/main.ts"></script>
            </body>
            </html>
            """.formatted(appName);
    }

    private String generateWebMain(String appName) {
        return """
            import { Kuikly } from '@anthropic/kuikly-h5';

            /**
             * %s Web入口
             */
            async function bootstrap() {
                // 初始化KuiklyUI
                await Kuikly.init({
                    container: '#app',
                    debug: import.meta.env.DEV,
                    startPage: 'main'
                });

                console.log('%s 已启动');
            }

            bootstrap().catch(console.error);
            """.formatted(appName, appName);
    }

    private String generateWebPackageJson(String appName) {
        return """
            {
              "name": "%s",
              "version": "1.0.0",
              "private": true,
              "type": "module",
              "scripts": {
                "dev": "vite",
                "build": "vite build",
                "preview": "vite preview"
              },
              "dependencies": {
                "@anthropic/kuikly-h5": "^1.0.0"
              },
              "devDependencies": {
                "typescript": "^5.3.0",
                "vite": "^5.0.0"
              }
            }
            """.formatted(appName.toLowerCase());
    }

    // ==================== 工具方法 ====================

    private String packageToPath(String packageName) {
        return packageName.replace('.', '/');
    }

    private String mapToKotlinType(String fieldType) {
        return switch (fieldType) {
            case "TEXT", "VARCHAR", "UUID" -> "String";
            case "INTEGER" -> "Int";
            case "BIGINT" -> "Long";
            case "DECIMAL" -> "Double";
            case "BOOLEAN" -> "Boolean";
            case "TIMESTAMP", "DATE" -> "String";
            case "JSONB", "JSON" -> "Map<String, Any>";
            default -> "Any";
        };
    }
}
