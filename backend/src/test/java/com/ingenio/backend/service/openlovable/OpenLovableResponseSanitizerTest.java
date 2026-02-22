package com.ingenio.backend.service.openlovable;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * OpenLovableResponseSanitizer 单测
 *
 * 目标：验证 apply 阶段会过滤高风险配置文件，避免覆盖沙箱模板导致预览白屏。
 */
class OpenLovableResponseSanitizerTest {

    @Test
    void shouldRemoveBlockedConfigFilesAndKeepBusinessFiles() {
        String input = """
                <file path="package.json">
                {"name":"bad","scripts":{}}
                </file>

                <file path="src/App.jsx">
                export default function App(){ return <div>OK</div> }
                </file>

                <file path="vite.config.ts">
                export default {}
                </file>
                """;

        OpenLovableResponseSanitizer.SanitizeResult result = OpenLovableResponseSanitizer
                .sanitizeForSandboxApply(input);

        // package.json is now merged (not removed), vite.config.ts is NOT removed
        assertEquals(0, result.removedPaths().size());

        // package.json should be in mergedPaths
        assertEquals(1, result.mergedPaths().size());
        assertTrue(result.mergedPaths().contains("package.json"));

        assertNotNull(result.sanitizedResponse());
        // package.json is merged (still present in response)
        assertTrue(result.sanitizedResponse().contains("package.json"));
        // vite.config.ts should be RETAINED
        assertTrue(result.sanitizedResponse().contains("vite.config.ts"));
        assertTrue(result.sanitizedResponse().contains("src/App.jsx"));
        assertTrue(result.sanitizedResponse().contains("return <div>OK</div>"));
    }

    @Test
    void shouldNormalizePathAndBlockEnvFiles() {
        String input = """
                <file path="./.env.local">
                SECRET=should_not_be_written
                </file>
                <file path="src/main.jsx">
                console.log('hello')
                </file>
                """;

        OpenLovableResponseSanitizer.SanitizeResult result = OpenLovableResponseSanitizer
                .sanitizeForSandboxApply(input);

        assertEquals(1, result.removedPaths().size());
        assertEquals(".env.local", result.removedPaths().get(0));
        assertFalse(result.sanitizedResponse().contains("SECRET="));
        assertTrue(result.sanitizedResponse().contains("src/main.jsx"));
    }

    @Test
    void shouldReturnOriginalWhenNoBlockedFilesPresent() {
        String input = """
                <file path="src/App.jsx">
                export default function App(){ return <div>OK</div> }
                </file>
                """;

        OpenLovableResponseSanitizer.SanitizeResult result = OpenLovableResponseSanitizer
                .sanitizeForSandboxApply(input);

        assertTrue(result.removedPaths().isEmpty());
        assertTrue(result.mergedPaths().isEmpty());
        // The sanitizer processes the response, so it returns a new string (not the
        // same reference)
        // But the content should be equivalent
        assertEquals(input.trim(), result.sanitizedResponse().trim());
    }

    @Test
    void shouldConvertLucideRequireToImport() {
        String input = """
                <file path="src/App.jsx">
                const { Activity, AlertCircle } = require('lucide-react');
                export default function App(){ return <div><Activity /></div> }
                </file>
                """;

        OpenLovableResponseSanitizer.SanitizeResult result = OpenLovableResponseSanitizer
                .sanitizeForSandboxApply(input);

        assertTrue(result.sanitizedResponse().contains("import { Activity, AlertCircle } from 'lucide-react';"));
        assertFalse(result.sanitizedResponse().contains("require('lucide-react')"));
    }

    @Test
    void shouldConvertLucideNamespaceRequireToImportStar() {
        String input = """
                <file path="src/App.jsx">
                const Icons = require("lucide-react");
                export default function App(){ return <div><Icons.Activity /></div> }
                </file>
                """;

        OpenLovableResponseSanitizer.SanitizeResult result = OpenLovableResponseSanitizer
                .sanitizeForSandboxApply(input);

        assertTrue(result.sanitizedResponse().contains("import * as Icons from 'lucide-react';"));
        assertFalse(result.sanitizedResponse().contains("require(\"lucide-react\")"));
    }

