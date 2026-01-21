package com.ingenio.backend.service.adapter;

import com.ingenio.backend.dto.CodeGenerationResult;
import com.ingenio.backend.dto.CodeGenerationResult.GeneratedFile;
import com.ingenio.backend.dto.request.validation.CompileValidationRequest;
import com.ingenio.backend.dto.request.validation.TestValidationRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 验证请求适配器
 *
 * 将CompileValidationRequest适配为CodeGenerationResult
 * 解决DTO不兼容问题：
 * - CompileValidationRequest: appSpecId, code, language
 * - CodeGenerationResult: taskId, projectRoot, projectType, files
 *
 * @author Ingenio Team
 * @since 2.0.0 Phase 1
 */
@Component
public class ValidationRequestAdapter {

    private static final Logger log = LoggerFactory.getLogger(ValidationRequestAdapter.class);

    /**
     * 将CompileValidationRequest转换为CodeGenerationResult
     *
     * 转换逻辑：
     * 1. appSpecId → taskId
     * 2. language → projectType (typescript→nextjs, java→spring-boot, kotlin→kmp)
     * 3. code → 临时文件系统 + files列表
     * 4. 创建临时projectRoot目录
     *
     * @param request 编译验证请求
     * @return 代码生成结果（适配后）
     * @throws RuntimeException 当临时目录创建或文件写入失败时抛出
     */
    public CodeGenerationResult toCodeGenerationResult(CompileValidationRequest request) {
        log.info("开始适配CompileValidationRequest → CodeGenerationResult: appSpecId={}, language={}",
                request.getAppSpecId(), request.getLanguage());

        try {
            // 1. 创建临时项目目录
            Path tempProjectRoot = createTempProjectDirectory();
            log.debug("创建临时项目目录: {}", tempProjectRoot);

            // 2. 映射语言到项目类型
            String projectType = mapLanguageToProjectType(request.getLanguage());
            log.debug("语言映射: {} → {}", request.getLanguage(), projectType);

            // 3. 生成文件列表（根据项目类型创建相应的文件结构）
            List<GeneratedFile> files = createProjectFiles(
                    request.getCode(),
                    request.getLanguage(),
                    projectType,
                    tempProjectRoot
            );
            log.debug("生成文件列表: count={}", files.size());

            // 4. 构建CodeGenerationResult
            CodeGenerationResult result = CodeGenerationResult.builder()
                    .taskId(request.getAppSpecId())
                    .tenantId(null) // 编译验证不需要租户ID
                    .projectRoot(tempProjectRoot.toString())
                    .projectType(projectType)
                    .files(files)
                    .techStack(detectTechStack(projectType))
                    .buildConfigs(createBuildConfigs(projectType, request.getCompilerVersion()))
                    .dependencies(new ArrayList<>()) // 简化实现：暂不解析依赖
                    .build();

            log.info("适配完成: projectRoot={}, projectType={}, filesCount={}",
                    tempProjectRoot, projectType, files.size());

            return result;

        } catch (IOException e) {
            log.error("适配失败：临时目录创建或文件写入失败", e);
            throw new RuntimeException("适配CompileValidationRequest失败: " + e.getMessage(), e);
        }
    }

    /**
     * 创建临时项目目录
     *
     * @return 临时目录路径
     * @throws IOException 当目录创建失败时抛出
     */
    private Path createTempProjectDirectory() throws IOException {
        return Files.createTempDirectory("ingenio-validation-");
    }

    /**
     * 映射编程语言到项目类型
     *
     * 映射规则：
     * - typescript → nextjs（Next.js项目）
     * - java → spring-boot（Spring Boot项目）
     * - kotlin → kmp（Kotlin Multiplatform项目）
     *
     * @param language 编程语言
     * @return 项目类型
     * @throws IllegalArgumentException 当语言不支持时抛出
     */
    private String mapLanguageToProjectType(String language) {
        return switch (language.toLowerCase()) {
            case "typescript" -> "nextjs";
            case "java" -> "spring-boot";
            case "kotlin" -> "kmp";
            default -> throw new IllegalArgumentException("不支持的编程语言: " + language);
        };
    }

    /**
     * 创建项目文件列表
     *
     * 根据项目类型创建相应的文件结构：
     * - nextjs: src/page.tsx, package.json, tsconfig.json
     * - spring-boot: src/main/java/Main.java, pom.xml
     * - kmp: src/commonMain/kotlin/Main.kt, build.gradle.kts
     *
     * @param code 代码内容
     * @param language 编程语言
     * @param projectType 项目类型
     * @param projectRoot 项目根目录
     * @return 生成的文件列表
     * @throws IOException 当文件写入失败时抛出
     */
    private List<GeneratedFile> createProjectFiles(
            String code,
            String language,
            String projectType,
            Path projectRoot
    ) throws IOException {
        List<GeneratedFile> files = new ArrayList<>();

        switch (projectType) {
            case "nextjs" -> {
                // TypeScript: 创建Next.js项目结构
                files.add(createTypeScriptFile(code, projectRoot));
                files.add(createPackageJson(projectRoot));
                files.add(createTsConfig(projectRoot));
            }
            case "spring-boot" -> {
                // Java: 创建Spring Boot项目结构
                files.add(createJavaFile(code, projectRoot));
                files.add(createPomXml(projectRoot));
            }
            case "kmp" -> {
                // Kotlin: 创建KMP项目结构
                files.add(createKotlinFile(code, projectRoot));
                files.add(createGradleBuild(projectRoot));
            }
            default -> throw new IllegalArgumentException("不支持的项目类型: " + projectType);
        }

        return files;
    }

