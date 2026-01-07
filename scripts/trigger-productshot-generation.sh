#!/bin/bash
# scripts/trigger-productshot-generation.sh

TEMPLATE_ID="a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11"
REQUIREMENT="Create a Product Shot SaaS application where users can upload product images, automatically remove the background, and generate a new scene using AI."

echo "🚀 Triggering G3 Job for ProductShot AI..."
echo "Template ID: $TEMPLATE_ID"

# 等待服务就绪
sleep 2

RESPONSE=$(curl -s -X POST http://localhost:8080/api/v1/g3/jobs \
  -H "Content-Type: application/json" \
  -d "{
    \"requirement\": \"$REQUIREMENT\",
    \"templateId\": \"$TEMPLATE_ID\",
    \"maxRounds\": 1
  }")

echo "Response: $RESPONSE"

JOB_ID=$(echo $RESPONSE | grep -o '"jobId":"[^"]*"' | cut -d'"' -f4)

if [ -n "$JOB_ID" ] && [ "$JOB_ID" != "null" ]; then
  echo "✅ Job Created: $JOB_ID"
  echo "Waiting for logs..."
  # 简单轮询几次状态
  for i in {1..5}; do
    sleep 5
    curl -s http://localhost:8080/api/v1/g3/jobs/$JOB_ID | grep "status"
  done
else
  echo "❌ Failed to create job"
fi
