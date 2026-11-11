package com.vietspecialty.service.impl;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.vietspecialty.config.AppProperties;
import com.vietspecialty.exception.BusinessException;
import com.vietspecialty.exception.ErrorCode;
import com.vietspecialty.service.StorageService;
import com.vietspecialty.util.MagicByteVerifier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@Slf4j
@Service
public class CloudinaryStorageService implements StorageService {

    private final AppProperties appProperties;
    private final StorageService localStorageService;
    private Cloudinary cloudinary;

    public CloudinaryStorageService(AppProperties appProperties, @Qualifier("localStorageService") StorageService localStorageService) {
        this.appProperties = appProperties;
        this.localStorageService = localStorageService;
        String cloudinaryUrl = appProperties.getStorage().getCloudinaryUrl();
        if (cloudinaryUrl != null && !cloudinaryUrl.isBlank() && cloudinaryUrl.startsWith("cloudinary://")) {
            try {
                this.cloudinary = new Cloudinary(cloudinaryUrl);
                log.info("Cloudinary client initialized successfully");
            } catch (Exception e) {
                log.warn("Failed to initialize Cloudinary with URL, falling back to local storage: {}", e.getMessage());
            }
        }
    }

    @Override
    public String uploadImage(MultipartFile file, String folder) {
        // 1. Kiểm tra Magic Bytes chống webshell
        if (!MagicByteVerifier.isAllowedImage(file)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Tệp không phải là hình ảnh hợp lệ (chỉ hỗ trợ JPG, PNG, WEBP)");
        }

        // 2. Nếu Cloudinary chưa được khởi tạo hoặc provider là local -> Lưu local
        if (cloudinary == null || "local".equalsIgnoreCase(appProperties.getStorage().getProvider())) {
            return localStorageService.uploadImage(file, folder);
        }

        try {
            Map<?, ?> uploadResult = cloudinary.uploader().upload(file.getBytes(), ObjectUtils.asMap(
                    "folder", "vietspecialty/" + (folder != null ? folder : "common"),
                    "transformation", "f_auto,q_auto"
            ));

            String secureUrl = (String) uploadResult.get("secure_url");
            log.info("Uploaded image to Cloudinary: {}", secureUrl);
            return secureUrl;

        } catch (IOException e) {
            log.error("Cloudinary upload failed, falling back to local storage", e);
            return localStorageService.uploadImage(file, folder);
        }
    }

    @Override
    public void deleteImage(String imageUrl) {
        if (imageUrl == null) return;
        if (imageUrl.startsWith("/uploads/")) {
            localStorageService.deleteImage(imageUrl);
        } else if (cloudinary != null && imageUrl.contains("cloudinary.com")) {
            try {
                // Tách public_id từ URL Cloudinary
                log.info("Requested deletion of Cloudinary image: {}", imageUrl);
            } catch (Exception e) {
                log.warn("Failed to delete Cloudinary image: {}", imageUrl, e);
            }
        }
    }
}

