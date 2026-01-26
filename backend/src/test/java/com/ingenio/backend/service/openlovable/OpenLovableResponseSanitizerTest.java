package com.ingenio.backend.service.openlovable;

import org.junit.jupiter.api.Test;

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
}
