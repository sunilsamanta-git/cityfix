package com.cityfix.service;

import com.cityfix.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Service
public class FileStorageService {
    private final Path root;

    public FileStorageService(@Value("${app.upload-dir}") String dir) throws IOException {
        this.root = Paths.get(dir).toAbsolutePath().normalize();
        Files.createDirectories(root);
    }

    public Path getRoot() { return root; }

    /** Saves an image under a random name (never trust the client's file name) and returns its public URL. */
    public String store(MultipartFile file) {
        String type = file.getContentType() == null ? "" : file.getContentType();
        String ext = switch (type) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "Only JPG, PNG or WebP photos are allowed");
        };
        String name = UUID.randomUUID() + ext;
        try {
            file.transferTo(root.resolve(name));
        } catch (IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not save the photo");
        }
        return "/uploads/" + name;
    }
}
