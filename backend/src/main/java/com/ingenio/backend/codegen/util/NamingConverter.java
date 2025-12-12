package com.ingenio.backend.codegen.util;

/**
 * 命名转换工具类（V2.0 Phase 3.5）
 *
 * <p>提供各种命名格式之间的转换功能</p>
 *
 * <p>核心功能：</p>
 * <ul>
 *   <li>snake_case → PascalCase（toPascalCase）</li>
 *   <li>snake_case → camelCase（toCamelCase）</li>
 *   <li>单数 → 复数（toPlural）</li>
 *   <li>首字母大写（capitalize）</li>
 * </ul>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * // snake_case → PascalCase
 * String pascalCase = NamingConverter.toPascalCase("user_profile");  // "UserProfile"
 *
 * // snake_case → camelCase
 * String camelCase = NamingConverter.toCamelCase("user_profile");    // "userProfile"
 *
 * // 单数 → 复数
 * String plural = NamingConverter.toPlural("user");                   // "users"
 * String plural2 = NamingConverter.toPlural("category");              // "categories"
 * }</pre>
 *
 * @author Justin
 * @since 2025-11-17 V2.0 Phase 3.5: 命名转换工具类
 */
public class NamingConverter {

    /**
     * 将snake_case转换为PascalCase（首字母大写）
     *
     * <p>示例：</p>
     * <ul>
     *   <li>user_profile → UserProfile</li>
     *   <li>app_settings → AppSettings</li>
     *   <li>users → Users</li>
     * </ul>
     *
     * @param snakeCase snake_case格式的字符串
     * @return PascalCase格式的字符串
     */
    public static String toPascalCase(String snakeCase) {
        if (snakeCase == null || snakeCase.isEmpty()) {
            return snakeCase;
        }

        // 分割snake_case
        String[] parts = snakeCase.split("_");
        StringBuilder pascalCase = new StringBuilder();

        for (String part : parts) {
            if (!part.isEmpty()) {
                // 首字母大写
                pascalCase.append(Character.toUpperCase(part.charAt(0)));
                if (part.length() > 1) {
                    pascalCase.append(part.substring(1).toLowerCase());
                }
            }
        }

        return pascalCase.toString();
    }

    /**
     * 将snake_case转换为camelCase（首字母小写）
     *
     * <p>示例：</p>
     * <ul>
     *   <li>user_profile → userProfile</li>
     *   <li>app_settings → appSettings</li>
     *   <li>user_id → userId</li>
     * </ul>
     *
     * @param snakeCase snake_case格式的字符串
     * @return camelCase格式的字符串
     */
    public static String toCamelCase(String snakeCase) {
        if (snakeCase == null || snakeCase.isEmpty()) {
            return snakeCase;
        }

        String[] parts = snakeCase.split("_");
        StringBuilder camelCase = new StringBuilder(parts[0].toLowerCase());

        for (int i = 1; i < parts.length; i++) {
            String part = parts[i];
            if (!part.isEmpty()) {
                camelCase.append(Character.toUpperCase(part.charAt(0)));
                if (part.length() > 1) {
                    camelCase.append(part.substring(1).toLowerCase());
                }
            }
        }

        return camelCase.toString();
    }

    /**
     * 将PascalCase转换为camelCase（首字母小写）
     *
     * <p>示例：</p>
     * <ul>
     *   <li>UserProfile → userProfile</li>
     *   <li>AppSettings → appSettings</li>
     * </ul>
     *
     * @param pascalCase PascalCase格式的字符串
     * @return camelCase格式的字符串
     */
    public static String toCamelCase(String pascalCase, boolean fromPascalCase) {
        if (!fromPascalCase) {
            return toCamelCase(pascalCase);
        }

        if (pascalCase == null || pascalCase.isEmpty()) {
            return pascalCase;
        }

        return Character.toLowerCase(pascalCase.charAt(0)) + pascalCase.substring(1);
    }

