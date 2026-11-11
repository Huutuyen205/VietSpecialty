package com.vietspecialty.service.impl;

import com.vietspecialty.config.AppProperties;
import com.vietspecialty.exception.BusinessException;
import com.vietspecialty.exception.ErrorCode;
import com.vietspecialty.service.StorageService;
import com.vietspecialty.util.MagicByteVerifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Slf4j
@Service
@Primary
@RequiredArgsConstructor
public class LocalStorageService implements StorageService {

    private final AppProperties appProperties;

    @Override
    public String uploadImage(MultipartFile file, String folder) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Tệp tải lên không được để trống");
        }

        // 1. Kiểm tra Magic Bytes chống tấn công upload webshell/mã độc
        if (!MagicByteVerifier.isAllowedImage(file)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Tệp không phải là hình ảnh hợp lệ (chỉ hỗ trợ JPG, PNG, WEBP)");
        }

        try {
            // 2. Xác định đuôi file an toàn
            String originalFilename = file.getOriginalFilename();
            String extension = ".jpg";
            if (originalFilename != null && originalFilename.lastIndexOf('.') != -1) {
                String rawExt = originalFilename.substring(originalFilename.lastIndexOf('.')).toLowerCase();
                if (rawExt.equals(".png") || rawExt.equals(".webp") || rawExt.equals(".jpeg") || rawExt.equals(".jpg")) {
                    extension = rawExt;
                }
            }

            // 3. Tạo thư mục lưu trữ nếu chưa có
            String subFolder = (folder != null && !folder.isBlank()) ? folder.trim() : "common";
            Path targetDir = Paths.get(appProperties.getStorage().getLocalDir(), subFolder).toAbsolutePath().normalize();
            if (!Files.exists(targetDir)) {
                Files.createDirectories(targetDir);
            }

            // 4. Lưu tệp với UUID duy nhất
            String fileName = UUID.randomUUID() + extension;
            Path targetFilePath = targetDir.resolve(fileName);
            Files.copy(file.getInputStream(), targetFilePath, StandardCopyOption.REPLACE_EXISTING);

            log.info("Saved image locally to: {}", targetFilePath);

            // 5. Trả về đường dẫn web
            return "/uploads/" + subFolder + "/" + fileName;

        } catch (IOException e) {
            log.error("Failed to upload image to local storage", e);
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "Không thể lưu tệp ảnh tải lên");
        }
    }

    @Override
    public void deleteImage(String imageUrl) {
        if (imageUrl == null || !imageUrl.startsWith("/uploads/")) {
            return;
        }

        try {
            String relativePath = imageUrl.substring("/uploads/".length());
            Path filePath = Paths.get(appProperties.getStorage().getLocalDir(), relativePath).toAbsolutePath().normalize();
            if (Files.exists(filePath)) {
                Files.delete(filePath);
                log.info("Deleted local image file: {}", filePath);
            }
        } catch (IOException e) {
            log.warn("Failed to delete local image file: {}", imageUrl, e);
        }
    }
}

