#!/bin/bash

# OpenLovable代码生成质量测试脚本
# 目的: 验证提示词优化和代码清理工具是否有效减少重复导入问题

echo "=== OpenLovable代码生成质量测试 ==="
echo ""
echo "测试目标: 验证重复导入问题是否得到解决"
echo "优化措施:"
echo "  1. 提示词优化 - 在structured-prompt-engine.ts添加Import导入规范"
echo "  2. 代码清理工具 - code-sanitizer.ts自动合并重复导入"
echo ""

# 测试用例: 生成一个简单的React计数器组件
TEST_REQUIREMENT="创建一个简单的React计数器组件,包含增加和减少按钮"

echo "测试用例: $TEST_REQUIREMENT"
echo ""
echo "正在调用OpenLovable API生成代码..."
echo ""

# 调用OpenLovable API
curl -X POST http://localhost:3001/api/generate \
  -H "Content-Type: application/json" \
  -d "{
    \"prompt\": \"$TEST_REQUIREMENT\",
    \"stream\": false
  }" \
  -o /tmp/openlovable-test-result.json

echo ""
echo "=== 生成结果 ==="
cat /tmp/openlovable-test-result.json | jq '.'

echo ""
echo "=== 检查重复导入 ==="
echo "正在分析生成的代码..."

# 提取生成的代码并检查重复导入
cat /tmp/openlovable-test-result.json | jq -r '.files[].content' | grep -E "^import.*from" | sort | uniq -d

if [ $? -eq 0 ]; then
  echo "❌ 发现重复导入!"
  echo "优化措施可能未生效,需要进一步调查"
else
  echo "✅ 未发现重复导入!"
  echo "优化措施生效,代码生成质量提升"
fi

echo ""
echo "=== 测试完成 ==="
