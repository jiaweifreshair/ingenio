package com.ingenio.backend.service.generated.impl;

import com.ingenio.backend.common.exception.BusinessException;
import com.ingenio.backend.common.exception.ErrorCode;
import com.ingenio.backend.entity.generated.ProductShotEntity;
import com.ingenio.backend.mapper.generated.ProductShotMapper;
import com.ingenio.backend.service.generated.ProductShotService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductShotServiceImpl implements ProductShotService {
    private final ProductShotMapper mapper;

    @Override
    public ProductShotEntity create(ProductShotEntity entity) {
        mapper.insert(entity);
        return entity;
    }

    @Override
    public ProductShotEntity getById(UUID id) {
        return mapper.selectById(id);
    }

    @Override
    public ProductShotEntity getByIdAndUser(UUID id, UUID userId) {
        ProductShotEntity entity = mapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "记录不存在");
        }
        if (userId != null && !userId.equals(entity.getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权访问此数据");
        }
        return entity;
    }

    @Override
    public ProductShotEntity updateStatus(UUID id, String status, String resultUrl) {
        ProductShotEntity entity = mapper.selectById(id);
        if (entity != null) {
            entity.setStatus(status);
            if (resultUrl != null) entity.setResultImageUrl(resultUrl);
            mapper.updateById(entity);
        }
        return entity;
    }

    @Async
    @Override
    public void processImage(UUID id) {
        log.info("Processing image for ProductShot: {}", id);
        updateStatus(id, "PROCESSING", null);
        try {
            // Mock Replicate Processing
            Thread.sleep(2000); 
            String mockResult = "https://placehold.co/600x400/png?text=AI+Result";
            updateStatus(id, "COMPLETED", mockResult);
            log.info("ProductShot completed: {}", id);
        } catch (Exception e) {
            log.error("ProductShot failed: {}", id, e);
            updateStatus(id, "FAILED", null);
        }
    }
}