    /**
     * 创建TypeScript文件
     */
    private GeneratedFile createTypeScriptFile(String code, Path projectRoot) throws IOException {
        String filePath = "src/page.tsx";
        Path fullPath = projectRoot.resolve(filePath);

        Files.createDirectories(fullPath.getParent());
        Files.writeString(fullPath, code, StandardCharsets.UTF_8);

        return GeneratedFile.builder()
                .path(filePath)
                .content(code)
                .type("typescript")
                .build();
    }

    /**
     * 创建package.json（最小化Next.js配置）
     */
    private GeneratedFile createPackageJson(Path projectRoot) throws IOException {
        String filePath = "package.json";
        String content = """
                {
                  "name": "validation-project",
                  "version": "1.0.0",
                  "scripts": {
                    "build": "next build",
                    "typecheck": "tsc --noEmit"
                  },
                  "dependencies": {
                    "next": "^15.0.0",
                    "react": "^19.0.0",
                    "react-dom": "^19.0.0"
                  },
                  "devDependencies": {
                    "typescript": "^5.3.0",
                    "@types/react": "^19.0.0",
                    "@types/node": "^20.0.0"
                  }
                }
                """;

        Path fullPath = projectRoot.resolve(filePath);
        Files.writeString(fullPath, content, StandardCharsets.UTF_8);

        return GeneratedFile.builder()
                .path(filePath)
                .content(content)
                .type("json")
                .build();
    }

    /**
     * 创建tsconfig.json
     */
    private GeneratedFile createTsConfig(Path projectRoot) throws IOException {
        String filePath = "tsconfig.json";
        String content = """
                {
                  "compilerOptions": {
                    "target": "ES2020",
                    "lib": ["dom", "dom.iterable", "esnext"],
                    "strict": true,
                    "noEmit": true,
                    "esModuleInterop": true,
                    "moduleResolution": "bundler",
                    "resolveJsonModule": true,
                    "isolatedModules": true,
                    "jsx": "preserve"
                  },
                  "include": ["src/**/*"]
                }
                """;

        Path fullPath = projectRoot.resolve(filePath);
        Files.writeString(fullPath, content, StandardCharsets.UTF_8);

        return GeneratedFile.builder()
                .path(filePath)
                .content(content)
                .type("json")
                .build();
    }

    /**
     * 创建Java文件
     */
    private GeneratedFile createJavaFile(String code, Path projectRoot) throws IOException {
        String filePath = "src/main/java/Main.java";
        Path fullPath = projectRoot.resolve(filePath);

        Files.createDirectories(fullPath.getParent());
        Files.writeString(fullPath, code, StandardCharsets.UTF_8);

        return GeneratedFile.builder()
                .path(filePath)
                .content(code)
                .type("java")
                .build();
    }

    /**
     * 创建pom.xml（最小化Spring Boot配置）
     */
    private GeneratedFile createPomXml(Path projectRoot) throws IOException {
        String filePath = "pom.xml";
        String content = """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.ingenio</groupId>
                    <artifactId>validation-project</artifactId>
                    <version>1.0.0</version>
                    <properties>
                        <maven.compiler.source>17</maven.compiler.source>
                        <maven.compiler.target>17</maven.compiler.target>
                        <spring-boot.version>3.4.0</spring-boot.version>
                    </properties>
                    <dependencies>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter-web</artifactId>
                            <version>${spring-boot.version}</version>
                        </dependency>
                    </dependencies>
                    <build>
                        <plugins>
                            <plugin>
                                <groupId>org.apache.maven.plugins</groupId>
                                <artifactId>maven-compiler-plugin</artifactId>
                                <version>3.11.0</version>
                            </plugin>
                        </plugins>
                    </build>
                </project>
                """;

        Path fullPath = projectRoot.resolve(filePath);
        Files.writeString(fullPath, content, StandardCharsets.UTF_8);

        return GeneratedFile.builder()
                .path(filePath)
                .content(content)
                .type("xml")
                .build();
    }

    /**
     * 创建Kotlin文件
     */
    private GeneratedFile createKotlinFile(String code, Path projectRoot) throws IOException {
        String filePath = "src/commonMain/kotlin/Main.kt";
        Path fullPath = projectRoot.resolve(filePath);

        Files.createDirectories(fullPath.getParent());
        Files.writeString(fullPath, code, StandardCharsets.UTF_8);

        return GeneratedFile.builder()
                .path(filePath)
                .content(code)
                .type("kotlin")
                .build();
    }

