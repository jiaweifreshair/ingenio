package com.ingenio.backend.service.generated;

import com.ingenio.backend.entity.generated.ProductShotEntity;
import java.util.UUID;

public interface ProductShotService {
    ProductShotEntity create(ProductShotEntity entity);
    ProductShotEntity getById(UUID id);
    ProductShotEntity getByIdAndUser(UUID id, UUID userId);
    ProductShotEntity updateStatus(UUID id, String status, String resultUrl);
    void processImage(UUID id);
}
