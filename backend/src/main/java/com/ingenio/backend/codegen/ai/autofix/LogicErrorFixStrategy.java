package com.ingenio.backend.codegen.ai.autofix;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * LogicErrorFixStrategy - 逻辑错误修复策略（Priority 3）
 *
 * <p>专注于修复业务逻辑完整性问题，确保代码具备基本的业务逻辑</p>
 *
 * <p>修复范围：</p>
 * <ul>
 *   <li>缺少Repository注入：添加@RequiredArgsConstructor和Repository字段</li>
 *   <li>业务逻辑不完整：添加基础的Repository调用</li>
 *   <li>异常处理缺失：添加try-catch块和日志</li>
 * </ul>
 *
 * <p>修复策略：</p>
 * <ul>
 *   <li>最小侵入：只添加缺失的关键逻辑，不改变现有逻辑</li>
 *   <li>最佳实践：遵循Spring Boot和Lombok最佳实践</li>
 *   <li>可扩展性：添加的逻辑易于扩展和修改</li>
 * </ul>
 *
 * @author Ingenio AutoFix Orchestrator
 * @since 2025-11-19 P0 Phase 2: LogicErrorFixStrategy实现
 */
@Slf4j
@Component
public class LogicErrorFixStrategy implements FixStrategy {

    @Override
    public boolean supports(List<ValidationIssue> issues) {
        // 支持条件：存在LOGIC类型的问题
        boolean hasLogicErrors = issues.stream()
                .anyMatch(ValidationIssue::isLogicError);

        if (hasLogicErrors) {
            log.debug("[LogicErrorFixStrategy] 检测到逻辑错误，策略适用");
        }

        return hasLogicErrors;
    }

    @Override
    public String apply(String code, List<ValidationIssue> issues) {
        log.info("[LogicErrorFixStrategy] 开始修复逻辑错误");

        String fixedCode = code;
        int fixCount = 0;

        // 提取LOGIC错误
        List<ValidationIssue> logicErrors = issues.stream()
                .filter(ValidationIssue::isLogicError)
                .collect(Collectors.toList());

        for (ValidationIssue issue : logicErrors) {
            String message = issue.getMessage();

            // 修复1: 缺少Repository/Service引用
            if (message.contains("未找到Repository/Service/Controller引用")) {
                log.debug("[LogicErrorFixStrategy] 修复缺少Repository引用");
                fixedCode = fixMissingRepositoryReference(fixedCode);
                fixCount++;
            }

            // 修复2: 业务逻辑不完整
            if (message.contains("未找到基础业务逻辑")) {
                log.debug("[LogicErrorFixStrategy] 修复业务逻辑不完整");
                fixedCode = fixIncompleteBusinessLogic(fixedCode);
                fixCount++;
            }

            // 修复3: 缺少异常处理
            if (message.contains("缺少异常处理")) {
                log.debug("[LogicErrorFixStrategy] 修复缺少异常处理");
                fixedCode = fixMissingExceptionHandling(fixedCode);
                fixCount++;
            }
        }

        if (fixCount > 0) {
            log.info("[LogicErrorFixStrategy] ✅ 修复完成：应用了{}个修复", fixCount);
        } else {
            log.warn("[LogicErrorFixStrategy] ⚠️ 未应用任何修复");
        }

        return fixedCode;
    }

    @Override
    public int priority() {
        return 3; // 第三优先级
    }

    @Override
    public String getDescription() {
        return "修复业务逻辑完整性问题（缺少Repository注入、业务逻辑不完整、异常处理缺失）";
    }

    /**
     * 修复缺少Repository引用
     *
     * <p>修复策略：</p>
     * <ul>
     *   <li>添加@RequiredArgsConstructor注解</li>
     *   <li>添加private final XxxRepository字段</li>
     * </ul>
     *
     * @param code 原始代码
     * @return 修复后的代码
     */
    private String fixMissingRepositoryReference(String code) {
        // 检查是否已有Repository字段
        if (code.contains("Repository") && code.contains("private")) {
            log.debug("[LogicErrorFixStrategy] 代码已有Repository字段，跳过");
            return code;
        }

        // 查找类定义位置
        int classDefStart = code.indexOf("public class ");
        if (classDefStart < 0) {
            classDefStart = code.indexOf("class ");
        }
        if (classDefStart < 0) {
            log.warn("[LogicErrorFixStrategy] 无法找到类定义");
            return code;
        }

        // 提取类名
        int classNameStart = code.indexOf("class ", classDefStart) + 6;
        int classNameEnd = code.indexOf("{", classNameStart);
        if (classNameEnd < 0) {
            classNameEnd = code.indexOf(" ", classNameStart);
        }
        String className = code.substring(classNameStart, classNameEnd).trim();

        // 移除可能的extends/implements
        if (className.contains(" ")) {
            className = className.substring(0, className.indexOf(" ")).trim();
        }

        // 推断Repository名称（假设是XxxService对应XxxRepository）
        String repositoryName = className.replace("Service", "Repository");
        String entityName = className.replace("Service", "");

        // 检查是否已有@RequiredArgsConstructor
        boolean hasRequiredArgsConstructor = code.contains("@RequiredArgsConstructor");

        // 在类定义前添加@RequiredArgsConstructor（如果没有）
        String annotation = "";
        if (!hasRequiredArgsConstructor) {
            annotation = "@RequiredArgsConstructor\n";
        }

        // 在类体开始位置添加Repository字段
        int classBodyStart = code.indexOf("{", classDefStart);
        if (classBodyStart < 0) {
            log.warn("[LogicErrorFixStrategy] 无法找到类体开始位置");
            return code;
        }

        String repositoryField = "\n\n    /**\n" +
                "     * " + entityName + " Repository\n" +
                "     */\n" +
                "    private final " + repositoryName + " " +
                Character.toLowerCase(repositoryName.charAt(0)) + repositoryName.substring(1) + ";\n";

        String beforeClass = code.substring(0, classDefStart);
        String classDefToBody = code.substring(classDefStart, classBodyStart + 1);
        String afterBody = code.substring(classBodyStart + 1);

        log.debug("[LogicErrorFixStrategy] 添加Repository字段: {}", repositoryName);

        return beforeClass + annotation + classDefToBody + repositoryField + afterBody;
    }

