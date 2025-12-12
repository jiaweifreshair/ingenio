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
 * KuiklyUI多端代码生成器
 *
 * <p>基于KuiklyUI框架生成5端代码：</p>
 * <ul>
 *   <li>Web - React/Next.js代码</li>
 *   <li>Android - Kotlin/Jetpack Compose代码</li>
 *   <li>iOS - Swift/SwiftUI代码</li>
 *   <li>WeChat - 微信小程序代码</li>
 *   <li>HarmonyOS - ArkTS/ArkUI代码</li>
 * </ul>
 *
 * <p>KuiklyUI设计原则：</p>
 * <ul>
 *   <li>统一组件协议：定义跨平台组件规范</li>
 *   <li>声明式UI：采用声明式语法描述界面</li>
 *   <li>响应式数据：支持数据绑定和状态管理</li>
 *   <li>原生性能：生成原生代码，非WebView渲染</li>
 * </ul>
 *
 * @author Justin
 * @since 2025-11-20 V2.0多端代码生成
 */
@Slf4j
@Component
public class KuiklyUIGenerator {

    /**
     * 支持的平台枚举
     */
    public enum Platform {
        WEB("Web", "React/Next.js"),
        ANDROID("Android", "Kotlin/Jetpack Compose"),
        IOS("iOS", "Swift/SwiftUI"),
        WECHAT("WeChat", "微信小程序"),
        HARMONYOS("HarmonyOS", "ArkTS/ArkUI");

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
     * 生成多端代码
     *
     * @param entities 实体列表
     * @param appName 应用名称
     * @return 多端代码生成结果
     */
    public MultiPlatformResult generateMultiPlatform(List<Entity> entities, String appName) {
        log.info("[KuiklyUI] 开始多端代码生成: appName={}, entityCount={}", appName, entities.size());
        long startTime = System.currentTimeMillis();

        try {
            Map<Platform, PlatformCode> platformCodes = new LinkedHashMap<>();

            // 并行生成各平台代码
            for (Platform platform : Platform.values()) {
                PlatformCode code = generateForPlatform(platform, entities, appName);
                platformCodes.put(platform, code);
                log.info("[KuiklyUI] {} 代码生成完成: {} 文件", platform.getDisplayName(), code.getFiles().size());
            }

            long durationMs = System.currentTimeMillis() - startTime;
            log.info("[KuiklyUI] 多端代码生成完成: 耗时={}ms", durationMs);

            return MultiPlatformResult.builder()
                .success(true)
                .platformCodes(platformCodes)
                .durationMs(durationMs)
                .entityCount(entities.size())
                .build();

        } catch (Exception e) {
            log.error("[KuiklyUI] 多端代码生成失败: {}", e.getMessage(), e);
            return MultiPlatformResult.builder()
                .success(false)
                .errorMessage(e.getMessage())
                .durationMs(System.currentTimeMillis() - startTime)
                .build();
        }
    }

    /**
     * 为指定平台生成代码
     */
    private PlatformCode generateForPlatform(Platform platform, List<Entity> entities, String appName) {
        Map<String, String> files = new LinkedHashMap<>();
        String entryPoint;
        String dependencies;

        switch (platform) {
            case WEB -> {
                files = generateWebCode(entities, appName);
                entryPoint = "src/App.tsx";
                dependencies = generateWebDependencies();
            }
            case ANDROID -> {
                files = generateAndroidCode(entities, appName);
                entryPoint = "app/src/main/java/MainActivity.kt";
                dependencies = generateAndroidDependencies();
            }
            case IOS -> {
                files = generateIOSCode(entities, appName);
                entryPoint = appName + "App.swift";
                dependencies = generateIOSDependencies();
            }
            case WECHAT -> {
                files = generateWeChatCode(entities, appName);
                entryPoint = "app.js";
                dependencies = generateWeChatDependencies();
            }
            case HARMONYOS -> {
                files = generateHarmonyOSCode(entities, appName);
                entryPoint = "entry/src/main/ets/pages/Index.ets";
                dependencies = generateHarmonyOSDependencies();
            }
            default -> throw new IllegalArgumentException("Unsupported platform: " + platform);
        }

        return PlatformCode.builder()
            .platform(platform)
            .files(files)
            .entryPoint(entryPoint)
            .dependencies(dependencies)
            .build();
    }

