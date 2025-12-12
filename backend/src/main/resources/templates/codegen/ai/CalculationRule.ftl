<#-- CALCULATION规则代码生成模板（V2.0 Phase 4.2.3）-->
<#--
  Input parameters Map:
  - patternType: ARITHMETIC | AGGREGATION | CONDITIONAL | FORMULA
  - targetField: 目标字段名（如 totalPrice）
  - entityName: 实体名（如 Order）
  - entityVarName: 实体变量名（如 order）
  - description: 规则描述（用于注释）

  ARITHMETIC模式参数:
  - leftOperand: 左操作数
  - operator: 运算符（+、-、*、/）
  - rightOperand: 右操作数
  - dataType: 数据类型（BigDecimal、Integer、Double）

  AGGREGATION模式参数:
  - sourceField: 源集合字段（如 orderItems）
  - aggregationType: 聚合类型（sum、avg、max、min、count）
  - valueExpression: 值表达式（如 item.getPrice()）

  CONDITIONAL模式参数:
  - condition: 条件表达式
  - trueValue: 条件为真时的值
  - falseValue: 条件为假时的值

  FORMULA模式参数:
  - formula: 复杂公式表达式
-->

<#if patternType == "ARITHMETIC">
<#-- ========== 算术计算模式 ========== -->
<#-- 简单的四则运算：totalPrice = unitPrice * quantity -->

// CALCULATION规则：${description}
<#if dataType == "BigDecimal">
BigDecimal ${targetField}Value = ${leftOperand}.multiply(${rightOperand});
${entityVarName}.set${targetField?cap_first}(${targetField}Value);
<#elseif dataType == "Integer">
Integer ${targetField}Value = ${leftOperand} ${operator} ${rightOperand};
${entityVarName}.set${targetField?cap_first}(${targetField}Value);
<#elseif dataType == "Double">
Double ${targetField}Value = ${leftOperand} ${operator} ${rightOperand};
${entityVarName}.set${targetField?cap_first}(${targetField}Value);
<#else>
// TODO: 未知数据类型 - ${dataType}
${entityVarName}.set${targetField?cap_first}(${leftOperand} ${operator} ${rightOperand});
</#if>

<#elseif patternType == "AGGREGATION">
<#-- ========== 聚合函数模式 ========== -->
<#-- 集合的求和、平均、最大、最小等操作 -->

// CALCULATION规则：${description}
<#if aggregationType == "sum">
<#-- 求和：BigDecimal totalPrice = orderItems.stream().map(...).reduce(BigDecimal.ZERO, BigDecimal::add); -->
BigDecimal ${targetField}Value = ${entityVarName}.get${sourceField?cap_first}().stream()
    .map(item -> ${valueExpression})
    .reduce(BigDecimal.ZERO, BigDecimal::add);
${entityVarName}.set${targetField?cap_first}(${targetField}Value);

<#elseif aggregationType == "avg">
<#-- 平均值：计算总和后除以数量 -->
List<${sourceItemType!"Object"}> items = ${entityVarName}.get${sourceField?cap_first}();
if (items != null && !items.isEmpty()) {
    BigDecimal sum = items.stream()
        .map(item -> ${valueExpression})
        .reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal ${targetField}Value = sum.divide(BigDecimal.valueOf(items.size()), 2, RoundingMode.HALF_UP);
    ${entityVarName}.set${targetField?cap_first}(${targetField}Value);
}

<#elseif aggregationType == "max">
<#-- 最大值 -->
BigDecimal ${targetField}Value = ${entityVarName}.get${sourceField?cap_first}().stream()
    .map(item -> ${valueExpression})
    .max(BigDecimal::compareTo)
    .orElse(BigDecimal.ZERO);
${entityVarName}.set${targetField?cap_first}(${targetField}Value);

<#elseif aggregationType == "min">
<#-- 最小值 -->
BigDecimal ${targetField}Value = ${entityVarName}.get${sourceField?cap_first}().stream()
    .map(item -> ${valueExpression})
    .min(BigDecimal::compareTo)
    .orElse(BigDecimal.ZERO);
${entityVarName}.set${targetField?cap_first}(${targetField}Value);

<#elseif aggregationType == "count">
<#-- 计数 -->
Long ${targetField}Value = (long) ${entityVarName}.get${sourceField?cap_first}().size();
${entityVarName}.set${targetField?cap_first}(${targetField}Value.intValue());

<#else>
// TODO: 未知聚合类型 - ${aggregationType}
</#if>

<#elseif patternType == "CONDITIONAL">
<#-- ========== 条件计算模式 ========== -->
<#-- If-then-else逻辑：discount = (totalPrice > 100) ? totalPrice * 0.9 : totalPrice -->

// CALCULATION规则：${description}
<#if dataType == "BigDecimal">
BigDecimal ${targetField}Value = (${condition}) ? ${trueValue} : ${falseValue};
${entityVarName}.set${targetField?cap_first}(${targetField}Value);
<#else>
${dataType!"Object"} ${targetField}Value = (${condition}) ? ${trueValue} : ${falseValue};
${entityVarName}.set${targetField?cap_first}(${targetField}Value);
</#if>

<#elseif patternType == "FORMULA">
<#-- ========== 公式表达式模式 ========== -->
<#-- 复杂数学公式：finalPrice = (basePrice + tax) * (1 - discount) + shippingFee -->

// CALCULATION规则：${description}
<#if dataType == "BigDecimal">
// 公式：${formula}
BigDecimal ${targetField}Value = ${formulaExpression!"BigDecimal.ZERO"};
${entityVarName}.set${targetField?cap_first}(${targetField}Value);
<#else>
// 公式：${formula}
${dataType!"Object"} ${targetField}Value = ${formulaExpression!"0"};
${entityVarName}.set${targetField?cap_first}(${targetField}Value);
</#if>

<#else>
<#-- ========== 未知模式 ========== -->
// TODO: CALCULATION规则生成失败 - 未知模式类型: ${patternType}
// 描述: ${description}
</#if>