    /**
     * 是什么：lucide-react 导入中误带 React Hook 的清洗用例。
     * 做什么：验证清洗器会移除 lucide-react 中的 Hook 导入，避免重复声明。
     * 为什么：防止 Vite/Babel 报错 "Identifier 'useState' has already been declared"。
     */
    @Test
    void shouldStripReactHooksFromLucideImport() {
        String input = """
                <file path="src/components/Header.jsx">
                import { X, Menu, useState } from 'lucide-react';
                import { useState } from 'react';
                export default function Header(){ return <div/> }
                </file>
                """;

        OpenLovableResponseSanitizer.SanitizeResult result = OpenLovableResponseSanitizer
                .sanitizeForSandboxApply(input);

        assertTrue(result.sanitizedResponse().contains("import { X, Menu } from 'lucide-react';"));
        assertTrue(result.sanitizedResponse().contains("import { useState } from 'react';"));
        assertFalse(result.sanitizedResponse().contains("useState } from 'lucide-react'"));
    }

    /**
     * 是什么：lucide 图标缺失导入的修复用例。
     * 做什么：在 JSX 使用 Palette 但未导入时，补齐 lucide-react 导入列表。
     * 为什么：避免预览运行时报 ReferenceError。
     */
    @Test
    void shouldAppendMissingLucideIconImport() {
        String input = """
                <file path="src/components/Footer.jsx">
                import React from 'react';
                import { Github, Twitter, Instagram } from 'lucide-react';
                export default function Footer(){ return <Palette className="w-4 h-4" /> }
                </file>
                """;

        OpenLovableResponseSanitizer.SanitizeResult result = OpenLovableResponseSanitizer
                .sanitizeForSandboxApply(input);

        assertTrue(result.sanitizedResponse().contains(
                "import { Github, Twitter, Instagram, Palette } from 'lucide-react';"));
    }

    @Test
    void shouldNormalizeSupabaseEnvAndGuardClientCreation() {
        String input = """
                <file path="src/services/supabaseLogic.js">
                import { createClient } from '@supabase/supabase-js'
                const supabaseUrl = process.env.NEXT_PUBLIC_SUPABASE_URL
                const supabaseAnonKey = process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY
                export const supabase = createClient(supabaseUrl, supabaseAnonKey)
                </file>
                """;

        OpenLovableResponseSanitizer.SanitizeResult result = OpenLovableResponseSanitizer
                .sanitizeForSandboxApply(input);

        String sanitized = result.sanitizedResponse();
        assertTrue(sanitized.contains("import.meta.env.VITE_SUPABASE_URL"));
        assertTrue(sanitized.contains("import.meta.env.VITE_SUPABASE_ANON_KEY"));
        assertTrue(sanitized.contains("supabaseUrl && supabaseAnonKey"));
    }

    /**
     * 是什么：Tailwind 自定义噪点背景类缺失的修复用例。
     * 做什么：移除 @apply 中的 bg-subtle-noise，避免编译失败。
     * 为什么：防止 Vite/Tailwind 报错导致白屏。
     */
    @Test
    void shouldStripUnsupportedTailwindNoiseApply() {
        String input = """
                <file path="src/index.css">
                @tailwind base;
                @tailwind components;
                @tailwind utilities;

                body {
                  @apply bg-gradient-to-br from-stone-50 to-stone-100 bg-subtle-noise;
                }
                </file>
                """;

        OpenLovableResponseSanitizer.SanitizeResult result = OpenLovableResponseSanitizer
                .sanitizeForSandboxApply(input);

        assertTrue(result.sanitizedResponse().contains("from-stone-50"));
        assertFalse(result.sanitizedResponse().contains("bg-subtle-noise"));
    }