    // ==================== Web (React/Next.js) ====================

    private Map<String, String> generateWebCode(List<Entity> entities, String appName) {
        Map<String, String> files = new LinkedHashMap<>();

        // 生成App.tsx
        files.put("src/App.tsx", generateWebApp(entities, appName));

        // 生成每个实体的组件
        for (Entity entity : entities) {
            String componentName = entity.getName() + "List";
            files.put("src/components/" + componentName + ".tsx", generateWebComponent(entity));
            files.put("src/components/" + entity.getName() + "Form.tsx", generateWebForm(entity));
        }

        // 生成API服务
        files.put("src/lib/api.ts", generateWebApiService(entities));

        // 生成类型定义
        files.put("src/types/index.ts", generateWebTypes(entities));

        return files;
    }

    private String generateWebApp(List<Entity> entities, String appName) {
        StringBuilder sb = new StringBuilder();
        sb.append("import React from 'react';\n");
        sb.append("import { BrowserRouter, Routes, Route } from 'react-router-dom';\n\n");

        for (Entity entity : entities) {
            sb.append("import ").append(entity.getName()).append("List from './components/")
              .append(entity.getName()).append("List';\n");
        }

        sb.append("\nexport default function App() {\n");
        sb.append("  return (\n");
        sb.append("    <BrowserRouter>\n");
        sb.append("      <div className=\"min-h-screen bg-gray-50\">\n");
        sb.append("        <header className=\"bg-white shadow\">\n");
        sb.append("          <div className=\"max-w-7xl mx-auto py-6 px-4\">\n");
        sb.append("            <h1 className=\"text-3xl font-bold text-gray-900\">").append(appName).append("</h1>\n");
        sb.append("          </div>\n");
        sb.append("        </header>\n");
        sb.append("        <main className=\"max-w-7xl mx-auto py-6 px-4\">\n");
        sb.append("          <Routes>\n");

        for (Entity entity : entities) {
            String path = "/" + entity.getName().toLowerCase() + "s";
            sb.append("            <Route path=\"").append(path).append("\" element={<")
              .append(entity.getName()).append("List />} />\n");
        }

        sb.append("          </Routes>\n");
        sb.append("        </main>\n");
        sb.append("      </div>\n");
        sb.append("    </BrowserRouter>\n");
        sb.append("  );\n");
        sb.append("}\n");

        return sb.toString();
    }

    private String generateWebComponent(Entity entity) {
        String name = entity.getName();
        StringBuilder sb = new StringBuilder();

        sb.append("import React, { useEffect, useState } from 'react';\n");
        sb.append("import { ").append(name).append(" } from '../types';\n");
        sb.append("import { api } from '../lib/api';\n\n");

        sb.append("export default function ").append(name).append("List() {\n");
        sb.append("  const [items, setItems] = useState<").append(name).append("[]>([]);\n");
        sb.append("  const [loading, setLoading] = useState(true);\n\n");

        sb.append("  useEffect(() => {\n");
        sb.append("    api.get").append(name).append("s().then(data => {\n");
        sb.append("      setItems(data);\n");
        sb.append("      setLoading(false);\n");
        sb.append("    });\n");
        sb.append("  }, []);\n\n");

        sb.append("  if (loading) return <div>Loading...</div>;\n\n");

        sb.append("  return (\n");
        sb.append("    <div className=\"space-y-4\">\n");
        sb.append("      <h2 className=\"text-2xl font-bold\">").append(name).append(" List</h2>\n");
        sb.append("      <div className=\"grid gap-4\">\n");
        sb.append("        {items.map(item => (\n");
        sb.append("          <div key={item.id} className=\"p-4 bg-white rounded-lg shadow\">\n");

        if (entity.getFields() != null) {
            for (Field field : entity.getFields()) {
                sb.append("            <p><strong>").append(field.getName()).append(":</strong> {item.")
                  .append(field.getName()).append("}</p>\n");
            }
        }

        sb.append("          </div>\n");
        sb.append("        ))}\n");
        sb.append("      </div>\n");
        sb.append("    </div>\n");
        sb.append("  );\n");
        sb.append("}\n");

        return sb.toString();
    }

