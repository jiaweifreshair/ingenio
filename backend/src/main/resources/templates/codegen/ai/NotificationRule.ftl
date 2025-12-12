<#-- NOTIFICATION规则代码生成模板（V2.0 Phase 4.2.4）-->
<#--
  Input parameters Map:
  - patternType: EMAIL | SMS | SYSTEM_MESSAGE
  - notificationType: 通知类型（EMAIL/SMS/SYSTEM_MESSAGE）
  - recipientField: 接收者字段名（如 userEmail、merchantPhone）
  - titleTemplate: 通知标题模板
  - contentTemplate: 通知内容模板
  - entityName: 实体名（如 Order）
  - entityVarName: 实体变量名（如 order）
  - description: 规则描述（用于注释）
  - triggerCondition: 触发条件（可选）

  EMAIL模式参数:
  - recipientField: 邮箱字段名
  - titleTemplate: 邮件标题
  - contentTemplate: 邮件内容

  SMS模式参数:
  - recipientField: 手机号字段名
  - contentTemplate: 短信内容

  SYSTEM_MESSAGE模式参数:
  - recipientField: 用户ID字段名
  - titleTemplate: 消息标题
  - contentTemplate: 消息内容
-->

<#if patternType == "EMAIL">
<#-- ========== 邮件通知模式 ========== -->

// NOTIFICATION规则：${description}
<#if triggerCondition??>
if (${triggerCondition}) {
    // 发送邮件通知
    notificationService.sendEmail(
        ${entityVarName}.get${recipientField?cap_first}(),
        "${titleTemplate}",
        "${contentTemplate}"
    );
}
<#else>
// 发送邮件通知
notificationService.sendEmail(
    ${entityVarName}.get${recipientField?cap_first}(),
    "${titleTemplate}",
    "${contentTemplate}"
);
</#if>

<#elseif patternType == "SMS">
<#-- ========== 短信通知模式 ========== -->

// NOTIFICATION规则：${description}
<#if triggerCondition??>
if (${triggerCondition}) {
    // 发送短信通知
    notificationService.sendSms(
        ${entityVarName}.get${recipientField?cap_first}(),
        "${contentTemplate}"
    );
}
<#else>
// 发送短信通知
notificationService.sendSms(
    ${entityVarName}.get${recipientField?cap_first}(),
    "${contentTemplate}"
);
</#if>

<#elseif patternType == "SYSTEM_MESSAGE">
<#-- ========== 站内消息通知模式 ========== -->

// NOTIFICATION规则：${description}
<#if triggerCondition??>
if (${triggerCondition}) {
    // 发送站内消息通知
    notificationService.sendSystemMessage(
        ${entityVarName}.get${recipientField?cap_first}(),
        "${titleTemplate}",
        "${contentTemplate}"
    );
}
<#else>
// 发送站内消息通知
notificationService.sendSystemMessage(
    ${entityVarName}.get${recipientField?cap_first}(),
    "${titleTemplate}",
    "${contentTemplate}"
);
</#if>

<#else>
<#-- ========== 未知模式 ========== -->
// TODO: NOTIFICATION规则生成失败 - 未知模式类型: ${patternType}
// 描述: ${description}
</#if>
