package com.vietspecialty.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

@Slf4j
public final class MagicByteVerifier {

    private MagicByteVerifier() {}

    // JPEG: FF D8 FF
    private static final byte[] JPEG_MAGIC = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};

    // PNG: 89 50 4E 47 0D 0A 1A 0A
    private static final byte[] PNG_MAGIC = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

    // GIF: 47 49 46 38
    private static final byte[] GIF_MAGIC = new byte[]{0x47, 0x49, 0x46, 0x38};

    // WEBP: RIFF....WEBP (52 49 46 46 ... 57 45 42 50)
    private static final byte[] RIFF_MAGIC = new byte[]{0x52, 0x49, 0x46, 0x46};
    private static final byte[] WEBP_MAGIC = new byte[]{0x57, 0x45, 0x42, 0x50};

    public static boolean isAllowedImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return false;
        }

        // Kiểm tra dung lượng tối đa 10MB
        if (file.getSize() > 10 * 1024 * 1024) {
            log.warn("File size exceeds 10MB limit: {} bytes", file.getSize());
            return false;
        }

        byte[] header = new byte[12];
        try (InputStream is = file.getInputStream()) {
            int bytesRead = is.read(header);
            if (bytesRead < 4) {
                return false;
            }

            // Kiểm tra JPEG
            if (bytesMatch(header, JPEG_MAGIC, 0)) {
                return true;
            }

            // Kiểm tra PNG
            if (bytesRead >= 8 && bytesMatch(header, PNG_MAGIC, 0)) {
                return true;
            }

            // Kiểm tra GIF
            if (bytesMatch(header, GIF_MAGIC, 0)) {
                return true;
            }

            // Kiểm tra WEBP
            if (bytesRead >= 12 && bytesMatch(header, RIFF_MAGIC, 0) && bytesMatch(header, WEBP_MAGIC, 8)) {
                return true;
            }

            log.warn("Uploaded file failed magic bytes verification: name={}, size={}", file.getOriginalFilename(), file.getSize());
            return false;
        } catch (IOException e) {
            log.error("Failed to read input stream for file magic byte check", e);
            return false;
        }
    }

    private static boolean bytesMatch(byte[] source, byte[] target, int offset) {
        if (source.length < offset + target.length) {
            return false;
        }
        for (int i = 0; i < target.length; i++) {
            if (source[offset + i] != target[i]) {
                return false;
            }
        }
        return true;
    }
}