    private String generateWebForm(Entity entity) {
        String name = entity.getName();
        StringBuilder sb = new StringBuilder();

        sb.append("import React, { useState } from 'react';\n");
        sb.append("import { ").append(name).append(" } from '../types';\n\n");

        sb.append("interface Props {\n");
        sb.append("  onSubmit: (data: Partial<").append(name).append(">) => void;\n");
        sb.append("  initialData?: ").append(name).append(";\n");
        sb.append("}\n\n");

        sb.append("export default function ").append(name).append("Form({ onSubmit, initialData }: Props) {\n");
        sb.append("  const [formData, setFormData] = useState(initialData || {});\n\n");

        sb.append("  const handleSubmit = (e: React.FormEvent) => {\n");
        sb.append("    e.preventDefault();\n");
        sb.append("    onSubmit(formData);\n");
        sb.append("  };\n\n");

        sb.append("  return (\n");
        sb.append("    <form onSubmit={handleSubmit} className=\"space-y-4\">\n");

        if (entity.getFields() != null) {
            for (Field field : entity.getFields()) {
                if (!"id".equals(field.getName())) {
                    sb.append("      <div>\n");
                    sb.append("        <label className=\"block text-sm font-medium\">").append(field.getName()).append("</label>\n");
                    sb.append("        <input\n");
                    sb.append("          type=\"text\"\n");
                    sb.append("          className=\"mt-1 block w-full rounded-md border-gray-300 shadow-sm\"\n");
                    sb.append("          value={formData.").append(field.getName()).append(" || ''}\n");
                    sb.append("          onChange={e => setFormData({...formData, ").append(field.getName()).append(": e.target.value})}\n");
                    sb.append("        />\n");
                    sb.append("      </div>\n");
                }
            }
        }

        sb.append("      <button type=\"submit\" className=\"px-4 py-2 bg-blue-600 text-white rounded-md\">Submit</button>\n");
        sb.append("    </form>\n");
        sb.append("  );\n");
        sb.append("}\n");

        return sb.toString();
    }

    private String generateWebApiService(List<Entity> entities) {
        StringBuilder sb = new StringBuilder();
        sb.append("const API_BASE = process.env.NEXT_PUBLIC_API_URL || '/api';\n\n");

        sb.append("export const api = {\n");

        for (Entity entity : entities) {
            String name = entity.getName();
            String plural = name.toLowerCase() + "s";

            sb.append("  get").append(name).append("s: async () => {\n");
            sb.append("    const res = await fetch(`${API_BASE}/").append(plural).append("`);\n");
            sb.append("    return res.json();\n");
            sb.append("  },\n\n");

            sb.append("  get").append(name).append(": async (id: string) => {\n");
            sb.append("    const res = await fetch(`${API_BASE}/").append(plural).append("/${id}`);\n");
            sb.append("    return res.json();\n");
            sb.append("  },\n\n");

            sb.append("  create").append(name).append(": async (data: any) => {\n");
            sb.append("    const res = await fetch(`${API_BASE}/").append(plural).append("`, {\n");
            sb.append("      method: 'POST',\n");
            sb.append("      headers: { 'Content-Type': 'application/json' },\n");
            sb.append("      body: JSON.stringify(data),\n");
            sb.append("    });\n");
            sb.append("    return res.json();\n");
            sb.append("  },\n\n");
        }

        sb.append("};\n");

        return sb.toString();
    }

    private String generateWebTypes(List<Entity> entities) {
        StringBuilder sb = new StringBuilder();

        for (Entity entity : entities) {
            sb.append("export interface ").append(entity.getName()).append(" {\n");
            sb.append("  id: string;\n");

            if (entity.getFields() != null) {
                for (Field field : entity.getFields()) {
                    if (!"id".equals(field.getName())) {
                        String tsType = mapToTypeScriptType(field.getType().name());
                        sb.append("  ").append(field.getName()).append(": ").append(tsType).append(";\n");
                    }
                }
            }

            sb.append("}\n\n");
        }

        return sb.toString();
    }

