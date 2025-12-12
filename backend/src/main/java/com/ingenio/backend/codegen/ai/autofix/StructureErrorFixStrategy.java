package com.ingenio.backend.codegen.ai.autofix;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * StructureErrorFixStrategy - 结构错误修复策略（Priority 2）
 *
 * <p>专注于修复代码结构完整性问题，确保代码具备基本的Java类结构</p>
 *
 * <p>修复范围：</p>
 * <ul>
 *   <li>缺少包声明：添加标准包声明</li>
 *   <li>缺少类定义：添加完整的Service类定义</li>
 *   <li>缺少方法定义：添加标准的业务方法定义</li>
 *   <li>缺少@Service注解：添加Spring Service注解</li>
 * </ul>
 *
 * <p>修复策略：</p>
 * <ul>
 *   <li>模板化修复：使用标准模板添加缺失的结构</li>
 *   <li>保留原有代码：只添加缺失部分，不删除已有代码</li>
 *   <li>Spring Boot规范：遵循Spring Boot最佳实践</li>
 * </ul>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * StructureErrorFixStrategy strategy = new StructureErrorFixStrategy();
 * List<ValidationIssue> structureErrors = issues.stream()
 *     .filter(issue -> issue.isStructureError())
 *     .collect(Collectors.toList());
 *
 * if (strategy.supports(structureErrors)) {
 *     String fixedCode = strategy.apply(code, structureErrors);
 * }
 * }</pre>
 *
 * @author Ingenio AutoFix Orchestrator
 * @since 2025-11-19 P0 Phase 2: StructureErrorFixStrategy实现
 */
@Slf4j
@Component
public class StructureErrorFixStrategy implements FixStrategy {

    @Override
    public boolean supports(List<ValidationIssue> issues) {
        // 支持条件：存在STRUCTURE类型的ERROR或WARNING问题
        boolean hasStructureErrors = issues.stream()
                .anyMatch(issue -> issue.isStructureError());

        if (hasStructureErrors) {
            log.debug("[StructureErrorFixStrategy] 检测到结构错误，策略适用");
        }

        return hasStructureErrors;
    }

    @Override
    public String apply(String code, List<ValidationIssue> issues) {
        log.info("[StructureErrorFixStrategy] 开始修复结构错误");

        String fixedCode = code;
        int fixCount = 0;

        // 提取STRUCTURE错误
        List<ValidationIssue> structureErrors = issues.stream()
                .filter(ValidationIssue::isStructureError)
                .collect(Collectors.toList());

        for (ValidationIssue issue : structureErrors) {
            String message = issue.getMessage();

            // 修复1: 缺少包声明
            if (message.contains("缺少包声明")) {
                log.debug("[StructureErrorFixStrategy] 修复缺少包声明");
                fixedCode = fixMissingPackageDeclaration(fixedCode);
                fixCount++;
            }

            // 修复2: 缺少类定义
            if (message.contains("缺少类定义")) {
                log.debug("[StructureErrorFixStrategy] 修复缺少类定义");
                fixedCode = fixMissingClassDefinition(fixedCode);
                fixCount++;
            }

            // 修复3: 未找到方法定义
            if (message.contains("未找到方法定义")) {
                log.debug("[StructureErrorFixStrategy] 修复缺少方法定义");
                fixedCode = fixMissingMethodDefinition(fixedCode);
                fixCount++;
            }
        }

        if (fixCount > 0) {
            log.info("[StructureErrorFixStrategy] ✅ 修复完成：应用了{}个修复", fixCount);
        } else {
            log.warn("[StructureErrorFixStrategy] ⚠️ 未应用任何修复");
        }

        return fixedCode;
    }

    @Override
    public int priority() {
        return 2; // 第二优先级
    }

    @Override
    public String getDescription() {
        return "修复代码结构完整性问题（缺少包声明、类定义、方法定义）";
    }

    /**
     * 修复缺少包声明
     *
     * <p>修复策略：</p>
     * <ul>
     *   <li>如果代码开头没有package声明，添加标准包声明</li>
     *   <li>默认包名：com.ingenio.backend.service</li>
     * </ul>
     *
     * @param code 原始代码
     * @return 修复后的代码
     */
    private String fixMissingPackageDeclaration(String code) {
        // 检查是否已有package声明
        if (code.trim().startsWith("package ")) {
            log.debug("[StructureErrorFixStrategy] 代码已有package声明，跳过");
            return code;
        }

        // 添加标准包声明
        String packageDeclaration = "package com.ingenio.backend.service;\n\n";
        log.debug("[StructureErrorFixStrategy] 添加包声明: com.ingenio.backend.service");

        return packageDeclaration + code;
    }

