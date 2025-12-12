<#-- VALIDATION规则代码生成模板（V2.0 Phase 4.2.2）-->
<#--
输入参数Map示例：
{
  "patternType": "RANGE_CHECK" | "FORMAT_CHECK" | "UNIQUENESS_CHECK" | "NOT_NULL_CHECK" | "LENGTH_CHECK",
  "fieldName": "age",
  "entityName": "User",
  "getter": "getAge",
  "operator": ">=",
  "threshold": "18",
  "thresholdMin": "3",
  "thresholdMax": "20",
  "regex": "^[a-zA-Z0-9._%+-]+@...",
  "errorCode": "INVALID_AGE",
  "errorMessage": "用户年龄必须≥18岁"
}
-->
<#if patternType == "RANGE_CHECK">
<#-- 范围检查模式 -->
<#if operator == ">=">
// VALIDATION规则：${errorMessage}
if (${entityVarName}.${getter}() < ${threshold}) {
    throw new BusinessException(ErrorCode.${errorCode}, "${errorMessage}");
}
<#elseif operator == ">">
// VALIDATION规则：${errorMessage}
if (${entityVarName}.${getter}() <= ${threshold}) {
    throw new BusinessException(ErrorCode.${errorCode}, "${errorMessage}");
}
<#elseif operator == "<=">
// VALIDATION规则：${errorMessage}
if (${entityVarName}.${getter}() > ${threshold}) {
    throw new BusinessException(ErrorCode.${errorCode}, "${errorMessage}");
}
<#elseif operator == "<">
// VALIDATION规则：${errorMessage}
if (${entityVarName}.${getter}() >= ${threshold}) {
    throw new BusinessException(ErrorCode.${errorCode}, "${errorMessage}");
}
<#elseif operator == "==">
// VALIDATION规则：${errorMessage}
if (!${entityVarName}.${getter}().equals(${threshold})) {
    throw new BusinessException(ErrorCode.${errorCode}, "${errorMessage}");
}
<#elseif operator == "!=">
// VALIDATION规则：${errorMessage}
if (${entityVarName}.${getter}().equals(${threshold})) {
    throw new BusinessException(ErrorCode.${errorCode}, "${errorMessage}");
}
<#elseif operator == "between">
// VALIDATION规则：${errorMessage}
if (${entityVarName}.${getter}() < ${thresholdMin} || ${entityVarName}.${getter}() > ${thresholdMax}) {
    throw new BusinessException(ErrorCode.${errorCode}, "${errorMessage}");
}
</#if>

<#elseif patternType == "FORMAT_CHECK">
<#-- 格式验证模式 -->
// VALIDATION规则：${errorMessage}
String ${fieldName}Value = ${entityVarName}.${getter}();
if (${fieldName}Value != null && !${fieldName}Value.matches("${regex}")) {
    throw new BusinessException(ErrorCode.${errorCode}, "${errorMessage}");
}

<#elseif patternType == "NOT_NULL_CHECK">
<#-- 非空检查模式 -->
// VALIDATION规则：${errorMessage}
if (${entityVarName}.${getter}() == null) {
    throw new BusinessException(ErrorCode.${errorCode}, "${errorMessage}");
}

<#elseif patternType == "LENGTH_CHECK">
<#-- 长度限制模式 -->
<#if operator == "between">
// VALIDATION规则：${errorMessage}
String ${fieldName}Value = ${entityVarName}.${getter}();
if (${fieldName}Value != null && (${fieldName}Value.length() < ${thresholdMin} || ${fieldName}Value.length() > ${thresholdMax})) {
    throw new BusinessException(ErrorCode.${errorCode}, "${errorMessage}");
}
<#elseif operator == ">=">
// VALIDATION规则：${errorMessage}
String ${fieldName}Value = ${entityVarName}.${getter}();
if (${fieldName}Value != null && ${fieldName}Value.length() < ${threshold}) {
    throw new BusinessException(ErrorCode.${errorCode}, "${errorMessage}");
}
<#elseif operator == "<=">
// VALIDATION规则：${errorMessage}
String ${fieldName}Value = ${entityVarName}.${getter}();
if (${fieldName}Value != null && ${fieldName}Value.length() > ${threshold}) {
    throw new BusinessException(ErrorCode.${errorCode}, "${errorMessage}");
}
</#if>

<#elseif patternType == "UNIQUENESS_CHECK">
<#-- 唯一性校验模式 -->
// VALIDATION规则：${errorMessage}
// TODO: Phase 4.3 - 实现唯一性校验（需要Repository查询）
// 示例实现：
// if (${entityVarName?uncap_first}Repository.existsBy${fieldName?cap_first}(${entityVarName}.${getter}())) {
//     throw new BusinessException(ErrorCode.${errorCode}, "${errorMessage}");
// }

<#else>
<#-- 未知模式 -->
// TODO: 未知VALIDATION模式 - ${patternType}
// 字段: ${fieldName}, 错误信息: ${errorMessage}

</#if>
