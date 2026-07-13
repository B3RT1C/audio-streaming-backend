package net.b3rt1c.music_streaming_backend.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AudioFileService {
    private final String rootPath = "uploads/songs/";

    public Resource getAudioFile(String path, String name, String extension) {
        return new FileSystemResource(path + name + "." + extension);
    }

    public long addAudioFile(MultipartFile audioFile) {
        Path destination = Path.of(rootPath + audioFile.getOriginalFilename());

        try (InputStream inputStream = audioFile.getInputStream()) {
            Files.createDirectories(destination.getParent());
            return Files.copy(inputStream, destination, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to store audio file", exception);
        }
    }

    public void deleteAudioFile(String path, String name, String extension) {
        Path filePath = Path.of(path + name + "." + extension);

        try {
            Files.deleteIfExists(filePath);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to delete audio file", exception);
        }
    }
}