    /**
     * 修复缺少类定义
     *
     * <p>修复策略：</p>
     * <ul>
     *   <li>如果代码没有class关键字，将代码包装在Service类中</li>
     *   <li>添加必要的import语句</li>
     *   <li>添加@Service和@Slf4j注解</li>
     * </ul>
     *
     * @param code 原始代码
     * @return 修复后的代码
     */
    private String fixMissingClassDefinition(String code) {
        // 检查是否已有class定义
        if (code.contains("class ") || code.contains("interface ") || code.contains("enum ")) {
            log.debug("[StructureErrorFixStrategy] 代码已有类定义，跳过");
            return code;
        }

        // 提取已有的package声明
        String packageDeclaration = "";
        String codeWithoutPackage = code;
        if (code.trim().startsWith("package ")) {
            int packageEnd = code.indexOf(";");
            if (packageEnd > 0) {
                packageDeclaration = code.substring(0, packageEnd + 1) + "\n";
                codeWithoutPackage = code.substring(packageEnd + 1).trim();
            }
        }

        // 添加import语句
        String imports = "\nimport lombok.extern.slf4j.Slf4j;\n" +
                "import lombok.RequiredArgsConstructor;\n" +
                "import org.springframework.stereotype.Service;\n" +
                "import org.springframework.transaction.annotation.Transactional;\n\n";

        // 将代码包装在Service类中
        String classDefinition = "@Slf4j\n" +
                "@Service\n" +
                "@RequiredArgsConstructor\n" +
                "public class GeneratedService {\n\n";

        String classEnd = "\n}";

        log.debug("[StructureErrorFixStrategy] 添加Service类定义");

        return packageDeclaration + imports + classDefinition +
                "    // 原始代码开始\n" +
                indentCode(codeWithoutPackage, 1) +
                "\n    // 原始代码结束\n" +
                classEnd;
    }

    /**
     * 修复缺少方法定义
     *
     * <p>修复策略：</p>
     * <ul>
     *   <li>如果代码片段没有完整的方法签名，将其包装在标准方法中</li>
     *   <li>添加@Transactional注解</li>
     *   <li>添加基础的日志和异常处理</li>
     * </ul>
     *
     * @param code 原始代码
     * @return 修复后的代码
     */
    private String fixMissingMethodDefinition(String code) {
        // 检查是否已有方法定义
        if (code.matches("(?s).*\\b(public|private|protected)\\s+\\w+\\s+\\w+\\s*\\([^)]*\\)\\s*\\{.*")) {
            log.debug("[StructureErrorFixStrategy] 代码已有方法定义，跳过");
            return code;
        }

        // 检查代码是否在类内部（有class关键字）
        boolean hasClassDefinition = code.contains("class ") || code.contains("interface ");

        if (!hasClassDefinition) {
            // 如果没有类定义，先修复类定义
            log.debug("[StructureErrorFixStrategy] 代码缺少类定义，先修复类定义");
            return fixMissingClassDefinition(code);
        }

        // 查找类定义的位置
        int classStart = code.indexOf("{");
        if (classStart < 0) {
            log.warn("[StructureErrorFixStrategy] 无法找到类体的开始位置");
            return code;
        }

        // 在类体开始位置后插入标准方法
        String methodDefinition = "\n\n    /**\n" +
                "     * 自动生成的业务方法\n" +
                "     */\n" +
                "    @Transactional\n" +
                "    public void executeBusinessLogic() {\n" +
                "        log.info(\"开始执行业务逻辑\");\n" +
                "        try {\n" +
                "            // 原始业务逻辑\n";

        String methodEnd = "\n            log.info(\"业务逻辑执行成功\");\n" +
                "        } catch (Exception e) {\n" +
                "            log.error(\"业务逻辑执行失败: {}\", e.getMessage(), e);\n" +
                "            throw new RuntimeException(\"业务逻辑执行失败: \" + e.getMessage(), e);\n" +
                "        }\n" +
                "    }\n";

        String beforeClass = code.substring(0, classStart + 1);
        String afterClass = code.substring(classStart + 1);

        log.debug("[StructureErrorFixStrategy] 添加标准业务方法定义");

        return beforeClass + methodDefinition +
                indentCode(afterClass, 3) +
                methodEnd;
    }

    /**
     * 为代码添加缩进
     *
     * @param code  原始代码
     * @param level 缩进级别（每级4个空格）
     * @return 缩进后的代码
     */
    private String indentCode(String code, int level) {
        String indent = "    ".repeat(level);
        String[] lines = code.split("\n");
        StringBuilder indented = new StringBuilder();

        for (String line : lines) {
            if (line.trim().isEmpty()) {
                indented.append("\n");
            } else {
                indented.append(indent).append(line).append("\n");
            }
        }

        return indented.toString();
    }
}
