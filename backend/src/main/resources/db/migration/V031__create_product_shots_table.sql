-- V031: Create Product Shots Table
-- Purpose: Manually create the table for ProductShot AI Pilot (since G3 generation was simulated)

CREATE TABLE IF NOT EXISTS product_shots (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    original_image_url VARCHAR(1024) NOT NULL,
    mask_image_url VARCHAR(1024),
    result_image_url VARCHAR(1024),
    prompt TEXT,
    status VARCHAR(32),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_product_shots_user_id ON product_shots(user_id);
CREATE INDEX idx_product_shots_status ON product_shots(status);

COMMENT ON TABLE product_shots IS 'ProductShot AI Pilot Data Table';