    /**
     * 场景 API 别名补齐测试。
     *
     * 是什么：验证 api.js 中缺失的 mockScan/mockGenerateSolution 会被补齐。
     * 做什么：在 scanDevice/generateSolution 已存在时注入 mock 别名导出。
     * 为什么：避免前端引用 mockScan 报 “is not defined” 的运行时错误。
     */
    @Test
    void shouldAddMockAliasesForScenarioApi() {
        String input = """
                <file path="src/services/api.js">
                export const scanDevice = async () => ({ ok: true });
                export const generateSolution = async () => ({ ok: true });
                </file>
                """;

        OpenLovableResponseSanitizer.SanitizeResult result = OpenLovableResponseSanitizer
                .sanitizeForSandboxApply(input);

        String sanitized = result.sanitizedResponse();
        assertTrue(sanitized.contains("export const mockScan = scanDevice;"));
        assertTrue(sanitized.contains("export const mockGenerateSolution = generateSolution;"));
    }

    /**
     * 去重策略：当同一路径存在多种脚本扩展名时，保留内容更完整的版本。
     *
     * 是什么：验证 .ts 与 .jsx 冲突时的优先选择规则。
     * 做什么：确保输出仅保留一个版本，避免 Vite 解析冲突。
     * 为什么：修复生成过程中同时写入 TS/JSX 导致的运行时错误。
     */
    @Test
    void shouldPreferRicherScriptVariantWhenDuplicateBasePath() {
        String input = """
                <file path="src/hooks/useStepFlow.ts">
                export default function useStepFlow(){ return { currentStep: 'scan' } }
                </file>

                <file path="src/hooks/useStepFlow.jsx">
                export default function useStepFlow(){
                  return {
                    currentStep: 'scan',
                    setScanData: () => {},
                    setAnalysisData: () => {}
                  }
                }
                </file>
                """;

        OpenLovableResponseSanitizer.SanitizeResult result = OpenLovableResponseSanitizer
                .sanitizeForSandboxApply(input);

        String sanitized = result.sanitizedResponse();
        assertFalse(sanitized.contains("src/hooks/useStepFlow.ts"));
        assertTrue(sanitized.contains("src/hooks/useStepFlow.jsx"));
        assertTrue(sanitized.contains("setAnalysisData"));
    }

    /**
     * 去重策略：内容长度相同时，优先保留 TypeScript 版本。
     *
     * 是什么：验证 .ts 与 .jsx 内容一致时的优先级规则。
     * 做什么：确保在同内容下优先保留 TS 文件，提升类型一致性。
     * 为什么：避免因扩展名优先级不同导致的不可预测加载结果。
     */
    @Test
    void shouldPreferTypeScriptWhenContentLengthTie() {
        String input = """
                <file path="src/hooks/useStepFlow.ts">
                export default function useStepFlow(){ return { currentStep: 'scan' } }
                </file>

                <file path="src/hooks/useStepFlow.jsx">
                export default function useStepFlow(){ return { currentStep: 'scan' } }
                </file>
                """;

        OpenLovableResponseSanitizer.SanitizeResult result = OpenLovableResponseSanitizer
                .sanitizeForSandboxApply(input);

        String sanitized = result.sanitizedResponse();
        assertTrue(sanitized.contains("src/hooks/useStepFlow.ts"));
        assertFalse(sanitized.contains("src/hooks/useStepFlow.jsx"));
    }

    /**
     * 去重策略：同一路径出现空文件块时，保留非空内容。
     *
     * 是什么：验证文件块重复时不会被空内容覆盖。
     * 做什么：确保解析结果保留有效代码。
     * 为什么：避免空文件导致预览白屏或模块缺失。
     */
    @Test
    void shouldKeepNonEmptyFileWhenDuplicateEmptyBlock() {
        String input = """
                <file path="src/context/ItemsContext.tsx">
                export const ItemsProvider = () => null;
                </file>

                <file path="src/context/ItemsContext.tsx">
                </file>
                """;

        var blocks = OpenLovableResponseSanitizer.extractFileBlocks(input);
        assertEquals(1, blocks.size());
        assertEquals("src/context/ItemsContext.tsx", blocks.get(0).normalizedPath());
        assertTrue(blocks.get(0).content().contains("ItemsProvider"));
    }

