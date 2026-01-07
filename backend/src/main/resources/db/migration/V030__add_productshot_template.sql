-- V025_1: Add ProductShot AI Template
-- Purpose: Provide a specialized template for the ProductShot AI Pilot
-- This enables G3 to "know" about ProductShot requirements (Schema + Features)

INSERT INTO industry_templates (
    id, name, description, category, subcategory,
    keywords, reference_url, complexity_score,
    is_active, usage_count, rating,
    created_at, updated_at,
    blueprint_spec
) VALUES (
    'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', -- Fixed UUID for easier testing
    'ProductShot AI (SaaS)',
    'AI-powered product photography generator. Upload product images, remove background automatically, and generate professional scenes using Flux.1-Pro.',
    'SaaS',
    'AI Tools',
    '["AI", "Image Generation", "Product Photography", "Replicate", "SaaS"]',
    'https://replicate.com',
    8,
    true,
    0,
    5.0,
    NOW(),
    NOW(),
    '{
      "schema": [
        {
          "tableName": "product_shots",
          "columns": [
            {"name": "id", "type": "UUID", "constraints": "PRIMARY KEY DEFAULT gen_random_uuid()"},
            {"name": "user_id", "type": "UUID", "constraints": "NOT NULL"},
            {"name": "original_image_url", "type": "VARCHAR(1024)", "constraints": "NOT NULL", "comment": "Uploaded raw product image"},
            {"name": "mask_image_url", "type": "VARCHAR(1024)", "comment": "Background removed mask"},
            {"name": "result_image_url", "type": "VARCHAR(1024)", "comment": "Final AI generated image"},
            {"name": "prompt", "type": "TEXT", "comment": "User prompt for scene generation"},
            {"name": "status", "type": "VARCHAR(32)", "comment": "PENDING, PROCESSING, COMPLETED, FAILED"},
            {"name": "created_at", "type": "TIMESTAMP", "constraints": "DEFAULT CURRENT_TIMESTAMP"},
            {"name": "updated_at", "type": "TIMESTAMP", "constraints": "DEFAULT CURRENT_TIMESTAMP"}
          ]
        }
      ],
      "features": [
        "Product Image Upload to MinIO",
        "Background Removal (Integration with Replicate/rembg)",
        "Scene Generation (Integration with Replicate/Flux.1-Pro)",
        "Gallery View of Generated Shots"
      ],
      "constraints": {
        "techStack": "Spring Boot + React",
        "apiStyle": "RESTful",
        "thirdParty": "Replicate API"
      }
    }'::jsonb
);
