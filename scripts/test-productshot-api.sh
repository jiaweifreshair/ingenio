#!/bin/bash
# scripts/test-productshot-api.sh

echo "🚀 Testing ProductShot API..."

# Create
RESPONSE=$(curl -s -X POST http://localhost:8080/api/v1/product-shots \
  -H "Content-Type: application/json" \
  -d '{
    "originalImageUrl": "https://example.com/product.jpg",
    "prompt": "studio lighting, professional shot"
  }')

echo "Create Response: $RESPONSE"

ID=$(echo $RESPONSE | jq -r '.data.id')

if [ -n "$ID" ] && [ "$ID" != "null" ]; then
  echo "✅ ProductShot Created: $ID"
  
  echo "Waiting for processing..."
  sleep 3
  
  # Get
  GET_RESPONSE=$(curl -s http://localhost:8080/api/v1/product-shots/$ID)
  echo "Get Response: $GET_RESPONSE"
  
  STATUS=$(echo $GET_RESPONSE | jq -r '.data.status')
  RESULT_URL=$(echo $GET_RESPONSE | jq -r '.data.resultImageUrl')
  
  if [ "$STATUS" == "COMPLETED" ]; then
    echo "✅ Processing Completed. Result URL: $RESULT_URL"
  else
    echo "⚠️ Processing Status: $STATUS (Expected COMPLETED)"
  fi
else
  echo "❌ Failed to create ProductShot"
fi