    /**
     * 空脚本文件视为截断。
     *
     * 是什么：空文件块被认为是生成不完整。
     * 做什么：将空脚本文件纳入截断列表。
     * 为什么：触发自动续生成，避免运行时缺文件。
     */
    @Test
    void sanitizeForSandboxApply_shouldTreatEmptyScriptFileAsTruncated() {
        String input = """
                <file path="src/context/ItemsContext.tsx">
                </file>
                """;

        OpenLovableResponseSanitizer.SanitizeResult result = OpenLovableResponseSanitizer
                .sanitizeForSandboxApply(input);

        assertTrue(result.truncatedPaths().contains("src/context/ItemsContext.tsx"));
    }

    // ==================== 格式转换测试 ====================

    @Test
    void convertToFileFormat_shouldReturnAsIs_whenAlreadyHasFileTags() {
        String input = """
                <file path="src/App.jsx">
                export default function App(){ return <div>OK</div> }
                </file>
                """;

        String result = OpenLovableResponseSanitizer.convertToFileFormat(input);

        assertEquals(input, result);
    }

    @Test
    void convertToFileFormat_shouldConvertBoltFormat() {
        String input = """
                <boltAction type="file" filePath="src/App.jsx">
                export default function App(){ return <div>OK</div> }
                </boltAction>
                """;

        String result = OpenLovableResponseSanitizer.convertToFileFormat(input);

        assertTrue(result.contains("<file path=\"src/App.jsx\">"));
        assertTrue(result.contains("</file>"));
        assertTrue(result.contains("export default function App()"));
    }

    @Test
    void sanitizeForSandboxApply_shouldStripMarkdownFenceInsideFile() {
        String input = """
                <file path="src/components/FixStep.jsx">
                ```jsx
                export default function FixStep() { return <div>OK</div> }
                ```
                </file>
                """;

        OpenLovableResponseSanitizer.SanitizeResult result = OpenLovableResponseSanitizer
                .sanitizeForSandboxApply(input);

        String sanitized = result.sanitizedResponse();
        assertTrue(sanitized.contains("export default function FixStep"));
        assertFalse(sanitized.contains("```"));
    }

    @Test
    void sanitizeForSandboxApply_shouldStripInlineMarkdownFence() {
        String input = """
                <file path="src/components/FixStep.jsx">```jsx export default function FixStep(){ return <div>OK</div> } ```</file>
                """;

        OpenLovableResponseSanitizer.SanitizeResult result = OpenLovableResponseSanitizer
                .sanitizeForSandboxApply(input);

        String sanitized = result.sanitizedResponse();
        assertTrue(sanitized.contains("export default function FixStep"));
        assertFalse(sanitized.contains("```"));
    }

    @Test
    void convertToFileFormat_shouldConvertMarkdownCodeBlockWithFilename() {
        String input = """
                ```jsx filename="src/App.jsx"
                export default function App(){ return <div>OK</div> }
                ```
                """;

        String result = OpenLovableResponseSanitizer.convertToFileFormat(input);

        assertTrue(result.contains("<file path=\"src/App.jsx\">"));
        assertTrue(result.contains("</file>"));
        assertTrue(result.contains("export default function App()"));
    }

    @Test
    void convertToFileFormat_shouldConvertMarkdownCodeBlockWithPathAttribute() {
        String input = """
                ```tsx src/components/Button.tsx
                export function Button(){ return <button>Click</button> }
                ```
                """;

        String result = OpenLovableResponseSanitizer.convertToFileFormat(input);

        assertTrue(result.contains("<file path=\"src/components/Button.tsx\">"));
        assertTrue(result.contains("</file>"));
        assertTrue(result.contains("export function Button()"));
    }

    @Test
    void convertToFileFormat_shouldWrapPureReactCode() {
        String input = """
                import React from 'react'

                export default function App() {
                  return (
                    <div className="container">
                      <h1>Hello World</h1>
                    </div>
                  )
                }
                """;

        String result = OpenLovableResponseSanitizer.convertToFileFormat(input);

        assertTrue(result.contains("<file path=\"src/App.jsx\">"));
        assertTrue(result.contains("</file>"));
        assertTrue(result.contains("export default function App()"));
    }

