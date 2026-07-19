package net.b3rt1c.music_streaming_backend.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AudioFileService {
    private final String rootPath = "uploads/songs/";

    public Resource getAudioFile(String path, String storageKey, String extension) {
        return new FileSystemResource(path + storageKey + "." + extension);
    }

    public String addAudioFileAndComputeSha256(MultipartFile audioFile, String storageKey, String extension) {
        Path destination = Path.of(rootPath + storageKey + "." + extension);
        MessageDigest digest = sha256Digest();

        try (InputStream inputStream = audioFile.getInputStream();
            DigestInputStream digestInputStream = new DigestInputStream(inputStream, digest)) {
            Files.createDirectories(destination.getParent());
            Files.copy(digestInputStream, destination, StandardCopyOption.REPLACE_EXISTING);
            return toHex(digest.digest());
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to store audio file", exception);
        }
    }

    public void deleteAudioFile(String path, String storageKey, String extension) {
        Path filePath = Path.of(path + storageKey + "." + extension);

        try {
            Files.deleteIfExists(filePath);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to delete audio file", exception);
        }
    }

    private static MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }
}