    /**
     * 创建build.gradle.kts（最小化KMP配置）
     */
    private GeneratedFile createGradleBuild(Path projectRoot) throws IOException {
        String filePath = "build.gradle.kts";
        String content = """
                plugins {
                    kotlin("multiplatform") version "1.9.21"
                }

                group = "com.ingenio"
                version = "1.0.0"

                kotlin {
                    jvm()
                    sourceSets {
                        val commonMain by getting
                    }
                }
                """;

        Path fullPath = projectRoot.resolve(filePath);
        Files.writeString(fullPath, content, StandardCharsets.UTF_8);

        return GeneratedFile.builder()
                .path(filePath)
                .content(content)
                .type("kotlin")
                .build();
    }

    /**
     * 检测技术栈
     */
    private String detectTechStack(String projectType) {
        return switch (projectType) {
            case "nextjs" -> "Next.js 15 + React 19 + TypeScript 5";
            case "spring-boot" -> "Spring Boot 3.4.0 + Java 17";
            case "kmp" -> "Kotlin Multiplatform 1.9.21";
            default -> "Unknown";
        };
    }

    /**
     * 创建构建配置
     */
    private Map<String, String> createBuildConfigs(String projectType, String compilerVersion) {
        Map<String, String> configs = new HashMap<>();

        switch (projectType) {
            case "nextjs" -> {
                configs.put("typescript_version", compilerVersion != null ? compilerVersion : "5.3.0");
                configs.put("next_version", "15.0.0");
            }
            case "spring-boot" -> {
                configs.put("java_version", compilerVersion != null ? compilerVersion : "17");
                configs.put("spring_boot_version", "3.4.0");
                configs.put("maven_version", "3.9.0");
            }
            case "kmp" -> {
                configs.put("kotlin_version", compilerVersion != null ? compilerVersion : "1.9.21");
                configs.put("gradle_version", "8.5");
            }
        }

        return configs;
    }

    /**
     * 将TestValidationRequest转换为CodeGenerationResult（Phase 2扩展）
     *
     * 转换逻辑：
     * 1. appSpecId → taskId
     * 2. testType + testFiles → 推断projectType
     * 3. 使用现有的项目根目录（或创建临时目录）
     *
     * @param request 测试验证请求
     * @return 代码生成结果（适配后）
     * @throws RuntimeException 当临时目录创建失败时抛出
     */
    public CodeGenerationResult toCodeGenerationResultForTest(TestValidationRequest request) {
        log.info("开始适配TestValidationRequest → CodeGenerationResult: appSpecId={}, testType={}",
                request.getAppSpecId(), request.getTestType());

        try {
            // 1. 创建临时项目目录（测试场景）
            Path tempProjectRoot = createTempProjectDirectory();
            log.debug("创建临时项目目录: {}", tempProjectRoot);

            // 2. 从testFiles推断项目类型
            String projectType = detectProjectTypeFromTestFiles(request.getTestFiles());
            log.debug("从测试文件推断项目类型: {}", projectType);

            // 3. 构建CodeGenerationResult（测试执行不需要完整的文件列表）
            CodeGenerationResult result = CodeGenerationResult.builder()
                    .taskId(request.getAppSpecId())
                    .tenantId(null) // 测试验证不需要租户ID
                    .projectRoot(tempProjectRoot.toString())
                    .projectType(projectType)
                    .files(new ArrayList<>()) // 测试执行时文件已存在，不需要生成
                    .techStack(detectTechStack(projectType))
                    .buildConfigs(new HashMap<>())
                    .dependencies(new ArrayList<>())
                    .build();

            log.info("测试请求适配完成: projectRoot={}, projectType={}",
                    tempProjectRoot, projectType);

            return result;

        } catch (IOException e) {
            log.error("适配TestValidationRequest失败：临时目录创建失败", e);
            throw new RuntimeException("适配TestValidationRequest失败: " + e.getMessage(), e);
        }
    }

    /**
     * 从测试文件列表推断项目类型
     *
     * 推断规则：
     * - 包含 .test.ts / .spec.ts → nextjs (TypeScript)
     * - 包含 Test.java → spring-boot (Java)
     * - 包含 Test.kt → kmp (Kotlin)
     * - 默认 → nextjs
     *
     * @param testFiles 测试文件列表
     * @return 项目类型
     */
    private String detectProjectTypeFromTestFiles(List<String> testFiles) {
        if (testFiles == null || testFiles.isEmpty()) {
            log.warn("测试文件列表为空，默认使用nextjs");
            return "nextjs";
        }

        // 检查文件扩展名
        for (String file : testFiles) {
            if (file.endsWith(".test.ts") || file.endsWith(".spec.ts") ||
                file.endsWith(".test.tsx") || file.endsWith(".spec.tsx")) {
                return "nextjs";
            } else if (file.endsWith("Test.java") || file.endsWith("Tests.java")) {
                return "spring-boot";
            } else if (file.endsWith("Test.kt") || file.endsWith("Tests.kt")) {
                return "kmp";
            }
        }

        log.warn("无法从测试文件推断项目类型，默认使用nextjs");
        return "nextjs";
    }
}