    private String generateWebDependencies() {
        return """
            {
              "dependencies": {
                "react": "^19.0.0",
                "react-dom": "^19.0.0",
                "react-router-dom": "^6.21.0",
                "tailwindcss": "^3.4.0"
              }
            }
            """;
    }

    // ==================== Android (Kotlin/Jetpack Compose) ====================

    private Map<String, String> generateAndroidCode(List<Entity> entities, String appName) {
        Map<String, String> files = new LinkedHashMap<>();

        // 生成MainActivity
        files.put("app/src/main/java/MainActivity.kt", generateAndroidMainActivity(entities, appName));

        // 生成每个实体的Screen
        for (Entity entity : entities) {
            files.put("app/src/main/java/ui/" + entity.getName() + "Screen.kt", generateAndroidScreen(entity));
            files.put("app/src/main/java/data/" + entity.getName() + ".kt", generateAndroidDataClass(entity));
        }

        return files;
    }

    private String generateAndroidMainActivity(List<Entity> entities, String appName) {
        StringBuilder sb = new StringBuilder();
        sb.append("package com.").append(appName.toLowerCase()).append("\n\n");
        sb.append("import android.os.Bundle\n");
        sb.append("import androidx.activity.ComponentActivity\n");
        sb.append("import androidx.activity.compose.setContent\n");
        sb.append("import androidx.compose.material3.*\n");
        sb.append("import androidx.navigation.compose.NavHost\n");
        sb.append("import androidx.navigation.compose.composable\n");
        sb.append("import androidx.navigation.compose.rememberNavController\n\n");

        sb.append("class MainActivity : ComponentActivity() {\n");
        sb.append("    override fun onCreate(savedInstanceState: Bundle?) {\n");
        sb.append("        super.onCreate(savedInstanceState)\n");
        sb.append("        setContent {\n");
        sb.append("            MaterialTheme {\n");
        sb.append("                val navController = rememberNavController()\n");
        sb.append("                NavHost(navController = navController, startDestination = \"home\") {\n");

        for (Entity entity : entities) {
            String route = entity.getName().toLowerCase();
            sb.append("                    composable(\"").append(route).append("\") { ").append(entity.getName()).append("Screen() }\n");
        }

        sb.append("                }\n");
        sb.append("            }\n");
        sb.append("        }\n");
        sb.append("    }\n");
        sb.append("}\n");

        return sb.toString();
    }

    private String generateAndroidScreen(Entity entity) {
        String name = entity.getName();
        StringBuilder sb = new StringBuilder();

        sb.append("package com.app.ui\n\n");
        sb.append("import androidx.compose.foundation.layout.*\n");
        sb.append("import androidx.compose.foundation.lazy.LazyColumn\n");
        sb.append("import androidx.compose.foundation.lazy.items\n");
        sb.append("import androidx.compose.material3.*\n");
        sb.append("import androidx.compose.runtime.*\n");
        sb.append("import androidx.compose.ui.Modifier\n");
        sb.append("import androidx.compose.ui.unit.dp\n\n");

        sb.append("@Composable\n");
        sb.append("fun ").append(name).append("Screen() {\n");
        sb.append("    var items by remember { mutableStateOf(listOf<").append(name).append(">()) }\n\n");

        sb.append("    LazyColumn(\n");
        sb.append("        modifier = Modifier.fillMaxSize().padding(16.dp),\n");
        sb.append("        verticalArrangement = Arrangement.spacedBy(8.dp)\n");
        sb.append("    ) {\n");
        sb.append("        items(items) { item ->\n");
        sb.append("            ").append(name).append("Card(item)\n");
        sb.append("        }\n");
        sb.append("    }\n");
        sb.append("}\n\n");

        sb.append("@Composable\n");
        sb.append("fun ").append(name).append("Card(item: ").append(name).append(") {\n");
        sb.append("    Card(\n");
        sb.append("        modifier = Modifier.fillMaxWidth(),\n");
        sb.append("        elevation = CardDefaults.cardElevation(4.dp)\n");
        sb.append("    ) {\n");
        sb.append("        Column(modifier = Modifier.padding(16.dp)) {\n");

        if (entity.getFields() != null) {
            for (Field field : entity.getFields()) {
                sb.append("            Text(\"").append(field.getName()).append(": ${item.")
                  .append(field.getName()).append("}\")\n");
            }
        }

        sb.append("        }\n");
        sb.append("    }\n");
        sb.append("}\n");

        return sb.toString();
    }

