package com.ingenio.backend.entity.g3;

import lombok.extern.slf4j.Slf4j;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * G3 错误签名计算器
 *
 * 用于检测"相同错误"，避免 Coach 重复修复同一类错误。
 *
 * 签名计算规则：
 * 1. 提取错误类型（cannot find symbol / incompatible types / ...）
 * 2. 提取错误涉及的符号/类型
 * 3. 忽略具体行号，聚焦错误本质
 * 4. 生成确定性哈希作为签名
 *
 * @author Ingenio Team
 * @since 2.1.0
 */
@Slf4j
public class G3ErrorSignature {

    /**
     * 已知的 Java 编译错误模式
     */
    private static final List<ErrorPattern> ERROR_PATTERNS = List.of(
            new ErrorPattern("SYMBOL_NOT_FOUND",
                    Pattern.compile("cannot find symbol.*?symbol:\\s*(?:class|variable|method)\\s+(\\w+)",
                            Pattern.CASE_INSENSITIVE | Pattern.DOTALL)),
            new ErrorPattern("INCOMPATIBLE_TYPES",
                    Pattern.compile("incompatible types:.*?(?:required|found):\\s*(\\S+)", Pattern.CASE_INSENSITIVE)),
            new ErrorPattern("PACKAGE_NOT_EXIST",
                    Pattern.compile("package\\s+(\\S+)\\s+does not exist", Pattern.CASE_INSENSITIVE)),
            new ErrorPattern("METHOD_NOT_APPLICABLE",
                    Pattern.compile("method\\s+(\\w+).*?cannot be applied", Pattern.CASE_INSENSITIVE)),
            new ErrorPattern("UNREPORTED_EXCEPTION",
                    Pattern.compile("unreported exception\\s+(\\S+)", Pattern.CASE_INSENSITIVE)),
            new ErrorPattern("MISSING_RETURN", Pattern.compile("missing return statement", Pattern.CASE_INSENSITIVE)),
            new ErrorPattern("SYNTAX_ERROR",
                    Pattern.compile("(';'|'\\)'|'\\{'|'\\}')\\s*expected", Pattern.CASE_INSENSITIVE)),
            new ErrorPattern("ILLEGAL_START",
                    Pattern.compile("illegal start of (expression|type)", Pattern.CASE_INSENSITIVE)),

            // Maven / 构建类错误
            new ErrorPattern("DEPENDENCY_RESOLVE",
                    Pattern.compile("could not resolve dependencies.*?artifact\\s+(\\S+)",
                            Pattern.CASE_INSENSITIVE | Pattern.DOTALL)),
            new ErrorPattern("ARTIFACT_NOT_FOUND",
                    Pattern.compile("could not find artifact\\s+(\\S+)", Pattern.CASE_INSENSITIVE)),
            new ErrorPattern("PARENT_POM_ERROR",
                    Pattern.compile("non-resolvable parent pom", Pattern.CASE_INSENSITIVE)),
            new ErrorPattern("PLUGIN_ERROR",
                    Pattern.compile("failed to execute goal\\s+(\\S+)", Pattern.CASE_INSENSITIVE)));

    /**
     * 从编译输出计算错误签名
     *
     * @param compilerOutput 编译器输出（stdout + stderr）
     * @return 错误签名（稳定的字符串标识）
     */
    public static String compute(String compilerOutput) {
        if (compilerOutput == null || compilerOutput.isBlank()) {
            return "EMPTY_OUTPUT";
        }

        // 提取所有匹配的错误模式
        List<String> extractedErrors = extractErrors(compilerOutput);

        if (extractedErrors.isEmpty()) {
            // 兜底：使用原始输出的摘要哈希
            return "UNKNOWN_" + hashText(normalizeOutput(compilerOutput));
        }

        // 对提取的错误排序并拼接，确保签名稳定
        Collections.sort(extractedErrors);
        String combined = String.join("|", extractedErrors);

        return hashText(combined);
    }

    /**
     * 从 ParsedError 列表计算错误签名
     *
     * @param parsedErrors 解析后的错误列表
     * @return 错误签名
     */
    public static String compute(List<G3ValidationResultEntity.ParsedError> parsedErrors) {
        if (parsedErrors == null || parsedErrors.isEmpty()) {
            return "NO_PARSED_ERRORS";
        }

        List<String> errorStrings = parsedErrors.stream()
                .filter(Objects::nonNull)
                .map(G3ErrorSignature::normalizeError)
                .sorted()
                .toList();

        if (errorStrings.isEmpty()) {
            return "NO_PARSED_ERRORS";
        }

        String combined = String.join("|", errorStrings);
        return hashText(combined);
    }

    /**
     * 从编译输出和 ParsedError 综合计算签名
     *
     * @param compilerOutput 编译器输出
     * @param parsedErrors   解析后的错误列表
     * @return 综合错误签名
     */
    public static String computeCombined(String compilerOutput,
            List<G3ValidationResultEntity.ParsedError> parsedErrors) {
        String sig1 = compute(compilerOutput);
        String sig2 = compute(parsedErrors);

        // 优先使用 parsedErrors（更结构化）
        if (!"NO_PARSED_ERRORS".equals(sig2)) {
            return sig2;
        }

        return sig1;
    }

