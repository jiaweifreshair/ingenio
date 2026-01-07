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

        OpenLovableResponseSanitizer.SanitizeResult result = OpenLovableResponseSanitizer.sanitizeForSandboxApply(input);

        assertEquals(2, result.removedPaths().size());
        assertTrue(result.removedPaths().contains("package.json"));
        assertTrue(result.removedPaths().contains("vite.config.ts"));

        assertNotNull(result.sanitizedResponse());
        assertFalse(result.sanitizedResponse().contains("package.json"));
        assertFalse(result.sanitizedResponse().contains("vite.config.ts"));
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

        OpenLovableResponseSanitizer.SanitizeResult result = OpenLovableResponseSanitizer.sanitizeForSandboxApply(input);

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

        OpenLovableResponseSanitizer.SanitizeResult result = OpenLovableResponseSanitizer.sanitizeForSandboxApply(input);

        assertTrue(result.removedPaths().isEmpty());
        assertSame(input, result.sanitizedResponse());
    }
}