    private String generateAndroidDataClass(Entity entity) {
        StringBuilder sb = new StringBuilder();
        sb.append("package com.app.data\n\n");
        sb.append("data class ").append(entity.getName()).append("(\n");

        if (entity.getFields() != null) {
            List<Field> fields = entity.getFields();
            for (int i = 0; i < fields.size(); i++) {
                Field field = fields.get(i);
                String kotlinType = mapToKotlinType(field.getType().name());
                sb.append("    val ").append(field.getName()).append(": ").append(kotlinType);
                if (i < fields.size() - 1) sb.append(",");
                sb.append("\n");
            }
        }

        sb.append(")\n");
        return sb.toString();
    }

    private String generateAndroidDependencies() {
        return """
            dependencies {
                implementation("androidx.compose.material3:material3:1.2.0")
                implementation("androidx.navigation:navigation-compose:2.7.6")
                implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
            }
            """;
    }

    // ==================== iOS (Swift/SwiftUI) ====================

    private Map<String, String> generateIOSCode(List<Entity> entities, String appName) {
        Map<String, String> files = new LinkedHashMap<>();

        files.put(appName + "App.swift", generateIOSApp(entities, appName));

        for (Entity entity : entities) {
            files.put(entity.getName() + "View.swift", generateIOSView(entity));
            files.put(entity.getName() + ".swift", generateIOSModel(entity));
        }

        return files;
    }

    private String generateIOSApp(List<Entity> entities, String appName) {
        StringBuilder sb = new StringBuilder();
        sb.append("import SwiftUI\n\n");

        sb.append("@main\n");
        sb.append("struct ").append(appName).append("App: App {\n");
        sb.append("    var body: some Scene {\n");
        sb.append("        WindowGroup {\n");
        sb.append("            NavigationStack {\n");
        sb.append("                ContentView()\n");
        sb.append("            }\n");
        sb.append("        }\n");
        sb.append("    }\n");
        sb.append("}\n\n");

        sb.append("struct ContentView: View {\n");
        sb.append("    var body: some View {\n");
        sb.append("        List {\n");

        for (Entity entity : entities) {
            sb.append("            NavigationLink(\"").append(entity.getName()).append("\") {\n");
            sb.append("                ").append(entity.getName()).append("View()\n");
            sb.append("            }\n");
        }

        sb.append("        }\n");
        sb.append("        .navigationTitle(\"").append(appName).append("\")\n");
        sb.append("    }\n");
        sb.append("}\n");

        return sb.toString();
    }

    private String generateIOSView(Entity entity) {
        String name = entity.getName();
        StringBuilder sb = new StringBuilder();

        sb.append("import SwiftUI\n\n");

        sb.append("struct ").append(name).append("View: View {\n");
        sb.append("    @State private var items: [").append(name).append("] = []\n\n");

        sb.append("    var body: some View {\n");
        sb.append("        List(items) { item in\n");
        sb.append("            VStack(alignment: .leading) {\n");

        if (entity.getFields() != null) {
            for (Field field : entity.getFields()) {
                sb.append("                Text(\"").append(field.getName()).append(": \\(item.")
                  .append(field.getName()).append(")\")\n");
            }
        }

        sb.append("            }\n");
        sb.append("        }\n");
        sb.append("        .navigationTitle(\"").append(name).append("\")\n");
        sb.append("    }\n");
        sb.append("}\n");

        return sb.toString();
    }

    private String generateIOSModel(Entity entity) {
        StringBuilder sb = new StringBuilder();
        sb.append("import Foundation\n\n");

        sb.append("struct ").append(entity.getName()).append(": Identifiable, Codable {\n");

        if (entity.getFields() != null) {
            for (Field field : entity.getFields()) {
                String swiftType = mapToSwiftType(field.getType().name());
                sb.append("    var ").append(field.getName()).append(": ").append(swiftType).append("\n");
            }
        }

        sb.append("}\n");
        return sb.toString();
    }

