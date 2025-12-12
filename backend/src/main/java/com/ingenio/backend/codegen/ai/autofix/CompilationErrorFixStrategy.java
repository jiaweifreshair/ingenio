package com.ingenio.backend.codegen.ai.autofix;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * CompilationErrorFixStrategy - 编译错误修复策略（Priority 1）
 *
 * <p>专注于修复阻塞编译的语法错误，是最高优先级的修复策略</p>
 *
 * <p>修复范围：</p>
 * <ul>
 *   <li>括号不匹配：自动补齐缺失的括号</li>
 *   <li>分号缺失：在语句行末尾添加分号</li>
 *   <li>关键字拼写错误：修正常见的关键字拼写错误</li>
 * </ul>
 *
 * <p>修复策略：</p>
 * <ul>
 *   <li>保守修复：只修复明确的语法错误，不改变代码逻辑</li>
 *   <li>快速修复：使用规则引擎而非AI，确保1秒内完成</li>
 *   <li>幂等性：多次应用不会产生副作用</li>
 * </ul>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * CompilationErrorFixStrategy strategy = new CompilationErrorFixStrategy();
 * List<ValidationIssue> syntaxErrors = issues.stream()
 *     .filter(issue -> issue.isSyntaxError())
 *     .collect(Collectors.toList());
 *
 * if (strategy.supports(syntaxErrors)) {
 *     String fixedCode = strategy.apply(code, syntaxErrors);
 * }
 * }</pre>
 *
 * @author Ingenio AutoFix Orchestrator
 * @since 2025-11-19 P0 Phase 2: CompilationErrorFixStrategy实现
 */
@Slf4j
@Component
public class CompilationErrorFixStrategy implements FixStrategy {

    @Override
    public boolean supports(List<ValidationIssue> issues) {
        // 支持条件：存在SYNTAX类型的ERROR问题
        boolean hasSyntaxErrors = issues.stream()
                .anyMatch(issue -> issue.isSyntaxError() && issue.isError());

        if (hasSyntaxErrors) {
            log.debug("[CompilationErrorFixStrategy] 检测到语法错误，策略适用");
        }

        return hasSyntaxErrors;
    }

    @Override
    public String apply(String code, List<ValidationIssue> issues) {
        log.info("[CompilationErrorFixStrategy] 开始修复编译错误");

        String fixedCode = code;
        int fixCount = 0;

        // 提取SYNTAX错误
        List<ValidationIssue> syntaxErrors = issues.stream()
                .filter(issue -> issue.isSyntaxError() && issue.isError())
                .collect(Collectors.toList());

        for (ValidationIssue issue : syntaxErrors) {
            String message = issue.getMessage();

            // 修复1: 括号不匹配
            if (message.contains("括号不匹配")) {
                log.debug("[CompilationErrorFixStrategy] 修复括号不匹配");
                fixedCode = fixBracketMismatch(fixedCode);
                fixCount++;
            }

            // 修复2: 分号缺失
            if (message.contains("缺少分号") || message.contains("分号使用不当")) {
                log.debug("[CompilationErrorFixStrategy] 修复分号缺失");
                fixedCode = fixMissingSemicolons(fixedCode);
                fixCount++;
            }

            // 修复3: 关键字拼写错误
            if (message.contains("拼写错误的关键字")) {
                log.debug("[CompilationErrorFixStrategy] 修复关键字拼写错误");
                fixedCode = fixKeywordSpelling(fixedCode);
                fixCount++;
            }
        }

        if (fixCount > 0) {
            log.info("[CompilationErrorFixStrategy] ✅ 修复完成：应用了{}个修复", fixCount);
        } else {
            log.warn("[CompilationErrorFixStrategy] ⚠️ 未应用任何修复");
        }

        return fixedCode;
    }

    @Override
    public int priority() {
        return 1; // 最高优先级
    }

    @Override
    public String getDescription() {
        return "修复阻塞编译的语法错误（括号不匹配、分号缺失、关键字拼写错误）";
    }

