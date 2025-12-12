<#-- WORKFLOW规则代码生成模板（V2.0 Phase 4.2.4）-->
<#--
  Input parameters Map:
  - patternType: STATE_TRANSITION | CONDITIONAL_BRANCH
  - currentState: 当前状态（如 "待支付"）
  - targetState: 目标状态（如 "已支付"）
  - nextState: 自动流转状态（可选，如 "待发货"）
  - statusField: 状态字段名（默认 "status"）
  - entityName: 实体名（如 Order）
  - entityVarName: 实体变量名（如 order）
  - description: 规则描述（用于注释）

  STATE_TRANSITION模式参数:
  - currentState: 当前状态
  - targetState: 目标状态
  - nextState: 自动流转状态（可选）
  - historyEntity: 状态历史实体（可选）

  CONDITIONAL_BRANCH模式参数:
  - condition: 条件表达式
  - trueState: 条件为真时的状态
  - falseState: 条件为假时的状态
-->

<#if patternType == "STATE_TRANSITION">
<#-- ========== 状态流转模式 ========== -->
<#-- 从状态A流转到状态B，可选自动流转到状态C，记录状态历史 -->

// WORKFLOW规则：${description}
if ("${currentState}".equals(${entityVarName}.get${statusField?cap_first}())) {
    // 验证状态流转合法性
    if (!isValidTransition(${entityVarName}.get${statusField?cap_first}(), "${targetState}")) {
        throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION,
            "状态不能从'${currentState}'流转到'${targetState}'");
    }

    // 更新状态
    ${entityVarName}.set${statusField?cap_first}("${targetState}");
    ${entityVarName}.setStatusUpdateTime(LocalDateTime.now());

<#if historyEntity??>
    // 记录状态流转历史
    ${historyEntity?uncap_first}Repository.save(${historyEntity}.builder()
        .${entityVarName}Id(${entityVarName}.getId())
        .fromStatus("${currentState}")
        .toStatus("${targetState}")
        .updateTime(LocalDateTime.now())
        .build());
<#else>
    // 注意：建议添加状态历史记录实体
</#if>

<#if nextState??>
    // 自动流转到下一状态
    ${entityVarName}.set${statusField?cap_first}("${nextState}");
    ${entityVarName}.setStatusUpdateTime(LocalDateTime.now());
</#if>
}

<#elseif patternType == "CONDITIONAL_BRANCH">
<#-- ========== 条件分支流转模式 ========== -->
<#-- 根据条件判断流转到不同状态 -->

// WORKFLOW规则：${description}
if (${condition!\"true\"}) {
    // 条件为真，流转到状态：${trueState}
    if (!isValidTransition(${entityVarName}.get${statusField?cap_first}(), "${trueState}")) {
        throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION,
            "状态不能流转到'${trueState}'");
    }
    ${entityVarName}.set${statusField?cap_first}("${trueState}");
    ${entityVarName}.setStatusUpdateTime(LocalDateTime.now());
} else {
    // 条件为假，流转到状态：${falseState}
    if (!isValidTransition(${entityVarName}.get${statusField?cap_first}(), "${falseState}")) {
        throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION,
            "状态不能流转到'${falseState}'");
    }
    ${entityVarName}.set${statusField?cap_first}("${falseState}");
    ${entityVarName}.setStatusUpdateTime(LocalDateTime.now());
}

<#else>
<#-- ========== 未知模式 ========== -->
// TODO: WORKFLOW规则生成失败 - 未知模式类型: ${patternType}
// 描述: ${description}
</#if>
