package com.vietspecialty.service;

import org.springframework.web.multipart.MultipartFile;

public interface StorageService {

    String uploadImage(MultipartFile file, String folder);

    void deleteImage(String imageUrl);
}

