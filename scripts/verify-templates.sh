#!/bin/bash
# scripts/verify-templates.sh

echo "🔍 Verifying Industry Templates..."

RESPONSE=$(curl -s http://localhost:8080/api/v1/templates?limit=100)

# Check for ProductShot
PRODUCT_SHOT=$(echo $RESPONSE | jq -r '.data[] | select(.name | contains("ProductShot")) | .id')

if [ -n "$PRODUCT_SHOT" ]; then
  echo "✅ ProductShot Template Found: $PRODUCT_SHOT"
else
  echo "❌ ProductShot Template NOT Found"
  echo "Available templates:"
  echo $RESPONSE | jq -r '.data[].name'
fi
