package net.b3rt1c.music_streaming_backend.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import lombok.RequiredArgsConstructor;
import net.b3rt1c.music_streaming_backend.error.ApiException;
import net.b3rt1c.music_streaming_backend.error.ErrorCodes;
import net.b3rt1c.music_streaming_backend.model.AudioData;
import net.b3rt1c.music_streaming_backend.util.AudioFormats;
import net.b3rt1c.music_streaming_backend.util.FilenameUtils;
import net.b3rt1c.music_streaming_backend.util.FilenameUtils.ParsedFilename;
import net.b3rt1c.music_streaming_backend.util.Names;

@Service
@RequiredArgsConstructor
public class AudioIngestService {
    private static final Logger log = LoggerFactory.getLogger(AudioIngestService.class);
    private static final int MAX_TEXT = 500;
    private static final int MAX_PATH = 4096;

    private final AudioFileService audioFileService;
    private final AudioMetadataReader audioMetadataReader;
    private final AudioDataService audioDataService;

    @Transactional
    public AudioData upload(MultipartFile file, String requestedName) {
        if (file == null || file.isEmpty() || file.getOriginalFilename() == null || file.getOriginalFilename().isBlank()) {
            throw ApiException.badRequest(ErrorCodes.FILE_REQUIRED, "File is required");
        }

        ParsedFilename parsed = FilenameUtils.parse(file.getOriginalFilename());
        String extension = parsed.extension().toLowerCase(Locale.ROOT);
        AudioFormats.requireAllowed(extension, file.getContentType());
        String displayName = requestedName == null || requestedName.isBlank()
            ? null
            : Names.required(requestedName, MAX_TEXT, "A valid name is required");

        String storageKey = UUID.randomUUID().toString();
        StoredFile stored = store(fileStream(file), storageKey, extension);
        try {
            if (audioDataService.existsByContentHash(stored.hash())) {
                log.info("Duplicate content detected for upload '{}': sha256={}", file.getOriginalFilename(), stored.hash());
            }
            AudioMetadata metadata = audioMetadataReader.read(stored.path(), parsed.name());
            AudioData audio = newAudio(
                displayName,
                parsed,
                extension,
                storageKey,
                stored.hash(),
                metadata,
                FilenameUtils.baseName(file.getOriginalFilename()),
                null
            );
            return audioDataService.save(audio);
        } catch (RuntimeException exception) {
            audioFileService.deleteStored(stored.path());
            throw exception;
        }
    }

    @Transactional
    public IngestStatus importLocalFile(Path source) {
        String originalPath;
        try {
            originalPath = source.toRealPath().normalize().toString();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read audio file", exception);
        }
        if (originalPath.length() > MAX_PATH) {
            throw ApiException.badRequest(ErrorCodes.INVALID_FILE, "Path is too long");
        }
        if (audioDataService.existsByOriginalPath(originalPath)) {
            return IngestStatus.SKIPPED;
        }

        ParsedFilename parsed = FilenameUtils.parse(source.getFileName().toString());
        String extension = parsed.extension().toLowerCase(Locale.ROOT);
        AudioFormats.requireAllowed(extension, null);

        String storageKey = UUID.randomUUID().toString();
        StoredFile stored;
        try {
            stored = store(Files.newInputStream(source), storageKey, extension);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read audio file", exception);
        }

        try {
            if (audioDataService.existsByContentHash(stored.hash())) {
                audioFileService.deleteStored(stored.path());
                return IngestStatus.SKIPPED;
            }
            AudioMetadata metadata = audioMetadataReader.read(source, parsed.name());
            AudioData audio = newAudio(
                null,
                parsed,
                extension,
                storageKey,
                stored.hash(),
                metadata,
                source.getFileName().toString(),
                originalPath
            );
            audioDataService.save(audio);
            return IngestStatus.IMPORTED;
        } catch (RuntimeException exception) {
            audioFileService.deleteStored(stored.path());
            throw exception;
        }
    }

    private StoredFile store(InputStream input, String storageKey, String extension) {
        return audioFileService.store(input, storageKey, extension);
    }

    private static InputStream fileStream(MultipartFile file) {
        try {
            return file.getInputStream();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read audio file", exception);
        }
    }

    private AudioData newAudio(
            String requestedName,
            ParsedFilename parsed,
            String extension,
            String storageKey,
            String hash,
            AudioMetadata metadata,
            String originalFilename,
            String originalPath) {
        String title = metadata.title() == null || metadata.title().isBlank()
            ? fallbackTitle(parsed.name())
            : metadata.title();
        String name = requestedName == null ? title : requestedName;
        AudioData audio = new AudioData(name, storageKey, extension, audioFileService.databaseDirectory());
        audio.setTitle(title);
        audio.setArtist(metadata.artist());
        audio.setAlbum(metadata.album());
        audio.setDurationSeconds(metadata.durationSeconds());
        audio.setTrackNumber(metadata.trackNumber());
        audio.setContentHash(hash);
        audio.setOriginalFilename(Names.clip(originalFilename, MAX_TEXT));
        audio.setOriginalPath(originalPath);
        audio.setCreatedAt(Instant.now());
        return audio;
    }

    private static String fallbackTitle(String filenameStem) {
        String clipped = Names.clip(filenameStem, MAX_TEXT);
        return clipped == null ? "audio" : clipped;
    }
}
