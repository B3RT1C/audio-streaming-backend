package net.b3rt1c.music_streaming_backend.service;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AudioFileService {
    private final String rootPath = "uploads/songs/";

    public Resource getAudioFile(String path, String name, String extension) {
        return new FileSystemResource(path + name +"."+ extension);
    }

    public long addAudioFile(MultipartFile audioFile) {
        Path destination = Path.of(rootPath + audioFile.getOriginalFilename());
        long totalBytes = 0;

        try (InputStream iS = audioFile.getInputStream()) {
            totalBytes = Files.copy(iS, destination, StandardCopyOption.REPLACE_EXISTING);
            return totalBytes;
        } catch (Exception e) {
            // TODO: handle exception
            return totalBytes;
        }
    }
}
