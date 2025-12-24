package com.ingenio.backend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ingenio.backend.agent.ExecuteAgentFactory;
import com.ingenio.backend.agent.IExecuteAgent;
import com.ingenio.backend.agent.IValidateAgent;
import com.ingenio.backend.agent.PlanAgent;
import com.ingenio.backend.agent.ValidateAgentFactory;
import com.ingenio.backend.agent.dto.PlanResult;
import com.ingenio.backend.agent.dto.ValidateResult;
import com.ingenio.backend.codegen.adapter.AppSpecAdapter;
import com.ingenio.backend.codegen.generator.*;
import com.ingenio.backend.codegen.kuikly.KuiklyUIGenerator;
import com.ingenio.backend.codegen.schema.Entity;
import com.ingenio.backend.common.exception.BusinessException;
import com.ingenio.backend.common.exception.ErrorCode;
import com.ingenio.backend.dto.request.GenerateFullRequest;
import com.ingenio.backend.dto.response.GenerateFullResponse;
import com.ingenio.backend.entity.AppSpecEntity;
import com.ingenio.backend.mapper.AppSpecMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 代码生成服务
 * 负责编排PlanAgent、ExecuteAgent、ValidateAgent的执行流程
 * 实现完整的需求→AppSpec→验证→全栈代码生成流程
 *
 * @author Ingenio Team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GenerateService {

    private final PlanAgent planAgent;
    private final ExecuteAgentFactory executeAgentFactory;
    private final ValidateAgentFactory validateAgentFactory;
    private final AppSpecMapper appSpecMapper;
    private final ObjectMapper objectMapper;
    private final CodePackagingService packagingService;

    // --- 全栈代码生成器 ---
    private final AppSpecAdapter appSpecAdapter;
    private final KuiklyUIGenerator kuiklyUIGenerator;
    private final EntityGenerator entityGenerator;
    private final DTOGenerator dtoGenerator;
    private final ServiceGenerator serviceGenerator;
    private final ControllerGenerator controllerGenerator;
    private final DatabaseSchemaGenerator schemaGenerator;
    private final AICodeGenerator aiCodeGenerator;


    /**
     * 完整生成流程：Plan → Execute → Validate → CodeGen
     *
     * @param request 生成请求
     * @return 生成结果
     * @throws BusinessException 当生成失败时抛出
     */
    @Transactional(rollbackFor = Exception.class)
    public GenerateFullResponse generateFull(GenerateFullRequest request) {
        log.info("开始完整生成流程 - userRequirement: {}", request.getUserRequirement());

        long startTime = System.currentTimeMillis();
        GenerateFullResponse.GenerateFullResponseBuilder responseBuilder = GenerateFullResponse.builder();

        try {
            // Step 1: PlanAgent规划
            log.info("Step 1: PlanAgent开始规划");
            responseBuilder.status("planning");
            PlanResult planResult = planAgent.plan(request.getUserRequirement());
            responseBuilder.planResult(planResult);
            log.info("Step 1: PlanAgent规划完成 - modules: {}, complexityScore: {}",
                    planResult.getModules().size(), planResult.getComplexityScore());

            // Step 2: ExecuteAgent生成AppSpec
            log.info("Step 2: ExecuteAgent开始生成AppSpec");
            responseBuilder.status("executing");
            IExecuteAgent executeAgent = executeAgentFactory.getExecuteAgent();
            Map<String, Object> appSpecJson = executeAgent.execute(planResult);
            log.info("Step 2: ExecuteAgent生成完成 - version: {}, appSpec keys: {}",
                    executeAgent.getVersion(), appSpecJson.keySet());

            // 保存AppSpec到数据库
            AppSpecEntity appSpec = saveAppSpec(request.getUserRequirement(), appSpecJson, planResult);
            responseBuilder.appSpecId(appSpec.getId());

            // Step 3: ValidateAgent验证
            log.info("Step 3: ValidateAgent开始验证");
            responseBuilder.status("validating");
            IValidateAgent validateAgent = validateAgentFactory.getValidateAgent();
            Map<String, Object> validateResultMap = validateAgent.validate(appSpecJson);
            ValidateResult validateResult = objectMapper.convertValue(validateResultMap, ValidateResult.class);
            responseBuilder.validateResult(validateResult);
            responseBuilder.isValid(Boolean.TRUE.equals(validateResult.getIsValid()));
            responseBuilder.qualityScore(validateResult.getQualityScore());
            log.info("Step 3: ValidateAgent验证完成 - version: {}, isValid: {}, qualityScore: {}",
                    validateAgent.getVersion(), validateResult.getIsValid(), validateResult.getQualityScore());

            // 检查验证结果
            if (!request.getSkipValidation() && !Boolean.TRUE.equals(validateResult.getIsValid())) {
                log.warn("AppSpec质量不达标，但在测试模式下继续执行 - qualityScore: {}", validateResult.getQualityScore());
            } else if (validateResult.getQualityScore() != null && validateResult.getQualityScore() < request.getQualityThreshold()) {
                 throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                        String.format("质量评分(%d)低于阈值(%d)，请优化需求后重试",
                                validateResult.getQualityScore(), request.getQualityThreshold()));
            }

            // Step 4: 生成代码（如果需要）
            if (Boolean.TRUE.equals(request.getGeneratePreview())) {
                log.info("Step 4: 开始生成代码");
                responseBuilder.status("generating");

                try {
                    // 生成所有代码文件
                    Map<String, String> generatedFiles = generateAllCodeFiles(
                            planResult,
                            appSpecJson,
                            request
                    );

                    // 打包并上传
                    String projectName = AppSpecAdapter.extractAppName(appSpecJson);
                    String downloadUrl = packagingService.packageAndUpload(
                            generatedFiles,
                            projectName
                    );

                    // 设置响应
                    responseBuilder.codeDownloadUrl(downloadUrl);
                    responseBuilder.generatedFileList(new java.util.ArrayList<>(generatedFiles.keySet()));
                    responseBuilder.codeSummary(buildCodeSummary(generatedFiles, projectName, appSpecJson));

                    log.info("Step 4: 代码生成完成 - totalFiles: {}, url: {}",
                            generatedFiles.size(), downloadUrl);

                } catch (Exception e) {
                    log.error("Step 4: 代码生成失败", e);
                    throw new BusinessException(ErrorCode.CODE_GENERATION_ERROR, "代码生成失败: " + e.getMessage());
                }
            }

            responseBuilder.status("completed");
            log.info("完整生成流程完成 - appSpecId: {}", appSpec.getId());

            long duration = System.currentTimeMillis() - startTime;
            responseBuilder.durationMs(duration);
            responseBuilder.generatedAt(Instant.now());

            return responseBuilder.build();

        } catch (BusinessException e) {
            log.error("生成流程失败 - BusinessException: {}", e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            log.error("生成流程失败 - Exception: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "生成失败: " + e.getMessage());
        }
    }


    private Map<String, String> generateAllCodeFiles(
            PlanResult planResult,
            Map<String, Object> appSpecJson,
            GenerateFullRequest request
    ) {
        // 获取平台类型：WEB（简单网页）、ENTERPRISE（复杂网页）、NATIVE（原生跨端）
        String platform = request.getPlatform() != null ? request.getPlatform() : "ENTERPRISE";
        log.info("代码生成模式: {}", platform);

        return switch (platform.toUpperCase()) {
            case "WEB" -> generateSimpleWebApp(planResult, appSpecJson, request);
            case "ENTERPRISE" -> generateEnterpriseWebApp(planResult, appSpecJson, request);
            case "NATIVE" -> generateNativeMultiPlatformApp(planResult, appSpecJson, request);
            default -> generateEnterpriseWebApp(planResult, appSpecJson, request);
        };
    }

    /**
     * 生成简单网页应用（React + Supabase）
     * 适用于：博客、管理后台、数据看板、预约系统
     *
     * 生成内容：
     * 1. React前端代码（Next.js + Tailwind CSS）
     * 2. Supabase客户端配置（lib/supabase.ts）
     * 3. Supabase数据库Schema（含RLS策略）
     * 4. 环境变量模板（.env.example）
     * 5. 部署说明（README.md）
     */
    private Map<String, String> generateSimpleWebApp(
            PlanResult planResult,
            Map<String, Object> appSpecJson,
            GenerateFullRequest request
    ) {
        Map<String, String> allFiles = new HashMap<>();
        List<Entity> entities = appSpecAdapter.adapt(appSpecJson);
        String appName = AppSpecAdapter.extractAppName(appSpecJson);

        log.info("[简单网页应用] 开始生成 React + Supabase 代码: appName={}, entities={}", appName, entities.size());

        // Step 1: 生成React前端项目结构
        allFiles.put("package.json", generateReactSupabasePackageJson(appName));
        allFiles.put("next.config.js", generateNextConfig());
        allFiles.put("tsconfig.json", generateTsConfig());
        allFiles.put("tailwind.config.js", generateTailwindConfig());
        allFiles.put("postcss.config.js", generatePostCssConfig());

        // Step 2: 生成Supabase客户端配置
        allFiles.put("lib/supabase.ts", generateSupabaseClient());
        allFiles.put("lib/database.types.ts", generateDatabaseTypes(entities));

        // Step 3: 生成页面和组件
        allFiles.put("app/layout.tsx", generateRootLayout(appName));
        allFiles.put("app/page.tsx", generateHomePage(appName, entities));
        allFiles.put("app/globals.css", generateGlobalCss());

        // 为每个实体生成CRUD页面
        for (Entity entity : entities) {
            String entityName = entity.getName();
            String entityNameLower = entityName.toLowerCase();
            allFiles.put("app/" + entityNameLower + "/page.tsx", generateEntityListPage(entity));
            allFiles.put("app/" + entityNameLower + "/[id]/page.tsx", generateEntityDetailPage(entity));
            allFiles.put("components/" + entityName + "Form.tsx", generateEntityForm(entity));
            allFiles.put("components/" + entityName + "Card.tsx", generateEntityCard(entity));
        }

        // Step 4: 生成Supabase数据库配置
        allFiles.put("supabase/config.toml", generateSupabaseConfig(appName));
        allFiles.put("supabase/migrations/001_initial_schema.sql", generateSupabaseSchemaWithRLS(entities));

        // Step 5: 生成环境变量和文档
        allFiles.put(".env.example", generateEnvExample());
        allFiles.put(".env.local.example", generateEnvLocalExample());
        allFiles.put("README.md", generateReadme(appName, entities));
        allFiles.put(".gitignore", generateGitignore());

        log.info("[简单网页应用] 生成完成: totalFiles={}", allFiles.size());
        return allFiles;
    }

    // ==================== React + Supabase 代码生成方法 ====================

    /**
     * 生成React + Supabase项目的package.json
     */
    private String generateReactSupabasePackageJson(String appName) {
        return """
            {
              "name": "%s",
              "version": "0.1.0",
              "private": true,
              "scripts": {
                "dev": "next dev",
                "build": "next build",
                "start": "next start",
                "lint": "next lint",
                "db:push": "supabase db push",
                "db:reset": "supabase db reset",
                "types": "supabase gen types typescript --local > lib/database.types.ts"
              },
              "dependencies": {
                "@supabase/supabase-js": "^2.45.0",
                "@supabase/ssr": "^0.5.0",
                "next": "14.2.0",
                "react": "^18.3.0",
                "react-dom": "^18.3.0",
                "lucide-react": "^0.400.0",
                "clsx": "^2.1.0",
                "tailwind-merge": "^2.3.0"
              },
              "devDependencies": {
                "@types/node": "^20",
                "@types/react": "^18",
                "@types/react-dom": "^18",
                "typescript": "^5",
                "tailwindcss": "^3.4.0",
                "autoprefixer": "^10.4.0",
                "postcss": "^8.4.0",
                "eslint": "^8",
                "eslint-config-next": "14.2.0",
                "supabase": "^1.200.0"
              }
            }
            """.formatted(appName.toLowerCase().replaceAll("[^a-z0-9-]", "-"));
    }

    private String generateNextConfig() {
        return """
            /** @type {import('next').NextConfig} */
            const nextConfig = {
              reactStrictMode: true,
              images: {
                remotePatterns: [
                  {
                    protocol: 'https',
                    hostname: '**.supabase.co',
                  },
                ],
              },
            }

            module.exports = nextConfig
            """;
    }

    private String generateTsConfig() {
        return """
            {
              "compilerOptions": {
                "lib": ["dom", "dom.iterable", "esnext"],
                "allowJs": true,
                "skipLibCheck": true,
                "strict": true,
                "noEmit": true,
                "esModuleInterop": true,
                "module": "esnext",
                "moduleResolution": "bundler",
                "resolveJsonModule": true,
                "isolatedModules": true,
                "jsx": "preserve",
                "incremental": true,
                "plugins": [{ "name": "next" }],
                "paths": {
                  "@/*": ["./*"]
                }
              },
              "include": ["next-env.d.ts", "**/*.ts", "**/*.tsx", ".next/types/**/*.ts"],
              "exclude": ["node_modules"]
            }
            """;
    }

    private String generateTailwindConfig() {
        return """
            /** @type {import('tailwindcss').Config} */
            module.exports = {
              content: [
                './pages/**/*.{js,ts,jsx,tsx,mdx}',
                './components/**/*.{js,ts,jsx,tsx,mdx}',
                './app/**/*.{js,ts,jsx,tsx,mdx}',
              ],
              theme: {
                extend: {},
              },
              plugins: [],
            }
            """;
    }

    private String generatePostCssConfig() {
        return """
            module.exports = {
              plugins: {
                tailwindcss: {},
                autoprefixer: {},
              },
            }
            """;
    }

    /**
     * 生成Supabase客户端配置
     */
    private String generateSupabaseClient() {
        return """
            import { createClient } from '@supabase/supabase-js'
            import { Database } from './database.types'

            /**
             * Supabase客户端配置
             *
             * 使用方法：
             * 1. 在.env.local中设置NEXT_PUBLIC_SUPABASE_URL和NEXT_PUBLIC_SUPABASE_ANON_KEY
             * 2. 导入supabase客户端：import { supabase } from '@/lib/supabase'
             * 3. 使用CRUD方法：await supabase.from('table').select()
             */

            const supabaseUrl = process.env.NEXT_PUBLIC_SUPABASE_URL!
            const supabaseAnonKey = process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY!

            if (!supabaseUrl || !supabaseAnonKey) {
              throw new Error('Missing Supabase environment variables. Check .env.local file.')
            }

            /**
             * Supabase客户端实例（带类型支持）
             */
            export const supabase = createClient<Database>(supabaseUrl, supabaseAnonKey, {
              auth: {
                persistSession: true,
                autoRefreshToken: true,
              },
            })

            /**
             * 服务端Supabase客户端（使用service_role密钥，绕过RLS）
             * 仅在服务端API路由中使用，禁止在客户端使用！
             */
            export function createServiceClient() {
              const serviceRoleKey = process.env.SUPABASE_SERVICE_ROLE_KEY
              if (!serviceRoleKey) {
                throw new Error('Missing SUPABASE_SERVICE_ROLE_KEY')
              }
              return createClient<Database>(supabaseUrl, serviceRoleKey, {
                auth: {
                  persistSession: false,
                  autoRefreshToken: false,
                },
              })
            }
            """;
    }

    /**
     * 生成数据库类型定义
     */
    private String generateDatabaseTypes(List<Entity> entities) {
        StringBuilder sb = new StringBuilder();
        sb.append("/**\n");
        sb.append(" * Supabase数据库类型定义\n");
        sb.append(" * 自动生成 - 请勿手动修改\n");
        sb.append(" * 运行 `npm run types` 从Supabase重新生成\n");
        sb.append(" */\n\n");

        sb.append("export type Json = string | number | boolean | null | { [key: string]: Json } | Json[]\n\n");

        sb.append("export interface Database {\n");
        sb.append("  public: {\n");
        sb.append("    Tables: {\n");

        for (Entity entity : entities) {
            String tableName = entity.getName().toLowerCase() + "s";
            sb.append("      ").append(tableName).append(": {\n");
            sb.append("        Row: {\n");
            sb.append("          id: string\n");
            sb.append("          created_at: string\n");
            sb.append("          updated_at: string\n");
            for (var field : entity.getFields()) {
                if (!"id".equalsIgnoreCase(field.getName())) {
                    sb.append("          ").append(field.getName()).append(": ")
                      .append(mapToTypeScriptType(field.getType().name())).append("\n");
                }
            }
            sb.append("        }\n");
            sb.append("        Insert: Omit<").append(tableName).append("['Row'], 'id' | 'created_at' | 'updated_at'>\n");
            sb.append("        Update: Partial<").append(tableName).append("['Insert']>\n");
            sb.append("      }\n");
        }

        sb.append("    }\n");
        sb.append("  }\n");
        sb.append("}\n");

        return sb.toString();
    }

    private String mapToTypeScriptType(String fieldType) {
        return switch (fieldType) {
            case "TEXT", "VARCHAR", "UUID" -> "string";
            case "INTEGER", "BIGINT", "DECIMAL" -> "number";
            case "BOOLEAN" -> "boolean";
            case "TIMESTAMP", "TIMESTAMPTZ", "DATE" -> "string";
            case "JSONB", "JSON" -> "Json";
            default -> "string";
        };
    }

    private String generateRootLayout(String appName) {
        return """
            import type { Metadata } from 'next'
            import { Inter } from 'next/font/google'
            import './globals.css'

            const inter = Inter({ subsets: ['latin'] })

            export const metadata: Metadata = {
              title: '%s',
              description: 'Generated by Ingenio with React + Supabase',
            }

            export default function RootLayout({
              children,
            }: {
              children: React.ReactNode
            }) {
              return (
                <html lang="zh-CN">
                  <body className={inter.className}>
                    <div className="min-h-screen bg-gray-50">
                      <nav className="bg-white shadow-sm border-b">
                        <div className="max-w-7xl mx-auto px-4 py-4">
                          <h1 className="text-xl font-bold text-gray-900">%s</h1>
                        </div>
                      </nav>
                      <main className="max-w-7xl mx-auto px-4 py-8">
                        {children}
                      </main>
                    </div>
                  </body>
                </html>
              )
            }
            """.formatted(appName, appName);
    }

    private String generateHomePage(String appName, List<Entity> entities) {
        StringBuilder entityLinks = new StringBuilder();
        for (Entity entity : entities) {
            String name = entity.getName();
            String nameLower = name.toLowerCase();
            entityLinks.append("""
                        <a href="/%s" className="block p-6 bg-white rounded-lg shadow hover:shadow-md transition-shadow">
                          <h3 className="text-lg font-semibold text-gray-900">%s管理</h3>
                          <p className="mt-2 text-gray-600">%s</p>
                        </a>
                """.formatted(nameLower, name, entity.getDescription() != null ? entity.getDescription() : "查看和管理" + name));
        }

        return """
            export default function Home() {
              return (
                <div className="space-y-8">
                  <div className="text-center">
                    <h1 className="text-3xl font-bold text-gray-900">欢迎使用 %s</h1>
                    <p className="mt-2 text-gray-600">使用 React + Supabase 构建</p>
                  </div>

                  <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
            %s
                  </div>
                </div>
              )
            }
            """.formatted(appName, entityLinks);
    }

    private String generateGlobalCss() {
        return """
            @tailwind base;
            @tailwind components;
            @tailwind utilities;

            :root {
              --foreground-rgb: 0, 0, 0;
              --background-start-rgb: 250, 250, 250;
              --background-end-rgb: 255, 255, 255;
            }

            body {
              color: rgb(var(--foreground-rgb));
              background: linear-gradient(
                to bottom,
                rgb(var(--background-start-rgb)),
                rgb(var(--background-end-rgb))
              );
            }
            """;
    }

    private String generateEntityListPage(Entity entity) {
        String name = entity.getName();
        String nameLower = name.toLowerCase();
        String tableName = nameLower + "s";

        return """
            'use client'

            import { useEffect, useState } from 'react'
            import { supabase } from '@/lib/supabase'
            import { %sCard } from '@/components/%sCard'
            import { %sForm } from '@/components/%sForm'
            import { Plus } from 'lucide-react'

            export default function %sListPage() {
              const [items, setItems] = useState<any[]>([])
              const [loading, setLoading] = useState(true)
              const [showForm, setShowForm] = useState(false)

              useEffect(() => {
                fetchItems()
              }, [])

              async function fetchItems() {
                setLoading(true)
                const { data, error } = await supabase
                  .from('%s')
                  .select('*')
                  .order('created_at', { ascending: false })

                if (error) {
                  console.error('Error fetching %s:', error)
                } else {
                  setItems(data || [])
                }
                setLoading(false)
              }

              async function handleCreate(formData: any) {
                const { error } = await supabase.from('%s').insert(formData)
                if (!error) {
                  setShowForm(false)
                  fetchItems()
                }
              }

              async function handleDelete(id: string) {
                if (!confirm('确定要删除吗？')) return
                const { error } = await supabase.from('%s').delete().eq('id', id)
                if (!error) fetchItems()
              }

              if (loading) return <div className="text-center py-8">加载中...</div>

              return (
                <div className="space-y-6">
                  <div className="flex justify-between items-center">
                    <h1 className="text-2xl font-bold">%s管理</h1>
                    <button
                      onClick={() => setShowForm(true)}
                      className="flex items-center gap-2 bg-blue-600 text-white px-4 py-2 rounded-lg hover:bg-blue-700"
                    >
                      <Plus size={20} /> 新建
                    </button>
                  </div>

                  {showForm && (
                    <div className="bg-white p-6 rounded-lg shadow">
                      <%sForm onSubmit={handleCreate} onCancel={() => setShowForm(false)} />
                    </div>
                  )}

                  <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
                    {items.map((item) => (
                      <%sCard key={item.id} data={item} onDelete={() => handleDelete(item.id)} />
                    ))}
                  </div>

                  {items.length === 0 && !loading && (
                    <div className="text-center py-12 text-gray-500">
                      暂无数据，点击"新建"添加第一条记录
                    </div>
                  )}
                </div>
              )
            }
            """.formatted(name, name, name, name, name, tableName, nameLower, tableName, tableName, name, name, name);
    }

    private String generateEntityDetailPage(Entity entity) {
        String name = entity.getName();
        String nameLower = name.toLowerCase();
        String tableName = nameLower + "s";

        return """
            'use client'

            import { useEffect, useState } from 'react'
            import { useParams, useRouter } from 'next/navigation'
            import { supabase } from '@/lib/supabase'
            import { %sForm } from '@/components/%sForm'
            import { ArrowLeft } from 'lucide-react'

            export default function %sDetailPage() {
              const params = useParams()
              const router = useRouter()
              const [item, setItem] = useState<any>(null)
              const [loading, setLoading] = useState(true)
              const [editing, setEditing] = useState(false)

              useEffect(() => {
                fetchItem()
              }, [params.id])

              async function fetchItem() {
                const { data, error } = await supabase
                  .from('%s')
                  .select('*')
                  .eq('id', params.id)
                  .single()

                if (error) {
                  console.error('Error:', error)
                } else {
                  setItem(data)
                }
                setLoading(false)
              }

              async function handleUpdate(formData: any) {
                const { error } = await supabase
                  .from('%s')
                  .update(formData)
                  .eq('id', params.id)

                if (!error) {
                  setEditing(false)
                  fetchItem()
                }
              }

              if (loading) return <div className="text-center py-8">加载中...</div>
              if (!item) return <div className="text-center py-8">未找到数据</div>

              return (
                <div className="space-y-6">
                  <button
                    onClick={() => router.back()}
                    className="flex items-center gap-2 text-gray-600 hover:text-gray-900"
                  >
                    <ArrowLeft size={20} /> 返回
                  </button>

                  <div className="bg-white p-6 rounded-lg shadow">
                    {editing ? (
                      <%sForm
                        initialData={item}
                        onSubmit={handleUpdate}
                        onCancel={() => setEditing(false)}
                      />
                    ) : (
                      <div className="space-y-4">
                        <div className="flex justify-between">
                          <h1 className="text-2xl font-bold">%s详情</h1>
                          <button
                            onClick={() => setEditing(true)}
                            className="bg-blue-600 text-white px-4 py-2 rounded-lg hover:bg-blue-700"
                          >
                            编辑
                          </button>
                        </div>
                        <pre className="bg-gray-50 p-4 rounded overflow-auto">
                          {JSON.stringify(item, null, 2)}
                        </pre>
                      </div>
                    )}
                  </div>
                </div>
              )
            }
            """.formatted(name, name, name, tableName, tableName, name, name);
    }

    private String generateEntityForm(Entity entity) {
        String name = entity.getName();
        StringBuilder fields = new StringBuilder();

        for (var field : entity.getFields()) {
            if (!"id".equalsIgnoreCase(field.getName())) {
                String fieldName = field.getName();
                String inputType = getInputType(field.getType().name());
                fields.append("""
                          <div>
                            <label className="block text-sm font-medium text-gray-700 mb-1">%s</label>
                            <input
                              type="%s"
                              name="%s"
                              defaultValue={initialData?.%s || ''}
                              className="w-full px-3 py-2 border rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                              %s
                            />
                          </div>
                    """.formatted(
                        field.getDescription() != null ? field.getDescription() : fieldName,
                        inputType,
                        fieldName,
                        fieldName,
                        field.isNullable() ? "" : "required"
                    ));
            }
        }

        return """
            'use client'

            import { FormEvent } from 'react'

            interface %sFormProps {
              initialData?: any
              onSubmit: (data: any) => void
              onCancel: () => void
            }

            export function %sForm({ initialData, onSubmit, onCancel }: %sFormProps) {
              function handleSubmit(e: FormEvent<HTMLFormElement>) {
                e.preventDefault()
                const formData = new FormData(e.currentTarget)
                const data = Object.fromEntries(formData.entries())
                onSubmit(data)
              }

              return (
                <form onSubmit={handleSubmit} className="space-y-4">
            %s
                  <div className="flex gap-3 pt-4">
                    <button
                      type="submit"
                      className="flex-1 bg-blue-600 text-white py-2 rounded-lg hover:bg-blue-700"
                    >
                      {initialData ? '保存' : '创建'}
                    </button>
                    <button
                      type="button"
                      onClick={onCancel}
                      className="flex-1 bg-gray-200 text-gray-700 py-2 rounded-lg hover:bg-gray-300"
                    >
                      取消
                    </button>
                  </div>
                </form>
              )
            }
            """.formatted(name, name, name, fields);
    }

    private String getInputType(String fieldType) {
        return switch (fieldType) {
            case "INTEGER", "BIGINT", "DECIMAL" -> "number";
            case "BOOLEAN" -> "checkbox";
            case "DATE" -> "date";
            case "TIMESTAMP", "TIMESTAMPTZ" -> "datetime-local";
            default -> "text";
        };
    }

    private String generateEntityCard(Entity entity) {
        String name = entity.getName();
        String nameLower = name.toLowerCase();

        return """
            'use client'

            import Link from 'next/link'
            import { Trash2, ExternalLink } from 'lucide-react'

            interface %sCardProps {
              data: any
              onDelete: () => void
            }

            export function %sCard({ data, onDelete }: %sCardProps) {
              return (
                <div className="bg-white p-4 rounded-lg shadow hover:shadow-md transition-shadow">
                  <div className="flex justify-between items-start">
                    <Link href={`/%s/${data.id}`} className="flex-1">
                      <h3 className="font-semibold text-gray-900 hover:text-blue-600">
                        {data.id.slice(0, 8)}...
                      </h3>
                      <p className="text-sm text-gray-500 mt-1">
                        创建于 {new Date(data.created_at).toLocaleDateString('zh-CN')}
                      </p>
                    </Link>
                    <div className="flex gap-2">
                      <Link
                        href={`/%s/${data.id}`}
                        className="p-2 text-gray-400 hover:text-blue-600"
                      >
                        <ExternalLink size={16} />
                      </Link>
                      <button
                        onClick={onDelete}
                        className="p-2 text-gray-400 hover:text-red-600"
                      >
                        <Trash2 size={16} />
                      </button>
                    </div>
                  </div>
                </div>
              )
            }
            """.formatted(name, name, name, nameLower, nameLower);
    }

    /**
     * 生成增强版Supabase Schema（含RLS策略）
     */
    private String generateSupabaseSchemaWithRLS(List<Entity> entities) {
        StringBuilder sb = new StringBuilder();
        sb.append("-- ==========================================\n");
        sb.append("-- Supabase数据库迁移脚本\n");
        sb.append("-- 生成工具: Ingenio 简单网页应用生成器\n");
        sb.append("-- ==========================================\n\n");

        // 启用UUID扩展
        sb.append("-- 启用UUID扩展\n");
        sb.append("CREATE EXTENSION IF NOT EXISTS \"uuid-ossp\";\n\n");

        // 创建更新触发器函数
        sb.append("-- 创建更新触发器函数\n");
        sb.append("""
            CREATE OR REPLACE FUNCTION update_updated_at_column()
            RETURNS TRIGGER AS $$
            BEGIN
                NEW.updated_at = NOW();
                RETURN NEW;
            END;
            $$ language 'plpgsql';

            """);

        // 为每个实体生成表和RLS策略
        for (Entity entity : entities) {
            String tableName = entity.getName().toLowerCase() + "s";

            sb.append("-- ==========================================\n");
            sb.append("-- Table: ").append(tableName).append("\n");
            sb.append("-- ==========================================\n");

            sb.append("CREATE TABLE IF NOT EXISTS ").append(tableName).append(" (\n");
            sb.append("    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),\n");

            if (entity.getFields() != null) {
                for (var field : entity.getFields()) {
                    if (!"id".equalsIgnoreCase(field.getName())) {
                        sb.append("    ").append(field.getName()).append(" ");
                        sb.append(mapToPostgresType(field.getType().name()));
                        if (!field.isNullable()) sb.append(" NOT NULL");
                        sb.append(",\n");
                    }
                }
            }

            sb.append("    created_at TIMESTAMPTZ DEFAULT NOW(),\n");
            sb.append("    updated_at TIMESTAMPTZ DEFAULT NOW()\n");
            sb.append(");\n\n");

            // 创建更新触发器
            sb.append("CREATE TRIGGER update_").append(tableName).append("_updated_at\n");
            sb.append("    BEFORE UPDATE ON ").append(tableName).append("\n");
            sb.append("    FOR EACH ROW\n");
            sb.append("    EXECUTE FUNCTION update_updated_at_column();\n\n");

            // 启用RLS
            sb.append("-- 启用行级安全\n");
            sb.append("ALTER TABLE ").append(tableName).append(" ENABLE ROW LEVEL SECURITY;\n\n");

            // 创建RLS策略（简单版：允许所有操作，生产环境需要细化）
            sb.append("-- RLS策略：允许所有认证用户读取\n");
            sb.append("CREATE POLICY \"Allow public read\" ON ").append(tableName).append("\n");
            sb.append("    FOR SELECT USING (true);\n\n");

            sb.append("-- RLS策略：允许认证用户创建\n");
            sb.append("CREATE POLICY \"Allow authenticated insert\" ON ").append(tableName).append("\n");
            sb.append("    FOR INSERT WITH CHECK (true);\n\n");

            sb.append("-- RLS策略：允许认证用户更新\n");
            sb.append("CREATE POLICY \"Allow authenticated update\" ON ").append(tableName).append("\n");
            sb.append("    FOR UPDATE USING (true);\n\n");

            sb.append("-- RLS策略：允许认证用户删除\n");
            sb.append("CREATE POLICY \"Allow authenticated delete\" ON ").append(tableName).append("\n");
            sb.append("    FOR DELETE USING (true);\n\n");
        }

        sb.append("-- ==========================================\n");
        sb.append("-- 迁移脚本结束\n");
        sb.append("-- ==========================================\n");

        return sb.toString();
    }

    private String generateEnvExample() {
        return """
            # Supabase配置（从Supabase Dashboard获取）
            NEXT_PUBLIC_SUPABASE_URL=https://your-project.supabase.co
            NEXT_PUBLIC_SUPABASE_ANON_KEY=your-anon-key

            # 服务端密钥（仅在服务端使用，禁止暴露给客户端）
            SUPABASE_SERVICE_ROLE_KEY=your-service-role-key
            """;
    }

    private String generateEnvLocalExample() {
        return """
            # 本地开发环境配置
            # 复制此文件为 .env.local 并填入真实值

            # Supabase配置（从 https://supabase.com/dashboard 获取）
            NEXT_PUBLIC_SUPABASE_URL=https://your-project-id.supabase.co
            NEXT_PUBLIC_SUPABASE_ANON_KEY=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...

            # 服务端密钥（用于服务端API，绕过RLS）
            # ⚠️ 禁止在客户端代码中使用！
            SUPABASE_SERVICE_ROLE_KEY=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
            """;
    }

    private String generateReadme(String appName, List<Entity> entities) {
        StringBuilder entityList = new StringBuilder();
        for (Entity entity : entities) {
            entityList.append("- **").append(entity.getName()).append("**: ")
                      .append(entity.getDescription() != null ? entity.getDescription() : "").append("\n");
        }

        return """
            # %s

            使用 Ingenio 自动生成的 React + Supabase 应用。

            ## 技术栈

            - **前端**: Next.js 14 + React 18 + Tailwind CSS
            - **后端**: Supabase (PostgreSQL + PostgREST + Auth)
            - **类型**: TypeScript

            ## 数据模型

            %s

            ## 快速开始

            ### 1. 安装依赖

            ```bash
            npm install
            ```

            ### 2. 配置Supabase

            1. 前往 [Supabase Dashboard](https://supabase.com/dashboard) 创建项目
            2. 复制 `.env.local.example` 为 `.env.local`
            3. 填入你的Supabase项目URL和密钥

            ```bash
            cp .env.local.example .env.local
            ```

            ### 3. 初始化数据库

            在Supabase Dashboard的SQL编辑器中执行 `supabase/migrations/001_initial_schema.sql`

            ### 4. 启动开发服务器

            ```bash
            npm run dev
            ```

            访问 http://localhost:3000

            ## 项目结构

            ```
            .
            ├── app/                    # Next.js App Router页面
            │   ├── layout.tsx          # 根布局
            │   ├── page.tsx            # 首页
            │   └── [entity]/           # 实体CRUD页面
            ├── components/             # React组件
            ├── lib/
            │   ├── supabase.ts         # Supabase客户端
            │   └── database.types.ts   # 数据库类型定义
            ├── supabase/
            │   ├── config.toml         # Supabase配置
            │   └── migrations/         # 数据库迁移脚本
            └── .env.local              # 环境变量（不提交）
            ```

            ## 部署

            ### Vercel部署（推荐）

            1. 推送代码到GitHub
            2. 在Vercel中导入项目
            3. 配置环境变量
            4. 部署

            ## 生成信息

            - 生成工具: Ingenio
            - 生成模式: 简单网页应用 (React + Supabase)
            """.formatted(appName, entityList);
    }

    private String generateGitignore() {
        return """
            # Dependencies
            node_modules/
            .pnp
            .pnp.js

            # Testing
            coverage/

            # Next.js
            .next/
            out/
            build/

            # Misc
            .DS_Store
            *.pem

            # Debug
            npm-debug.log*
            yarn-debug.log*
            yarn-error.log*

            # Local env files
            .env
            .env.local
            .env.development.local
            .env.test.local
            .env.production.local

            # Vercel
            .vercel

            # TypeScript
            *.tsbuildinfo
            next-env.d.ts

            # Supabase
            supabase/.branches
            supabase/.temp
            """;
    }

    /**
     * 生成复杂网页应用（React + Spring Boot）
     * 适用于：电商平台、企业ERP、在线教育、多租户SaaS
     */
    private Map<String, String> generateEnterpriseWebApp(
            PlanResult planResult,
            Map<String, Object> appSpecJson,
            GenerateFullRequest request
    ) {
        // 原有的generateFullStackWebApp逻辑
        return generateFullStackWebApp(planResult, appSpecJson, request);
    }

    /**
     * 生成原生跨端应用（KuiklyUI + Spring Boot）
     * 适用于：需要相机/GPS/蓝牙等原生能力的应用
     * 平台支持：Android、iOS、HarmonyOS、Web
     */
    private Map<String, String> generateNativeMultiPlatformApp(
            PlanResult planResult,
            Map<String, Object> appSpecJson,
            GenerateFullRequest request
    ) {
        Map<String, String> allFiles = new HashMap<>();
        List<Entity> entities = appSpecAdapter.adapt(appSpecJson);
        String appName = AppSpecAdapter.extractAppName(appSpecJson);
        String packageName = AppSpecAdapter.extractPackageName(appSpecJson, request);

        log.info("[原生跨端应用] 开始生成 KuiklyUI + Spring Boot 全栈代码...");

        // Step 1: 生成KuiklyUI多端代码（共享模块 + 各平台壳）
        KuiklyUIGenerator.MultiPlatformResult uiResult = kuiklyUIGenerator.generateMultiPlatform(entities, appName);
        if (uiResult.isSuccess()) {
            // 添加共享模块代码
            if (uiResult.getPlatformCodes().containsKey(KuiklyUIGenerator.Platform.SHARED)) {
                Map<String, String> sharedFiles = uiResult.getPlatformCodes().get(KuiklyUIGenerator.Platform.SHARED).getFiles();
                allFiles.putAll(sharedFiles);
                log.info("[原生跨端应用] KuiklyUI共享模块: {}个文件", sharedFiles.size());
            }

            // 添加Android壳
            if (uiResult.getPlatformCodes().containsKey(KuiklyUIGenerator.Platform.ANDROID)) {
                Map<String, String> androidFiles = uiResult.getPlatformCodes().get(KuiklyUIGenerator.Platform.ANDROID).getFiles();
                allFiles.putAll(androidFiles);
                log.info("[原生跨端应用] Android壳: {}个文件", androidFiles.size());
            }

            // 添加iOS壳
            if (uiResult.getPlatformCodes().containsKey(KuiklyUIGenerator.Platform.IOS)) {
                Map<String, String> iosFiles = uiResult.getPlatformCodes().get(KuiklyUIGenerator.Platform.IOS).getFiles();
                allFiles.putAll(iosFiles);
                log.info("[原生跨端应用] iOS壳: {}个文件", iosFiles.size());
            }

            // 添加HarmonyOS壳
            if (uiResult.getPlatformCodes().containsKey(KuiklyUIGenerator.Platform.HARMONYOS)) {
                Map<String, String> ohosFiles = uiResult.getPlatformCodes().get(KuiklyUIGenerator.Platform.HARMONYOS).getFiles();
                allFiles.putAll(ohosFiles);
                log.info("[原生跨端应用] HarmonyOS壳: {}个文件", ohosFiles.size());
            }

            // 添加Web壳
            if (uiResult.getPlatformCodes().containsKey(KuiklyUIGenerator.Platform.WEB)) {
                Map<String, String> webFiles = uiResult.getPlatformCodes().get(KuiklyUIGenerator.Platform.WEB).getFiles();
                allFiles.putAll(webFiles);
                log.info("[原生跨端应用] Web壳: {}个文件", webFiles.size());
            }
        } else {
            log.error("[原生跨端应用] KuiklyUI代码生成失败: {}", uiResult.getErrorMessage());
            throw new BusinessException(ErrorCode.CODE_GENERATION_ERROR, "KuiklyUI代码生成失败: " + uiResult.getErrorMessage());
        }

        // Step 2: 生成Spring Boot后端代码
        log.info("[原生跨端应用] 开始生成Spring Boot后端代码...");
        Map<String, String> backendFiles = new HashMap<>();
        entities.forEach(entity -> {
            backendFiles.put("src/main/java/" + packageToPath(packageName) + "/model/" + entity.getName() + ".java", entityGenerator.generate(entity));
            backendFiles.putAll(dtoGenerator.generateAll(entity));
            backendFiles.put("src/main/java/" + packageToPath(packageName) + "/service/" + "I" + entity.getName() + "Service.java", serviceGenerator.generateInterface(entity));
            backendFiles.put("src/main/java/" + packageToPath(packageName) + "/service/impl/" + entity.getName() + "ServiceImpl.java", serviceGenerator.generateImplementation(entity));
            backendFiles.put("src/main/java/" + packageToPath(packageName) + "/controller/" + entity.getName() + "Controller.java", controllerGenerator.generate(entity));
        });
        allFiles.putAll(prefixMapKeys(backendFiles, "backend/"));
        log.info("[原生跨端应用] 后端代码生成完成: {}个文件", backendFiles.size());

        // Step 3: 生成数据库Schema
        com.ingenio.backend.codegen.generator.DatabaseSchemaGenerator.DatabaseSchemaResult schemaResult = schemaGenerator.generate(request.getUserRequirement());
        allFiles.put("docs/schema.sql", schemaResult.getMigrationSQL());

        // Step 4: 生成项目配置
        allFiles.put("backend/pom.xml", generatePomXml(appName, packageName));
        allFiles.put("settings.gradle.kts", generateKmpSettingsGradle(appName));
        allFiles.put("build.gradle.kts", generateKmpRootBuildGradle());

        log.info("[原生跨端应用] 全栈代码生成完成: totalFiles={}", allFiles.size());
        return allFiles;
    }

    private String generateSupabaseConfig(String appName) {
        return """
            [api]
            enabled = true
            port = 54321
            schemas = ["public"]

            [db]
            port = 54322

            [studio]
            enabled = true
            port = 54323

            [analytics]
            enabled = false
            """;
    }

    private String generateSupabaseSchema(List<Entity> entities) {
        StringBuilder sb = new StringBuilder();
        sb.append("-- Supabase Schema for ").append(entities.size()).append(" entities\n\n");

        for (Entity entity : entities) {
            sb.append("CREATE TABLE IF NOT EXISTS ").append(entity.getName().toLowerCase()).append("s (\n");
            sb.append("    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),\n");

            if (entity.getFields() != null) {
                for (var field : entity.getFields()) {
                    if (!"id".equalsIgnoreCase(field.getName())) {
                        sb.append("    ").append(field.getName()).append(" ");
                        sb.append(mapToPostgresType(field.getType().name()));
                        sb.append(",\n");
                    }
                }
            }

            sb.append("    created_at TIMESTAMPTZ DEFAULT NOW(),\n");
            sb.append("    updated_at TIMESTAMPTZ DEFAULT NOW()\n");
            sb.append(");\n\n");
        }

        return sb.toString();
    }

    private String mapToPostgresType(String fieldType) {
        return switch (fieldType) {
            case "TEXT", "VARCHAR" -> "TEXT";
            case "INTEGER" -> "INTEGER";
            case "BIGINT" -> "BIGINT";
            case "DECIMAL" -> "DECIMAL(10,2)";
            case "BOOLEAN" -> "BOOLEAN";
            case "TIMESTAMP", "DATE" -> "TIMESTAMPTZ";
            case "UUID" -> "UUID";
            case "JSONB", "JSON" -> "JSONB";
            default -> "TEXT";
        };
    }

    private String generateKmpSettingsGradle(String appName) {
        return """
            rootProject.name = "%s"

            pluginManagement {
                repositories {
                    google()
                    mavenCentral()
                    gradlePluginPortal()
                }
            }

            dependencyResolutionManagement {
                repositories {
                    google()
                    mavenCentral()
                }
            }

            include(":shared")
            include(":androidApp")
            """.formatted(appName);
    }

    private String generateKmpRootBuildGradle() {
        return """
            plugins {
                alias(libs.plugins.androidApplication) apply false
                alias(libs.plugins.androidLibrary) apply false
                alias(libs.plugins.kotlinAndroid) apply false
                alias(libs.plugins.kotlinMultiplatform) apply false
            }
            """;
    }
    
    private Map<String, String> generateFullStackWebApp(
        PlanResult planResult,
        Map<String, Object> appSpecJson,
        GenerateFullRequest request
    ) {
        Map<String, String> allFiles = new HashMap<>();

        // Step 1: 适配AppSpec为内部统一的Entity模型
        List<Entity> entities = appSpecAdapter.adapt(appSpecJson);
        if (entities.isEmpty()) {
            log.warn("AppSpec中未发现有效实体，代码生成将跳过业务代码部分。");
        }
        String appName = AppSpecAdapter.extractAppName(appSpecJson);
        String packageName = AppSpecAdapter.extractPackageName(appSpecJson, request);

        // Step 2: 生成后端代码
        log.info("开始生成后端代码 (Spring Boot)...");
        Map<String, String> backendFiles = new HashMap<>();
        entities.forEach(entity -> {
            backendFiles.put("src/main/java/" + packageToPath(packageName) + "/model/" + entity.getName() + ".java", entityGenerator.generate(entity));
            backendFiles.putAll(dtoGenerator.generateAll(entity));
            backendFiles.put("src/main/java/" + packageToPath(packageName) + "/service/" + "I" + entity.getName() + "Service.java", serviceGenerator.generateInterface(entity));
            backendFiles.put("src/main/java/" + packageToPath(packageName) + "/service/impl/" + entity.getName() + "ServiceImpl.java", serviceGenerator.generateImplementation(entity));
            backendFiles.put("src/main/java/" + packageToPath(packageName) + "/controller/" + entity.getName() + "Controller.java", controllerGenerator.generate(entity));
        });
        allFiles.putAll(prefixMapKeys(backendFiles, "backend/"));
        log.info("后端代码生成完成: {}个文件", backendFiles.size());

        // Step 3: 生成前端代码
        log.info("开始生成前端代码 (React/Next.js)...");
        KuiklyUIGenerator.MultiPlatformResult uiResult = kuiklyUIGenerator.generateMultiPlatform(entities, appName);
        if (uiResult.isSuccess() && uiResult.getPlatformCodes().containsKey(KuiklyUIGenerator.Platform.WEB)) {
            Map<String, String> webFiles = uiResult.getPlatformCodes().get(KuiklyUIGenerator.Platform.WEB).getFiles();
            allFiles.putAll(prefixMapKeys(webFiles, "frontend/"));
            log.info("前端代码生成完成: {}个文件", webFiles.size());
        } else {
            log.error("前端代码生成失败: {}", uiResult.getErrorMessage());
            throw new BusinessException(ErrorCode.CODE_GENERATION_ERROR, "前端代码生成失败: " + uiResult.getErrorMessage());
        }

        // Step 4: 生成数据库Schema
        log.info("开始生成数据库Schema (SQL)...");
        com.ingenio.backend.codegen.generator.DatabaseSchemaGenerator.DatabaseSchemaResult schemaResult = schemaGenerator.generate(request.getUserRequirement());
        String sqlSchema = schemaResult.getMigrationSQL();
        allFiles.put("docs/schema.sql", sqlSchema);
        log.info("数据库Schema生成完成");
        
        // Step 5: 生成项目配置文件
        allFiles.put("backend/pom.xml", generatePomXml(appName, packageName));
        allFiles.put("frontend/package.json", generatePackageJson(appName));

        log.info("所有代码文件生成完成: totalFiles={}", allFiles.size());
        return allFiles;
    }

    private String generatePomXml(String appName, String packageName) {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                     xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
                <modelVersion>4.0.0</modelVersion>
                <parent>
                    <groupId>org.springframework.boot</groupId>
                    <artifactId>spring-boot-starter-parent</artifactId>
                    <version>3.4.0</version>
                    <relativePath/> <!-- lookup parent from repository -->
                </parent>
                <groupId>%s</groupId>
                <artifactId>%s</artifactId>
                <version>0.0.1-SNAPSHOT</version>
                <name>%s</name>
                <description>Generated by Ingenio</description>
                <properties>
                    <java.version>17</java.version>
                </properties>
                <dependencies>
                    <dependency>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-starter-web</artifactId>
                    </dependency>
                    <dependency>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-starter-data-jpa</artifactId>
                    </dependency>
                    <dependency>
                        <groupId>org.projectlombok</groupId>
                        <artifactId>lombok</artifactId>
                        <optional>true</optional>
                    </dependency>
                    <dependency>
                        <groupId>com.h2database</groupId>
                        <artifactId>h2</artifactId>
                        <scope>runtime</scope>
                    </dependency>
                    <dependency>
                        <groupId>org.springdoc</groupId>
                        <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
                        <version>2.3.0</version>
                    </dependency>
                    <dependency>
                        <groupId>cn.dev33</groupId>
                        <artifactId>sa-token-spring-boot3-starter</artifactId>
                        <version>1.37.0</version>
                    </dependency>
                </dependencies>
                <build>
                    <plugins>
                        <plugin>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-maven-plugin</artifactId>
                        </plugin>
                    </plugins>
                </build>
            </project>
            """.formatted(packageName, appName.toLowerCase(), appName);
    }

    private String generatePackageJson(String appName) {
        return """
            {
              "name": "%s",
              "version": "0.1.0",
              "private": true,
              "scripts": {
                "dev": "next dev",
                "build": "next build",
                "start": "next start",
                "lint": "next lint"
              },
              "dependencies": {
                "react": "^18",
                "react-dom": "^18",
                "next": "14.1.0",
                "tailwindcss": "^3.3.0",
                "autoprefixer": "^10.0.1",
                "postcss": "^8",
                "lucide-react": "^0.300.0"
              },
              "devDependencies": {
                "typescript": "^5",
                "@types/node": "^20",
                "@types/react": "^18",
                "@types/react-dom": "^18",
                "eslint": "^8",
                "eslint-config-next": "14.1.0"
              }
            }
            """.formatted(appName.toLowerCase());
    }


    private AppSpecEntity saveAppSpec(String userRequirement, Map<String, Object> appSpecJson, PlanResult planResult) {
         try {
            UUID tenantId = UUID.fromString("00000000-0000-0000-0000-000000000001");
            UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000001");
            com.ingenio.backend.agent.dto.IntentClassificationResult intentResult = planResult.getIntentClassificationResult();
            AppSpecEntity.AppSpecEntityBuilder builder = AppSpecEntity.builder()
                    .id(UUID.randomUUID())
                    .tenantId(tenantId)
                    .createdByUserId(userId)
                    .specContent(appSpecJson)
                    .version(1)
                    .status(AppSpecEntity.Status.DRAFT.getValue())
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now());
            if (intentResult != null) {
                if (intentResult.getIntent() != null) builder.intentType(intentResult.getIntent().name());
                if (intentResult.getConfidence() != null) builder.confidenceScore(java.math.BigDecimal.valueOf(intentResult.getConfidence()));
                builder.matchedTemplates(new java.util.ArrayList<>());
                builder.designConfirmed(false);
            } else {
                builder.designConfirmed(false);
                builder.matchedTemplates(new java.util.ArrayList<>());
            }
            AppSpecEntity appSpec = builder.build();
            appSpecMapper.insert(appSpec);
            log.info("AppSpec保存成功 - id: {}", appSpec.getId());
            return appSpec;
        } catch (Exception e) {
            log.error("保存AppSpec失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "保存AppSpec失败: " + e.getMessage());
        }
    }
    
    private String packageToPath(String packageName) {
        return packageName.replace('.', '/');
    }
    
    private Map<String, String> prefixMapKeys(Map<String, String> map, String prefix) {
        return map.entrySet().stream()
                .collect(Collectors.toMap(
                        entry -> prefix + entry.getKey(),
                        Map.Entry::getValue
                ));
    }


    private GenerateFullResponse.CodeGenerationSummary buildCodeSummary(
            Map<String, String> generatedFiles,
            String projectName,
            Map<String, Object> appSpecJson
    ) {
        int totalFiles = generatedFiles.size();
        long totalSize = generatedFiles.values().stream()
                .mapToLong(content -> content.getBytes(java.nio.charset.StandardCharsets.UTF_8).length)
                .sum();

        return GenerateFullResponse.CodeGenerationSummary.builder()
                .totalFiles(totalFiles)
                .totalSize(totalSize)
                .zipFileName(projectName + "-" + UUID.randomUUID().toString().substring(0, 8) + ".zip")
                .build();
    }
}