    /**
     * 将单词转换为复数形式
     *
     * <p>支持常见的复数规则：</p>
     * <ul>
     *   <li>以s、x、z、ch、sh结尾 → 加es（box → boxes、watch → watches）</li>
     *   <li>以辅音字母+y结尾 → 变y为ies（category → categories、entity → entities）</li>
     *   <li>以元音字母+y结尾 → 加s（day → days、key → keys）</li>
     *   <li>以f或fe结尾 → 变f/fe为ves（knife → knives、life → lives）</li>
     *   <li>其他 → 加s（user → users、project → projects）</li>
     * </ul>
     *
     * <p>特殊不规则复数：</p>
     * <ul>
     *   <li>person → people</li>
     *   <li>child → children</li>
     *   <li>man → men</li>
     *   <li>woman → women</li>
     *   <li>tooth → teeth</li>
     *   <li>foot → feet</li>
     *   <li>mouse → mice</li>
     * </ul>
     *
     * @param singular 单数形式的单词
     * @return 复数形式的单词
     */
    public static String toPlural(String singular) {
        if (singular == null || singular.isEmpty()) {
            return singular;
        }

        String lowerSingular = singular.toLowerCase();

        // 特殊不规则复数
        switch (lowerSingular) {
            case "person":
                return "people";
            case "child":
                return "children";
            case "man":
                return "men";
            case "woman":
                return "women";
            case "tooth":
                return "teeth";
            case "foot":
                return "feet";
            case "mouse":
                return "mice";
            case "goose":
                return "geese";
            default:
                break;
        }

        // 以s、x、z、ch、sh结尾 → 加es
        if (lowerSingular.endsWith("s") || lowerSingular.endsWith("x") ||
            lowerSingular.endsWith("z") || lowerSingular.endsWith("ch") ||
            lowerSingular.endsWith("sh")) {
            return singular + "es";
        }

        // 以辅音字母+y结尾 → 变y为ies
        if (lowerSingular.endsWith("y") && lowerSingular.length() > 1) {
            char beforeY = lowerSingular.charAt(lowerSingular.length() - 2);
            if (!isVowel(beforeY)) {
                return singular.substring(0, singular.length() - 1) + "ies";
            }
        }

        // 以f或fe结尾 → 变f/fe为ves
        if (lowerSingular.endsWith("fe")) {
            return singular.substring(0, singular.length() - 2) + "ves";
        }
        if (lowerSingular.endsWith("f")) {
            return singular.substring(0, singular.length() - 1) + "ves";
        }

        // 默认 → 加s
        return singular + "s";
    }

    /**
     * 将复数形式转换为单数形式
     *
     * <p>支持常见的反向复数规则：</p>
     * <ul>
     *   <li>以ies结尾 → 变ies为y（categories → category、entities → entity）</li>
     *   <li>以es结尾（非ies） → 去掉es（boxes → box、watches → watch）</li>
     *   <li>以ves结尾 → 变ves为f或fe（knives → knife、lives → life）</li>
     *   <li>以s结尾 → 去掉s（users → user、projects → project）</li>
     * </ul>
     *
     * <p>特殊不规则单数：</p>
     * <ul>
     *   <li>people → person</li>
     *   <li>children → child</li>
     *   <li>men → man</li>
     *   <li>women → woman</li>
     *   <li>teeth → tooth</li>
     *   <li>feet → foot</li>
     *   <li>mice → mouse</li>
     * </ul>
     *
     * @param plural 复数形式的单词
     * @return 单数形式的单词
     */
    public static String toSingular(String plural) {
        if (plural == null || plural.isEmpty()) {
            return plural;
        }

        String lowerPlural = plural.toLowerCase();

        // 特殊不规则单数
        switch (lowerPlural) {
            case "people":
                return "person";
            case "children":
                return "child";
            case "men":
                return "man";
            case "women":
                return "woman";
            case "teeth":
                return "tooth";
            case "feet":
                return "foot";
            case "mice":
                return "mouse";
            case "geese":
                return "goose";
            default:
                break;
        }

        // 以ies结尾 → 变ies为y
        if (lowerPlural.endsWith("ies") && lowerPlural.length() > 3) {
            return plural.substring(0, plural.length() - 3) + "y";
        }

        // 以ves结尾 → 变ves为f
        if (lowerPlural.endsWith("ves") && lowerPlural.length() > 3) {
            return plural.substring(0, plural.length() - 3) + "f";
        }

        // 以ses、xes、zes、ches、shes结尾 → 去掉es
        if ((lowerPlural.endsWith("ses") || lowerPlural.endsWith("xes") ||
             lowerPlural.endsWith("zes") || lowerPlural.endsWith("ches") ||
             lowerPlural.endsWith("shes")) && lowerPlural.length() > 2) {
            return plural.substring(0, plural.length() - 2);
        }

        // 以s结尾（但不是ss） → 去掉s
        if (lowerPlural.endsWith("s") && !lowerPlural.endsWith("ss") && lowerPlural.length() > 1) {
            return plural.substring(0, plural.length() - 1);
        }

        // 默认返回原词（可能本身就是单数）
        return plural;
    }

    /**
     * 首字母大写
     *
     * <p>示例：</p>
     * <ul>
     *   <li>user → User</li>
     *   <li>appSettings → AppSettings</li>
     * </ul>
     *
     * @param str 输入字符串
     * @return 首字母大写的字符串
     */
    public static String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return Character.toUpperCase(str.charAt(0)) + str.substring(1);
    }

    /**
     * 判断字符是否为元音字母
     *
     * @param c 字符
     * @return true表示元音字母，false表示辅音字母
     */
    private static boolean isVowel(char c) {
        char lower = Character.toLowerCase(c);
        return lower == 'a' || lower == 'e' || lower == 'i' || lower == 'o' || lower == 'u';
    }
}