    @Test
    void convertToFileFormat_shouldWrapCssCode() {
        String input = """
                @tailwind base;
                @tailwind components;
                @tailwind utilities;

                .container {
                  @apply mx-auto px-4;
                }
                """;

        String result = OpenLovableResponseSanitizer.convertToFileFormat(input);

        assertTrue(result.contains("<file path=\"src/index.css\">"));
        assertTrue(result.contains("</file>"));
        assertTrue(result.contains("@tailwind base"));
    }

    @Test
    void convertToFileFormat_shouldWrapMainJsxCode() {
        String input = """
                import React from 'react'
                import ReactDOM from 'react-dom/client'
                import App from './App'

                ReactDOM.createRoot(document.getElementById('root')).render(
                  <React.StrictMode>
                    <App />
                  </React.StrictMode>
                )
                """;

        String result = OpenLovableResponseSanitizer.convertToFileFormat(input);

        assertTrue(result.contains("<file path=\"src/main.jsx\">"));
        assertTrue(result.contains("</file>"));
        assertTrue(result.contains("ReactDOM.createRoot"));
    }

    @Test
    void convertToFileFormat_shouldReturnNullOrOriginal_whenNotCode() {
        String input = "这是一段普通的文字说明，不是代码。";

        String result = OpenLovableResponseSanitizer.convertToFileFormat(input);

        // 应该返回原始内容（因为无法识别为代码）
        assertEquals(input, result);
    }

    @Test
    void convertToFileFormat_shouldHandleMultipleMarkdownBlocks() {
        String input = """
                ```jsx src/App.jsx
                export default function App(){ return <div>App</div> }
                ```

                ```css src/index.css
                @tailwind base;
                ```
                """;

        String result = OpenLovableResponseSanitizer.convertToFileFormat(input);

        assertTrue(result.contains("<file path=\"src/App.jsx\">"));
        assertTrue(result.contains("<file path=\"src/index.css\">"));
        assertTrue(result.contains("export default function App()"));
        assertTrue(result.contains("@tailwind base"));
    }

    /**
     * 增量修复合并测试。
     *
     * 是什么：模拟仅返回部分文件的修复响应。
     * 做什么：合并已有文件并确保新文件覆盖旧内容。
     * 为什么：避免 patch apply 覆盖导致文件丢失或白屏。
     */
    @Test
    void shouldMergePatchBlocksWithExistingFiles() {
        String patch = """
                <file path="src/App.jsx">
                export default function App(){ return <div>new</div> }
                </file>

                <file path="src/components/MetricCard.jsx">
                export const MetricCard = () => <div>metric</div>;
                </file>
                """;

        List<OpenLovableResponseSanitizer.FileBlock> patchBlocks = OpenLovableResponseSanitizer
                .extractFileBlocks(patch);

        Map<String, String> existingFiles = new LinkedHashMap<>();
        existingFiles.put("src/App.jsx", "export default function App(){ return <div>old</div> }");
        existingFiles.put("src/components/MetricCard.jsx", "export const MetricCard = () => <div>old metric</div>;");
        existingFiles.put("src/index.css", "body { margin: 0; }");

        List<OpenLovableResponseSanitizer.FileBlock> mergedBlocks = OpenLovableResponseSanitizer
                .mergeWithExistingFiles(patchBlocks, existingFiles);
        String mergedResponse = OpenLovableResponseSanitizer.buildResponseFromFileBlocks(mergedBlocks);

        assertEquals(3, mergedBlocks.size());
        assertTrue(mergedResponse.contains("src/index.css"));
        assertTrue(mergedResponse.contains("body { margin: 0; }"));
        assertTrue(mergedResponse.contains("<div>new</div>"));
        assertTrue(mergedResponse.contains("<div>metric</div>"));
        assertFalse(mergedResponse.contains("<div>old</div>"));
    }
}