    /**
     * 修复括号不匹配
     *
     * <p>修复策略：</p>
     * <ul>
     *   <li>统计各类括号的开闭数量</li>
     *   <li>如果左括号多于右括号，在代码末尾添加右括号</li>
     *   <li>如果右括号多于左括号，在代码开头添加左括号（较少见）</li>
     * </ul>
     *
     * @param code 原始代码
     * @return 修复后的代码
     */
    private String fixBracketMismatch(String code) {
        // 统计括号数量
        int braces = 0;        // {}
        int brackets = 0;      // []
        int parentheses = 0;   // ()

        for (char c : code.toCharArray()) {
            switch (c) {
                case '{' -> braces++;
                case '}' -> braces--;
                case '[' -> brackets++;
                case ']' -> brackets--;
                case '(' -> parentheses++;
                case ')' -> parentheses--;
            }
        }

        StringBuilder fixed = new StringBuilder(code);

        // 修复大括号不匹配
        if (braces > 0) {
            // 左括号多，补齐右括号
            for (int i = 0; i < braces; i++) {
                fixed.append("\n}");
            }
            log.debug("[CompilationErrorFixStrategy] 补齐了{}个右大括号", braces);
        } else if (braces < 0) {
            // 右括号多，在开头补齐左括号（较少见，可能是代码片段）
            for (int i = 0; i < -braces; i++) {
                fixed.insert(0, "{\n");
            }
            log.debug("[CompilationErrorFixStrategy] 补齐了{}个左大括号", -braces);
        }

        // 修复方括号不匹配
        if (brackets > 0) {
            for (int i = 0; i < brackets; i++) {
                fixed.append("]");
            }
            log.debug("[CompilationErrorFixStrategy] 补齐了{}个右方括号", brackets);
        } else if (brackets < 0) {
            for (int i = 0; i < -brackets; i++) {
                fixed.insert(0, "[");
            }
            log.debug("[CompilationErrorFixStrategy] 补齐了{}个左方括号", -brackets);
        }

        // 修复圆括号不匹配
        if (parentheses > 0) {
            for (int i = 0; i < parentheses; i++) {
                fixed.append(")");
            }
            log.debug("[CompilationErrorFixStrategy] 补齐了{}个右圆括号", parentheses);
        } else if (parentheses < 0) {
            for (int i = 0; i < -parentheses; i++) {
                fixed.insert(0, "(");
            }
            log.debug("[CompilationErrorFixStrategy] 补齐了{}个左圆括号", -parentheses);
        }

        return fixed.toString();
    }

    /**
     * 修复分号缺失
     *
     * <p>修复策略：</p>
     * <ul>
     *   <li>识别需要分号的语句行（赋值、方法调用、return等）</li>
     *   <li>在语句行末尾添加分号</li>
     *   <li>跳过不需要分号的行（类定义、方法定义、花括号等）</li>
     * </ul>
     *
     * @param code 原始代码
     * @return 修复后的代码
     */
    private String fixMissingSemicolons(String code) {
        String[] lines = code.split("\n");
        StringBuilder fixed = new StringBuilder();

        for (String line : lines) {
            String trimmedLine = line.trim();

            // 跳过空行
            if (trimmedLine.isEmpty()) {
                fixed.append(line).append("\n");
                continue;
            }

            // 跳过注释行
            if (trimmedLine.startsWith("//") || trimmedLine.startsWith("/*") || trimmedLine.startsWith("*")) {
                fixed.append(line).append("\n");
                continue;
            }

            // 跳过已有分号的行
            if (trimmedLine.endsWith(";")) {
                fixed.append(line).append("\n");
                continue;
            }

            // 跳过不需要分号的行
            if (trimmedLine.endsWith("{") || trimmedLine.endsWith("}") ||
                    trimmedLine.startsWith("package ") || trimmedLine.startsWith("import ") ||
                    trimmedLine.startsWith("@") || trimmedLine.startsWith("public class") ||
                    trimmedLine.startsWith("private class") || trimmedLine.startsWith("public interface") ||
                    trimmedLine.contains("class ") || trimmedLine.contains("interface ") ||
                    trimmedLine.contains("enum ")) {
                fixed.append(line).append("\n");
                continue;
            }

            // 识别需要分号的语句行
            boolean needsSemicolon = trimmedLine.contains("=") ||  // 赋值语句
                    trimmedLine.startsWith("return ") ||           // return语句
                    trimmedLine.contains(".") ||                   // 方法调用
                    trimmedLine.startsWith("throw ");              // throw语句

            if (needsSemicolon) {
                fixed.append(line).append(";").append("\n");
                log.debug("[CompilationErrorFixStrategy] 为行添加分号: {}", trimmedLine.substring(0, Math.min(40, trimmedLine.length())));
            } else {
                fixed.append(line).append("\n");
            }
        }

        return fixed.toString();
    }

    /**
     * 修复关键字拼写错误
     *
     * <p>修复策略：</p>
     * <ul>
     *   <li>替换常见的关键字拼写错误</li>
     *   <li>使用词边界匹配确保只替换独立单词</li>
     * </ul>
     *
     * @param code 原始代码
     * @return 修复后的代码
     */
    private String fixKeywordSpelling(String code) {
        // 常见拼写错误映射
        String[][] spellingFixes = {
                {"pubilc", "public"},
                {"priavte", "private"},
                {"protecetd", "protected"},
                {"staitc", "static"},
                {"fianl", "final"},
                {"retrun", "return"},
                {"throew", "throw"},
                {"clas\\b", "class"},           // 使用词边界，避免匹配"class"
                {"intrface", "interface"},
                {"impelments", "implements"}
        };

        String fixed = code;
        int fixCount = 0;

        for (String[] fix : spellingFixes) {
            String wrongSpelling = fix[0];
            String correctSpelling = fix[1];

            // 使用词边界正则表达式替换
            String pattern = "\\b" + wrongSpelling;
            String beforeFix = fixed;
            fixed = fixed.replaceAll(pattern, correctSpelling);

            if (!fixed.equals(beforeFix)) {
                fixCount++;
                log.debug("[CompilationErrorFixStrategy] 修正拼写错误: {} -> {}", wrongSpelling, correctSpelling);
            }
        }

        if (fixCount > 0) {
            log.debug("[CompilationErrorFixStrategy] 修正了{}个关键字拼写错误", fixCount);
        }

        return fixed;
    }
}
