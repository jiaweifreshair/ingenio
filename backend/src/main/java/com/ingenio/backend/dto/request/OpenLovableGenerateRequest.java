package com.ingenio.backend.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * OpenLovable代码生成请求DTO
 * 用于V2.0架构Plan阶段调用OpenLovable-CN生成前端原型
 *
 * @author Ingenio Team
 * @version 2.0.0
 * @since 2025-11-14
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OpenLovableGenerateRequest {

    /**
     * 用户需求描述（自然语言）
     * 必填，描述要生成的应用功能
     */
    private String userRequirement;

    /**
     * 参考网站URL列表（可选）
     * 当intent为CLONE或HYBRID时，提供要爬取的网站URL
     */
    private List<String> referenceUrls;

    /**
     * 定制化需求（可选）
     * 当intent为HYBRID时，描述在参考网站基础上的定制化修改
     */
    private String customizationRequirement;

    /**
     * AI模型选择（可选，默认使用 Gemini 3 Pro）
     *
     * 支持的模型示例：
     * - gemini-3-pro-preview（默认）
     * - deepseek-r1-0528
     * - deepseek-v3
     * - qwen3-max
     * - kimi-k2
     */
    @Builder.Default
    private String aiModel = "gemini-3-pro-preview";

    /**
     * 是否需要爬取网站（可选，默认根据referenceUrls自动判断）
     * true: 强制爬取referenceUrls
     * false: 不爬取，纯生成
     * null: 自动判断（有referenceUrls则爬取）
     */
    private Boolean needsCrawling;

    /**
     * 生成超时时间（秒，默认30秒）
     * OpenLovable生成时间通常在5-10秒
     */
    @Builder.Default
    private Integer timeoutSeconds = 30;

    /**
     * 是否流式返回（默认false）
     * true: 使用SSE流式返回生成过程
     * false: 等待完成后返回最终结果
     */
    @Builder.Default
    private Boolean streaming = false;

    /**
     * 沙箱ID（可选）
     * 如果已有沙箱，可复用已有沙箱
     * 如果为null，会自动创建新沙箱
     */
    private String sandboxId;

    // ==================== Blueprint（前端约束）====================

    /**
     * Blueprint 前端约束规范（可选）
     *
     * 说明：
     * - 仅包含前端相关信息（如 apiSpec/uiSpec/dataStructure）
     * - 作为提示词约束注入到 OpenLovable-CN 生成过程
     */
    private Map<String, Object> blueprintFrontendSpec;

    /**
     * 是否启用 Blueprint 模式（可选）
     * 当 blueprintFrontendSpec 不为空时，建议为 true
     */
    private Boolean blueprintModeEnabled;

    // ==================== 语言设置 ====================

    /**
     * 目标语言（可选）
     *
     * 说明：
     * - 用于动态适配生成的网站语言
     * - "zh" 表示中文网站，UI文案使用中文
     * - "en" 表示英文网站，UI文案使用英文
     * - 默认为 "zh"（中文）
     */
    @Builder.Default
    private String language = "zh";

    /**
     * 验证请求参数
     *
     * @return true 如果参数有效
     */
    public boolean isValid() {
        if (userRequirement == null || userRequirement.trim().isEmpty()) {
            return false;
        }

        if (userRequirement.length() < 10) {
            return false; // 需求描述过短
        }

        return true;
    }

    /**
     * 判断是否需要爬取（根据needsCrawling和referenceUrls综合判断）
     *
     * @return true 如果需要爬取
     */
    public boolean shouldCrawl() {
        if (needsCrawling != null) {
            return needsCrawling;
        }

        return referenceUrls != null && !referenceUrls.isEmpty();
    }

    /**
     * 获取第一个参考URL（用于爬取）
     *
     * @return 第一个URL，如果没有则返回null
     */
    public String getPrimaryReferenceUrl() {
        if (referenceUrls == null || referenceUrls.isEmpty()) {
            return null;
        }
        return referenceUrls.get(0);
    }

    /**
     * 构建发送给Open-Lovable的提示词
     *
     * @return 优化后的提示词
     */
    public String buildPrompt() {
        StringBuilder prompt = new StringBuilder();

        // 基础需求
        prompt.append(userRequirement);

        // 如果有参考网站
        if (shouldCrawl()) {
            prompt.append("\n\n参考网站：");
            for (String url : referenceUrls) {
                prompt.append("\n- ").append(url);
            }
        }

        // 如果有定制化需求
        if (customizationRequirement != null && !customizationRequirement.trim().isEmpty()) {
            prompt.append("\n\n定制化要求：\n");
            prompt.append(customizationRequirement);
        }

        // Blueprint 约束注入（可选）
        if (Boolean.TRUE.equals(blueprintModeEnabled)
                && blueprintFrontendSpec != null
                && !blueprintFrontendSpec.isEmpty()) {
            prompt.append("\n\n## Blueprint 前端约束（必须遵守）\n");
            prompt.append("说明：你必须严格遵守以下约束生成前端代码（允许额外优化，但不可违反约束）。\n");
            try {
                String json = new com.fasterxml.jackson.databind.ObjectMapper()
                        .writerWithDefaultPrettyPrinter()
                        .writeValueAsString(blueprintFrontendSpec);
                prompt.append("```json\n").append(json).append("\n```\n");
            } catch (Exception e) {
                // 降级：避免因序列化失败导致生成流程中断
                prompt.append("(Blueprint 约束序列化失败，已降级为字符串) ")
                        .append(blueprintFrontendSpec.toString())
                        .append("\n");
            }
        }

        // === UI Design Standards (New V2.0) ===
        String langName = "en".equalsIgnoreCase(language) ? "English" : "中文";
        prompt.append(String.format(
                """

                        ## 🎨 UI Design Standards (Mandatory)

                        ### 0. Relevance Guard
                        - Follow the user's requirement strictly. Do NOT clone or reference unrelated websites unless the user explicitly provides a URL.

                        ### 1. Visual Requirements
                        - **Language**: All UI text MUST be in **%s**.
                        - **Direction**: Pick a clear, domain-appropriate visual direction; avoid generic SaaS styling.
                        - **Color**: Avoid purple-first palettes. Prefer calm combinations (sage/seafoam + sand + soft coral, or sky + amber + slate).
                        - **Background**: Use layered gradients, soft radial glows, and subtle patterns; avoid flat single-color backgrounds.
                        - **Typography**: Use expressive, non-default fonts; import two Google Fonts (e.g., "Noto Serif SC" + "Noto Sans SC" for Chinese, "Space Grotesk" + "Manrope" for English).

                        ### 2. Implementation Specs (Tailwind CSS)
                        - **MUST** use Tailwind CSS for all styling. Do NOT create custom CSS files unless absolutely necessary.
                        - **Components**:
                          - Cards: `bg-white dark:bg-slate-900 rounded-2xl shadow-sm border border-slate-200 dark:border-slate-800`
                          - Buttons: `bg-emerald-600 text-white hover:bg-emerald-700 rounded-lg shadow-sm transition-all`
                        - **Icons**: Use `lucide-react` (e.g., `<Activity className="w-5 h-5" />`).

                        """,
                langName));

        // === G3 Engine Technical Constraints (CRITICAL) ===
        // 修复: [plugin:vite:import-analysis] Failed to parse source... invalid JS syntax
        prompt.append("\n\n## Technical Constraints (CRITICAL)\n");
        prompt.append("To avoid build errors in the Vite/React environment, you MUST strictly follow these rules:\n");
        prompt.append(
                "1. **File Extensions**: Use `.tsx` for ANY file containing JSX syntax (e.g., React Components, Context Providers, Hooks returning JSX).\n");
        prompt.append(
                "2. **No .js for JSX**: NEVER put JSX code in a `.js` file. This causes 'Failed to parse source for import analysis' errors in Vite.\n");
        prompt.append(
                "3. **TypeScript**: Prefer TypeScript (`.ts`/`.tsx`) for all generated code unless explicitly requested otherwise.\n");

        // === Supabase Integration (V2.0新增：BaaS模式后端集成) ===
        appendSupabaseIntegrationPrompt(prompt);

        return prompt.toString();
    }

    /**
     * 追加 Supabase 集成提示词（V2.0新增）
     *
     * 当 blueprintFrontendSpec 包含 techStack="React+Supabase" 或 supabaseConfig 时，
     * 注入 Supabase SDK 使用指令，确保生成的前端代码能正确调用 Supabase API。
     *
     * @param prompt 提示词构建器
     */
    private void appendSupabaseIntegrationPrompt(StringBuilder prompt) {
        // 检查是否为 Supabase 技术栈
        boolean isSupabaseStack = false;
        String supabaseUrl = null;
        String supabaseAnonKey = null;
        String ddlSchema = null;

        if (blueprintFrontendSpec != null) {
            Object techStack = blueprintFrontendSpec.get("techStack");
            if (techStack != null && techStack.toString().toLowerCase().contains("supabase")) {
                isSupabaseStack = true;
            }

            // 提取 Supabase 配置
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> supabaseConfig = (java.util.Map<String, Object>) blueprintFrontendSpec.get("supabaseConfig");
            if (supabaseConfig != null) {
                supabaseUrl = (String) supabaseConfig.get("projectUrl");
                supabaseAnonKey = (String) supabaseConfig.get("anonKey");
            }

            // 提取 DDL Schema
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> dataSchema = (java.util.Map<String, Object>) blueprintFrontendSpec.get("dataSchema");
            if (dataSchema != null) {
                ddlSchema = (String) dataSchema.get("ddl");
            }
        }

        if (!isSupabaseStack) {
            return;
        }

        prompt.append("\n\n## Supabase Integration (MANDATORY - BaaS Mode)\n");
        prompt.append("This application uses **React + Supabase** architecture (BaaS mode). ");
        prompt.append("You MUST integrate Supabase as the backend. Follow these rules:\n\n");

        prompt.append("### 1. Supabase Client Setup\n");
        prompt.append("Create a `lib/supabase.ts` file with the following structure:\n");
        prompt.append("```typescript\n");
        prompt.append("import { createClient } from '@supabase/supabase-js'\n\n");
        if (supabaseUrl != null && supabaseAnonKey != null) {
            prompt.append("const supabaseUrl = '").append(supabaseUrl).append("'\n");
            prompt.append("const supabaseAnonKey = '").append(supabaseAnonKey).append("'\n");
        } else {
            prompt.append("const supabaseUrl = process.env.NEXT_PUBLIC_SUPABASE_URL!\n");
            prompt.append("const supabaseAnonKey = process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY!\n");
        }
        prompt.append("\nexport const supabase = createClient(supabaseUrl, supabaseAnonKey)\n");
        prompt.append("```\n\n");

        prompt.append("### 2. CRUD Operations Pattern\n");
        prompt.append("Generate type-safe CRUD functions for each entity. Example:\n");
        prompt.append("```typescript\n");
        prompt.append("// lib/api/posts.ts\n");
        prompt.append("import { supabase } from '../supabase'\n");
        prompt.append("import type { Database } from '../database.types'\n\n");
        prompt.append("type Post = Database['public']['Tables']['posts']['Row']\n");
        prompt.append("type PostInsert = Database['public']['Tables']['posts']['Insert']\n\n");
        prompt.append("export async function getPosts() {\n");
        prompt.append("  const { data, error } = await supabase.from('posts').select('*')\n");
        prompt.append("  if (error) throw error\n");
        prompt.append("  return data\n");
        prompt.append("}\n\n");
        prompt.append("export async function createPost(post: PostInsert) {\n");
        prompt.append("  const { data, error } = await supabase.from('posts').insert(post).select().single()\n");
        prompt.append("  if (error) throw error\n");
        prompt.append("  return data\n");
        prompt.append("}\n");
        prompt.append("```\n\n");

        prompt.append("### 3. Authentication (Optional)\n");
        prompt.append("If the application requires user authentication:\n");
        prompt.append("```typescript\n");
        prompt.append("// Sign up\n");
        prompt.append("const { data, error } = await supabase.auth.signUp({ email, password })\n\n");
        prompt.append("// Sign in\n");
        prompt.append("const { data, error } = await supabase.auth.signInWithPassword({ email, password })\n\n");
        prompt.append("// Sign out\n");
        prompt.append("await supabase.auth.signOut()\n\n");
        prompt.append("// Get current user\n");
        prompt.append("const { data: { user } } = await supabase.auth.getUser()\n");
        prompt.append("```\n\n");

        prompt.append("### 4. Critical Rules\n");
        prompt.append("- **DO NOT** generate any Express/Node.js backend code\n");
        prompt.append("- **DO NOT** create custom REST APIs - use Supabase's auto-generated APIs\n");
        prompt.append("- **ALWAYS** handle errors from Supabase calls\n");
        prompt.append("- **ALWAYS** use TypeScript types generated from the database schema\n");
        prompt.append("- **ALWAYS** install `@supabase/supabase-js` as a dependency\n\n");

        // 如果提供了 DDL Schema，注入到提示词中
        if (ddlSchema != null && !ddlSchema.isEmpty()) {
            prompt.append("### 5. Database Schema\n");
            prompt.append("The following tables have been created in Supabase. Generate CRUD operations for each:\n");
            prompt.append("```sql\n").append(ddlSchema).append("\n```\n\n");
        }
    }
}