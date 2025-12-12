package com.ingenio.backend.service;

import com.ingenio.backend.dto.CodeGenerationResult;
import com.ingenio.backend.dto.CompilationResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 编译验证器
 *
 * 支持的编译器：
 * - Kotlin Multiplatform (kotlinc)
 * - Java (javac/Maven/Gradle)
 * - TypeScript (tsc)
 *
 * 验证流程：
 * 1. 检测项目类型
 * 2. 选择合适的编译命令
 * 3. 执行编译
 * 4. 解析编译输出
 * 5. 提取错误和警告
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CompilationValidator {

    /**
     * 编译生成的代码
     *
     * @param codeResult 代码生成结果
     * @return 编译结果
     */
    public CompilationResult compile(CodeGenerationResult codeResult) {
        log.info("开始编译验证: taskId={}, projectType={}",
                codeResult.getTaskId(), codeResult.getProjectType());

        long startTime = System.currentTimeMillis();

        try {
            String projectType = codeResult.getProjectType();
            String projectRoot = codeResult.getProjectRoot();

            CompilationResult result;

            switch (projectType) {
                case "kmp" -> result = compileKotlinMultiplatform(projectRoot);
                case "spring-boot" -> result = compileSpringBoot(projectRoot);
                case "nextjs" -> result = compileNextJS(projectRoot);
                default -> {
                    log.warn("未知的项目类型: {}", projectType);
                    result = CompilationResult.builder()
                            .success(false)
                            .compiler("unknown")
                            .fullOutput("未知的项目类型: " + projectType)
                            .build();
                }
            }

            long duration = System.currentTimeMillis() - startTime;
            result.setDurationMs(duration);

            if (result.getSuccess()) {
                log.info("编译成功: taskId={}, duration={}ms",
                        codeResult.getTaskId(), duration);
            } else {
                log.error("编译失败: taskId={}, errors={}, warnings={}",
                        codeResult.getTaskId(),
                        result.getErrorCount(),
                        result.getWarningCount());
            }

            return result;

        } catch (Exception e) {
            log.error("编译过程异常: taskId={}", codeResult.getTaskId(), e);

            return CompilationResult.builder()
                    .success(false)
                    .compiler("unknown")
                    .errors(List.of(
                            CompilationResult.CompilationError.builder()
                                    .message("编译过程异常: " + e.getMessage())
                                    .build()
                    ))
                    .fullOutput(e.toString())
                    .durationMs(System.currentTimeMillis() - startTime)
                    .build();
        }
    }

    /**
     * 编译Kotlin Multiplatform项目
     */
    private CompilationResult compileKotlinMultiplatform(String projectRoot) {
        log.info("编译Kotlin Multiplatform项目: {}", projectRoot);

        String command = "cd " + projectRoot + " && ./gradlew build --no-daemon";

        try {
            ProcessResult processResult = executeCommand(command, projectRoot);

            boolean success = processResult.exitCode == 0;

            return CompilationResult.builder()
                    .success(success)
                    .compiler("kotlinc + gradle")
                    .compilerVersion(detectKotlinVersion(projectRoot))
                    .command(command)
                    .outputDirectory(projectRoot + "/build")
                    .errors(success ? List.of() : parseKotlinErrors(processResult.output))
                    .warnings(parseKotlinWarnings(processResult.output))
                    .fullOutput(processResult.output)
                    .build();

        } catch (Exception e) {
            log.error("Kotlin编译失败", e);
            return CompilationResult.builder()
                    .success(false)
                    .compiler("kotlinc")
                    .errors(List.of(
                            CompilationResult.CompilationError.builder()
                                    .message(e.getMessage())
                                    .build()
                    ))
                    .fullOutput(e.toString())
                    .build();
        }
    }

    /**
     * 编译Spring Boot项目
     */
    private CompilationResult compileSpringBoot(String projectRoot) {
        log.info("编译Spring Boot项目: {}", projectRoot);

        // 检测是用Maven还是Gradle
        boolean isMaven = new File(projectRoot + "/pom.xml").exists();
        String command = isMaven
                ? "cd " + projectRoot + " && mvn clean compile -DskipTests"
                : "cd " + projectRoot + " && ./gradlew build -x test";

        try {
            ProcessResult processResult = executeCommand(command, projectRoot);

            boolean success = processResult.exitCode == 0;

            return CompilationResult.builder()
                    .success(success)
                    .compiler(isMaven ? "javac + maven" : "javac + gradle")
                    .compilerVersion(detectJavaVersion())
                    .command(command)
                    .outputDirectory(isMaven ? projectRoot + "/target/classes" : projectRoot + "/build/classes")
                    .errors(success ? List.of() : parseJavaErrors(processResult.output))
                    .warnings(parseJavaWarnings(processResult.output))
                    .fullOutput(processResult.output)
                    .build();

        } catch (Exception e) {
            log.error("Java编译失败", e);
            return CompilationResult.builder()
                    .success(false)
                    .compiler("javac")
                    .errors(List.of(
                            CompilationResult.CompilationError.builder()
                                    .message(e.getMessage())
                                    .build()
                    ))
                    .fullOutput(e.toString())
                    .build();
        }
    }

    /**
     * 编译Next.js项目
     */
    private CompilationResult compileNextJS(String projectRoot) {
        log.info("编译Next.js项目: {}", projectRoot);

        String command = "cd " + projectRoot + " && npm run build";

        try {
            ProcessResult processResult = executeCommand(command, projectRoot);

            boolean success = processResult.exitCode == 0;

            return CompilationResult.builder()
                    .success(success)
                    .compiler("tsc + next")
                    .compilerVersion(detectTypeScriptVersion(projectRoot))
                    .command(command)
                    .outputDirectory(projectRoot + "/.next")
                    .errors(success ? List.of() : parseTypeScriptErrors(processResult.output))
                    .warnings(parseTypeScriptWarnings(processResult.output))
                    .fullOutput(processResult.output)
                    .build();

        } catch (Exception e) {
            log.error("TypeScript编译失败", e);
            return CompilationResult.builder()
                    .success(false)
                    .compiler("tsc")
                    .errors(List.of(
                            CompilationResult.CompilationError.builder()
                                    .message(e.getMessage())
                                    .build()
                    ))
                    .fullOutput(e.toString())
                    .build();
        }
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 执行Shell命令
     */
    private ProcessResult executeCommand(String command, String workingDir) throws Exception {
        ProcessBuilder pb = new ProcessBuilder("bash", "-c", command);
        pb.directory(new File(workingDir));
        pb.redirectErrorStream(true);

        Process process = pb.start();

        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }

        boolean finished = process.waitFor(10, TimeUnit.MINUTES);
        if (!finished) {
            process.destroy();
            throw new RuntimeException("编译超时（10分钟）");
        }

        int exitCode = process.exitValue();

        return new ProcessResult(exitCode, output.toString());
    }

    /**
     * 解析Kotlin编译错误
     */
    private List<CompilationResult.CompilationError> parseKotlinErrors(String output) {
        List<CompilationResult.CompilationError> errors = new ArrayList<>();

        // Kotlin错误格式: e: file:///path/to/file.kt:10:5: error message
        Pattern pattern = Pattern.compile("e: (.+?):(\\d+):(\\d+): (.+)");
        Matcher matcher = pattern.matcher(output);

        while (matcher.find()) {
            errors.add(CompilationResult.CompilationError.builder()
                    .filePath(matcher.group(1))
                    .lineNumber(Integer.parseInt(matcher.group(2)))
                    .columnNumber(Integer.parseInt(matcher.group(3)))
                    .message(matcher.group(4))
                    .build());
        }

        return errors;
    }

    /**
     * 解析Kotlin编译警告
     */
    private List<CompilationResult.CompilationWarning> parseKotlinWarnings(String output) {
        List<CompilationResult.CompilationWarning> warnings = new ArrayList<>();

        // Kotlin警告格式: w: file:///path/to/file.kt:10:5: warning message
        Pattern pattern = Pattern.compile("w: (.+?):(\\d+):(\\d+): (.+)");
        Matcher matcher = pattern.matcher(output);

        while (matcher.find()) {
            warnings.add(CompilationResult.CompilationWarning.builder()
                    .filePath(matcher.group(1))
                    .lineNumber(Integer.parseInt(matcher.group(2)))
                    .message(matcher.group(4))
                    .build());
        }

        return warnings;
    }

    /**
     * 解析Java编译错误
     */
    private List<CompilationResult.CompilationError> parseJavaErrors(String output) {
        List<CompilationResult.CompilationError> errors = new ArrayList<>();

        // Java错误格式: [ERROR] /path/to/File.java:[10,5] error message
        Pattern pattern = Pattern.compile("\\[ERROR\\] (.+?):\\[(\\d+),(\\d+)\\] (.+)");
        Matcher matcher = pattern.matcher(output);

        while (matcher.find()) {
            errors.add(CompilationResult.CompilationError.builder()
                    .filePath(matcher.group(1))
                    .lineNumber(Integer.parseInt(matcher.group(2)))
                    .columnNumber(Integer.parseInt(matcher.group(3)))
                    .message(matcher.group(4))
                    .build());
        }

        return errors;
    }

    /**
     * 解析Java编译警告
     */
    private List<CompilationResult.CompilationWarning> parseJavaWarnings(String output) {
        List<CompilationResult.CompilationWarning> warnings = new ArrayList<>();

        // Java警告格式: [WARNING] /path/to/File.java:[10] warning message
        Pattern pattern = Pattern.compile("\\[WARNING\\] (.+?):\\[(\\d+)\\] (.+)");
        Matcher matcher = pattern.matcher(output);

        while (matcher.find()) {
            warnings.add(CompilationResult.CompilationWarning.builder()
                    .filePath(matcher.group(1))
                    .lineNumber(Integer.parseInt(matcher.group(2)))
                    .message(matcher.group(3))
                    .build());
        }

        return warnings;
    }

    /**
     * 解析TypeScript编译错误
     */
    private List<CompilationResult.CompilationError> parseTypeScriptErrors(String output) {
        List<CompilationResult.CompilationError> errors = new ArrayList<>();

        // TypeScript错误格式: src/app/page.tsx(10,5): error TS2322: message
        Pattern pattern = Pattern.compile("(.+?)\\((\\d+),(\\d+)\\): error (TS\\d+): (.+)");
        Matcher matcher = pattern.matcher(output);

        while (matcher.find()) {
            errors.add(CompilationResult.CompilationError.builder()
                    .filePath(matcher.group(1))
                    .lineNumber(Integer.parseInt(matcher.group(2)))
                    .columnNumber(Integer.parseInt(matcher.group(3)))
                    .errorCode(matcher.group(4))
                    .message(matcher.group(5))
                    .build());
        }

        return errors;
    }

    /**
     * 解析TypeScript编译警告
     */
    private List<CompilationResult.CompilationWarning> parseTypeScriptWarnings(String output) {
        // TypeScript一般不输出警告，直接返回空列表
        return new ArrayList<>();
    }

    /**
     * 检测Kotlin版本
     */
    private String detectKotlinVersion(String projectRoot) {
        try {
            ProcessResult result = executeCommand("kotlinc -version", projectRoot);
            return result.output.trim();
        } catch (Exception e) {
            return "unknown";
        }
    }

    /**
     * 检测Java版本
     */
    private String detectJavaVersion() {
        return System.getProperty("java.version");
    }

    /**
     * 检测TypeScript版本
     */
    private String detectTypeScriptVersion(String projectRoot) {
        try {
            ProcessResult result = executeCommand("npx tsc --version", projectRoot);
            return result.output.trim();
        } catch (Exception e) {
            return "unknown";
        }
    }

    /**
     * 进程执行结果
     */
    private record ProcessResult(int exitCode, String output) {
    }
}
