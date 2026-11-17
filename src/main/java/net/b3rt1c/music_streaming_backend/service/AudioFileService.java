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

import net.b3rt1c.music_streaming_backend.config.StorageProperties;

@Service
public class AudioFileService {
    private static final String DEFAULT_DIRECTORY = "uploads/songs/";

    private final Path storageDir;
    private final String databaseDirectory;

    public AudioFileService(StorageProperties properties) {
        String configured = properties.getDir();
        if (configured == null || configured.isBlank()) {
            this.storageDir = Path.of("uploads", "songs");
            this.databaseDirectory = DEFAULT_DIRECTORY;
        } else {
            this.storageDir = Path.of(configured.trim()).toAbsolutePath().normalize();
            this.databaseDirectory = withTrailingSeparator(this.storageDir.toString());
        }
    }

    public Path storageDirectory() {
        return storageDir;
    }

    public String databaseDirectory() {
        return databaseDirectory;
    }

    public Resource getAudioFile(String directory, String storageKey, String extension) {
        return new FileSystemResource(resolveStoredFile(directory, storageKey, extension));
    }

    public StoredFile store(InputStream input, String storageKey, String extension) {
        Path destination = null;
        MessageDigest digest = sha256Digest();
        try (InputStream source = input;
             DigestInputStream digestInputStream = new DigestInputStream(source, digest)) {
            rejectUnsafeKey(storageKey);
            destination = storageDir.resolve(storageKey + "." + extension).normalize();
            if (!destination.startsWith(storageDir.normalize())) {
                throw new IllegalStateException("Refusing to store outside the audio directory");
            }
            Files.createDirectories(storageDir);
            Files.copy(digestInputStream, destination, StandardCopyOption.REPLACE_EXISTING);
            return new StoredFile(destination, toHex(digest.digest()));
        } catch (IOException exception) {
            deleteIfExists(destination);
            throw new IllegalStateException("Failed to store audio file", exception);
        } catch (RuntimeException exception) {
            deleteIfExists(destination);
            throw exception;
        }
    }

    public void deleteAudioFile(String directory, String storageKey, String extension) {
        Path filePath = resolveStoredFile(directory, storageKey, extension);
        try {
            Files.deleteIfExists(filePath);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to delete audio file", exception);
        }
    }

    public void deleteStored(Path path) {
        try {
            if (path != null) {
                Files.deleteIfExists(path);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to delete audio file", exception);
        }
    }

    public Path resolveStoredFile(String directory, String storageKey, String extension) {
        rejectUnsafeKey(storageKey);
        Path folder = Path.of(directory == null ? "" : directory);
        Path file = folder.resolve(storageKey + "." + extension).normalize();
        if (!file.startsWith(folder.normalize())) {
            throw new IllegalStateException("Invalid stored audio path");
        }
        return file;
    }

    private static void rejectUnsafeKey(String storageKey) {
        if (storageKey == null
                || storageKey.isBlank()
                || storageKey.indexOf('/') >= 0
                || storageKey.indexOf('\\') >= 0
                || storageKey.contains("..")) {
            throw new IllegalStateException("Invalid storage key");
        }
    }

    private static void deleteIfExists(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // The original failure is the one to report.
        }
    }

    private static String withTrailingSeparator(String path) {
        if (path.endsWith("/") || path.endsWith("\\")) {
            return path;
        }
        return path + java.io.File.separator;
    }

    private static MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(Character.forDigit((value >> 4) & 0xF, 16));
            builder.append(Character.forDigit(value & 0xF, 16));
        }
        return builder.toString();
    }
}