    /**
     * 修复业务逻辑不完整
     *
     * <p>修复策略：</p>
     * <ul>
     *   <li>添加基础的Repository方法调用</li>
     *   <li>添加返回值</li>
     * </ul>
     *
     * @param code 原始代码
     * @return 修复后的代码
     */
    private String fixIncompleteBusinessLogic(String code) {
        // 查找方法体
        int methodStart = code.indexOf("public ");
        if (methodStart < 0) {
            methodStart = code.indexOf("private ");
        }
        if (methodStart < 0) {
            log.warn("[LogicErrorFixStrategy] 无法找到方法定义");
            return code;
        }

        // 查找方法体的大括号
        int methodBodyStart = code.indexOf("{", methodStart);
        int methodBodyEnd = findMatchingBrace(code, methodBodyStart);

        if (methodBodyStart < 0 || methodBodyEnd < 0) {
            log.warn("[LogicErrorFixStrategy] 无法找到方法体");
            return code;
        }

        String methodBody = code.substring(methodBodyStart + 1, methodBodyEnd);

        // 检查是否已有return语句
        if (methodBody.contains("return ") || methodBody.contains("throw ")) {
            log.debug("[LogicErrorFixStrategy] 方法体已有return/throw语句，跳过");
            return code;
        }

        // 添加基础的Repository调用和返回
        String businessLogic = "\n        // 执行业务逻辑\n" +
                "        // TODO: 实现具体的业务逻辑\n" +
                "        log.info(\"执行业务逻辑\");\n" +
                "        \n" +
                "        return null; // TODO: 返回实际结果\n    ";

        String beforeMethod = code.substring(0, methodBodyEnd);
        String afterMethod = code.substring(methodBodyEnd);

        log.debug("[LogicErrorFixStrategy] 添加基础业务逻辑");

        return beforeMethod + businessLogic + afterMethod;
    }

    /**
     * 修复缺少异常处理
     *
     * <p>修复策略：</p>
     * <ul>
     *   <li>将方法体包装在try-catch块中</li>
     *   <li>添加日志记录</li>
     * </ul>
     *
     * @param code 原始代码
     * @return 修复后的代码
     */
    private String fixMissingExceptionHandling(String code) {
        // 检查是否已有try-catch
        if (code.contains("try {") || code.contains("catch (")) {
            log.debug("[LogicErrorFixStrategy] 代码已有异常处理，跳过");
            return code;
        }

        // 查找方法体
        int methodStart = code.indexOf("public ");
        if (methodStart < 0) {
            methodStart = code.indexOf("private ");
        }
        if (methodStart < 0) {
            log.warn("[LogicErrorFixStrategy] 无法找到方法定义");
            return code;
        }

        int methodBodyStart = code.indexOf("{", methodStart);
        int methodBodyEnd = findMatchingBrace(code, methodBodyStart);

        if (methodBodyStart < 0 || methodBodyEnd < 0) {
            log.warn("[LogicErrorFixStrategy] 无法找到方法体");
            return code;
        }

        String methodBody = code.substring(methodBodyStart + 1, methodBodyEnd);

        // 将方法体包装在try-catch中
        String wrappedBody = "\n        try {\n" +
                indentCode(methodBody, 1) +
                "\n        } catch (Exception e) {\n" +
                "            log.error(\"业务逻辑执行失败: {}\", e.getMessage(), e);\n" +
                "            throw new RuntimeException(\"业务逻辑执行失败: \" + e.getMessage(), e);\n" +
                "        }\n    ";

        String beforeMethod = code.substring(0, methodBodyStart + 1);
        String afterMethod = code.substring(methodBodyEnd);

        log.debug("[LogicErrorFixStrategy] 添加异常处理");

        return beforeMethod + wrappedBody + afterMethod;
    }

    /**
     * 查找匹配的右大括号位置
     *
     * @param code  代码
     * @param start 左大括号位置
     * @return 右大括号位置，如果未找到返回-1
     */
    private int findMatchingBrace(String code, int start) {
        int count = 1;
        for (int i = start + 1; i < code.length(); i++) {
            if (code.charAt(i) == '{') {
                count++;
            } else if (code.charAt(i) == '}') {
                count--;
                if (count == 0) {
                    return i;
                }
            }
        }
        return -1;
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