    private String generateIOSDependencies() {
        return "// Swift Package Manager dependencies\n// No external dependencies required for basic SwiftUI app\n";
    }

    // ==================== WeChat Mini Program ====================

    private Map<String, String> generateWeChatCode(List<Entity> entities, String appName) {
        Map<String, String> files = new LinkedHashMap<>();

        files.put("app.js", generateWeChatApp(appName));
        files.put("app.json", generateWeChatAppConfig(entities));

        for (Entity entity : entities) {
            String pagePath = "pages/" + entity.getName().toLowerCase();
            files.put(pagePath + "/index.js", generateWeChatPage(entity));
            files.put(pagePath + "/index.wxml", generateWeChatTemplate(entity));
            files.put(pagePath + "/index.wxss", generateWeChatStyle());
        }

        return files;
    }

    private String generateWeChatApp(String appName) {
        return """
            App({
              globalData: {
                apiBase: 'https://api.example.com'
              },
              onLaunch() {
                console.log('%s 启动');
              }
            })
            """.formatted(appName);
    }

    private String generateWeChatAppConfig(List<Entity> entities) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"pages\": [\n");

        for (int i = 0; i < entities.size(); i++) {
            sb.append("    \"pages/").append(entities.get(i).getName().toLowerCase()).append("/index\"");
            if (i < entities.size() - 1) sb.append(",");
            sb.append("\n");
        }

        sb.append("  ],\n");
        sb.append("  \"window\": {\n");
        sb.append("    \"navigationBarTitleText\": \"应用\"\n");
        sb.append("  }\n");
        sb.append("}\n");