    /**
     * 提取错误模式
     */
    private static List<String> extractErrors(String output) {
        List<String> results = new ArrayList<>();

        for (ErrorPattern pattern : ERROR_PATTERNS) {
            Matcher matcher = pattern.pattern().matcher(output);
            while (matcher.find()) {
                String extracted = pattern.type();
                // 如果有捕获组，附加具体符号
                if (matcher.groupCount() > 0 && matcher.group(1) != null) {
                    extracted += ":" + normalizeSymbol(matcher.group(1));
                }
                results.add(extracted);
            }
        }

        // 去重
        return results.stream().distinct().collect(Collectors.toList());
    }

    /**
     * 归一化符号名（移除包前缀、泛型参数等）
     */
    private static String normalizeSymbol(String symbol) {
        if (symbol == null)
            return "";

        // 移除泛型参数
        symbol = symbol.replaceAll("<[^>]+>", "");

        // 只保留类名（去掉包路径）
        if (symbol.contains(".")) {
            symbol = symbol.substring(symbol.lastIndexOf('.') + 1);
        }

        return symbol.trim().toLowerCase();
    }

    /**
     * 归一化 ParsedError
     */
    private static String normalizeError(G3ValidationResultEntity.ParsedError error) {
        StringBuilder sb = new StringBuilder();

        // 文件名（不含路径和行号）
        if (error.getFile() != null) {
            String file = error.getFile();
            if (file.contains("/")) {
                file = file.substring(file.lastIndexOf('/') + 1);
            }
            sb.append(file).append(":");
        }

        // 错误消息的核心部分
        if (error.getMessage() != null) {
            String msg = error.getMessage().toLowerCase();
            // 移除行号和列号信息
            msg = msg.replaceAll("\\[\\d+,\\d+\\]", "");
            msg = msg.replaceAll(":\\d+:", ":");
            sb.append(msg.trim());
        }

        return sb.toString();
    }

    /**
     * 归一化编译输出（移除时间戳、行号等变化部分）
     */
    private static String normalizeOutput(String output) {
        if (output == null)
            return "";

        // 移除时间戳
        output = output.replaceAll("\\d{4}-\\d{2}-\\d{2}[T ]\\d{2}:\\d{2}:\\d{2}", "");

        // 移除行号
        output = output.replaceAll(":\\d+:", ":");
        output = output.replaceAll("\\[\\d+,\\d+\\]", "");

        // 移除路径前缀
        output = output.replaceAll("/[a-zA-Z0-9_/.-]+/([A-Z][a-zA-Z0-9]+\\.java)", "$1");

        // 移除多余空白
        output = output.replaceAll("\\s+", " ").trim();

        // 取摘要（避免签名过长）
        if (output.length() > 500) {
            output = output.substring(0, 500);
        }

        return output;
    }

    /**
     * 计算文本哈希
     */
    private static String hashText(String text) {
        if (text == null || text.isBlank()) {
            return "EMPTY";
        }

        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(text.getBytes());

            // 取前 8 字节作为简短签名
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 8 && i < hash.length; i++) {
                sb.append(String.format("%02x", hash[i]));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            log.warn("[G3ErrorSignature] SHA-256 不可用，使用 hashCode 兜底");
            return "HASH_" + Math.abs(text.hashCode());
        }
    }

    /**
     * 判断两个签名是否表示相同类型的错误
     *
     * @param sig1 签名1
     * @param sig2 签名2
     * @return 是否相同
     */
    public static boolean isSameError(String sig1, String sig2) {
        if (sig1 == null || sig2 == null)
            return false;
        return sig1.equals(sig2);
    }

    /**
     * 获取错误签名的人类可读描述
     *
     * @param compilerOutput 编译输出
     * @return 错误类型描述
     */
    public static String getErrorTypeDescription(String compilerOutput) {
        if (compilerOutput == null || compilerOutput.isBlank()) {
            return "未知错误";
        }

        for (ErrorPattern pattern : ERROR_PATTERNS) {
            if (pattern.pattern().matcher(compilerOutput).find()) {
                return switch (pattern.type()) {
                    case "SYMBOL_NOT_FOUND" -> "符号未找到";
                    case "INCOMPATIBLE_TYPES" -> "类型不兼容";
                    case "PACKAGE_NOT_EXIST" -> "包不存在";
                    case "METHOD_NOT_APPLICABLE" -> "方法参数错误";
                    case "UNREPORTED_EXCEPTION" -> "未处理异常";
                    case "MISSING_RETURN" -> "缺少返回语句";
                    case "SYNTAX_ERROR" -> "语法错误";
                    case "ILLEGAL_START" -> "表达式语法错误";
                    case "DEPENDENCY_RESOLVE" -> "依赖解析失败";
                    case "ARTIFACT_NOT_FOUND" -> "找不到依赖";
                    case "PARENT_POM_ERROR" -> "父 POM 错误";
                    case "PLUGIN_ERROR" -> "插件执行失败";
                    default -> pattern.type();
                };
            }
        }

        return "其他编译错误";
    }

    /**
     * 错误模式记录
     */
    private record ErrorPattern(String type, Pattern pattern) {
    }
}