        return sb.toString();
    }

    private String generateWeChatPage(Entity entity) {
        return """
            Page({
              data: {
                items: []
              },
              onLoad() {
                this.loadData();
              },
              loadData() {
                wx.request({
                  url: getApp().globalData.apiBase + '/%ss',
                  success: (res) => {
                    this.setData({ items: res.data });
                  }
                });
              }
            })
            """.formatted(entity.getName().toLowerCase());
    }

    private String generateWeChatTemplate(Entity entity) {
        StringBuilder sb = new StringBuilder();
        sb.append("<view class=\"container\">\n");
        sb.append("  <view class=\"title\">").append(entity.getName()).append(" 列表</view>\n");
        sb.append("  <view wx:for=\"{{items}}\" wx:key=\"id\" class=\"item\">\n");

        if (entity.getFields() != null) {
            for (Field field : entity.getFields()) {
                sb.append("    <text>").append(field.getName()).append(": {{item.")
                  .append(field.getName()).append("}}</text>\n");
            }
        }

        sb.append("  </view>\n");
        sb.append("</view>\n");

        return sb.toString();
    }

    private String generateWeChatStyle() {
        return """
            .container { padding: 20rpx; }
            .title { font-size: 36rpx; font-weight: bold; margin-bottom: 20rpx; }
            .item { padding: 20rpx; background: #fff; margin-bottom: 10rpx; border-radius: 8rpx; }
            """;
    }

    private String generateWeChatDependencies() {
        return "// WeChat Mini Program 无需额外依赖\n";
    }

    // ==================== HarmonyOS (ArkTS/ArkUI) ====================

    private Map<String, String> generateHarmonyOSCode(List<Entity> entities, String appName) {
        Map<String, String> files = new LinkedHashMap<>();

        files.put("entry/src/main/ets/pages/Index.ets", generateHarmonyOSIndex(entities, appName));

        for (Entity entity : entities) {
            files.put("entry/src/main/ets/pages/" + entity.getName() + "Page.ets", generateHarmonyOSPage(entity));
            files.put("entry/src/main/ets/model/" + entity.getName() + ".ets", generateHarmonyOSModel(entity));
        }

        return files;
    }

    private String generateHarmonyOSIndex(List<Entity> entities, String appName) {
        StringBuilder sb = new StringBuilder();
        sb.append("import router from '@ohos.router';\n\n");

        sb.append("@Entry\n");
        sb.append("@Component\n");
        sb.append("struct Index {\n");
        sb.append("  build() {\n");
        sb.append("    Column() {\n");
        sb.append("      Text('").append(appName).append("')\n");
        sb.append("        .fontSize(24)\n");
        sb.append("        .fontWeight(FontWeight.Bold)\n");
        sb.append("        .margin({ bottom: 20 })\n\n");

        for (Entity entity : entities) {
            sb.append("      Button('").append(entity.getName()).append("')\n");
            sb.append("        .onClick(() => router.pushUrl({ url: 'pages/").append(entity.getName()).append("Page' }))\n");
            sb.append("        .margin({ bottom: 10 })\n\n");
        }

        sb.append("    }\n");
        sb.append("    .width('100%')\n");
        sb.append("    .height('100%')\n");
        sb.append("    .padding(20)\n");
        sb.append("  }\n");
        sb.append("}\n");

        return sb.toString();
    }

    private String generateHarmonyOSPage(Entity entity) {
        String name = entity.getName();
        StringBuilder sb = new StringBuilder();

        sb.append("@Entry\n");
        sb.append("@Component\n");
        sb.append("struct ").append(name).append("Page {\n");
        sb.append("  @State items: ").append(name).append("[] = []\n\n");

        sb.append("  build() {\n");
        sb.append("    Column() {\n");
        sb.append("      Text('").append(name).append(" 列表')\n");
        sb.append("        .fontSize(20)\n");
        sb.append("        .fontWeight(FontWeight.Bold)\n\n");

        sb.append("      List() {\n");
        sb.append("        ForEach(this.items, (item: ").append(name).append(") => {\n");
        sb.append("          ListItem() {\n");
        sb.append("            Column() {\n");

        if (entity.getFields() != null) {
            for (Field field : entity.getFields()) {
                sb.append("              Text(`").append(field.getName()).append(": ${item.")
                  .append(field.getName()).append("}`)\n");
            }
        }

        sb.append("            }\n");
        sb.append("          }\n");
        sb.append("        })\n");
        sb.append("      }\n");
        sb.append("    }\n");
        sb.append("    .width('100%')\n");
        sb.append("    .height('100%')\n");
        sb.append("  }\n");
        sb.append("}\n");

        return sb.toString();
    }

    private String generateHarmonyOSModel(Entity entity) {
        StringBuilder sb = new StringBuilder();
        sb.append("export class ").append(entity.getName()).append(" {\n");

        if (entity.getFields() != null) {
            for (Field field : entity.getFields()) {
                String tsType = mapToTypeScriptType(field.getType().name());
                sb.append("  ").append(field.getName()).append(": ").append(tsType).append(";\n");
            }
        }

        sb.append("}\n");
        return sb.toString();
    }

    private String generateHarmonyOSDependencies() {
        return "// HarmonyOS SDK 内置依赖\n";
    }

    // ==================== 类型映射辅助方法 ====================

    private String mapToTypeScriptType(String fieldType) {
        return switch (fieldType) {
            case "TEXT", "VARCHAR" -> "string";
            case "INTEGER", "BIGINT", "DECIMAL" -> "number";
            case "BOOLEAN" -> "boolean";
            case "TIMESTAMP", "DATE" -> "string";
            case "JSONB", "JSON" -> "object";
            case "UUID" -> "string";
            default -> "any";
        };
    }

    private String mapToKotlinType(String fieldType) {
        return switch (fieldType) {
            case "TEXT", "VARCHAR", "UUID" -> "String";
            case "INTEGER" -> "Int";
            case "BIGINT" -> "Long";
            case "DECIMAL" -> "Double";
            case "BOOLEAN" -> "Boolean";
            case "TIMESTAMP", "DATE" -> "String";
            default -> "Any";
        };
    }

    private String mapToSwiftType(String fieldType) {
        return switch (fieldType) {
            case "TEXT", "VARCHAR", "UUID" -> "String";
            case "INTEGER" -> "Int";
            case "BIGINT" -> "Int64";
            case "DECIMAL" -> "Double";
            case "BOOLEAN" -> "Bool";
            case "TIMESTAMP", "DATE" -> "Date";
            default -> "Any";
        };
    }
}
