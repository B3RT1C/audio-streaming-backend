package net.b3rt1c.music_streaming_backend.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;

import net.b3rt1c.music_streaming_backend.config.StorageProperties;

class AudioFileServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void blankDirectoryKeepsTheHistoricalRelativePath() {
        AudioFileService service = new AudioFileService(new StorageProperties());

        assertEquals("uploads/songs/", service.databaseDirectory());
        assertEquals(Path.of("uploads", "songs"), service.storageDirectory());
    }

    @Test
    void configuredDirectoryIsStoredAsAbsoluteAndServesExistingFiles() throws Exception {
        Path library = tempDir.resolve("library");
        StorageProperties properties = new StorageProperties();
        properties.setDir(library.toString());
        AudioFileService service = new AudioFileService(properties);

        assertTrue(service.storageDirectory().isAbsolute());
        assertEquals(library.toAbsolutePath().normalize(), service.storageDirectory());
        assertTrue(service.databaseDirectory().startsWith(service.storageDirectory().toString()));

        byte[] bytes = new byte[] {1, 2, 3, 4};
        StoredFile stored = service.store(new ByteArrayInputStream(bytes), "track-key", "mp3");
        assertTrue(stored.path().startsWith(service.storageDirectory()));
        assertEquals(64, stored.hash().length());

        String withSlash = service.storageDirectory().toString().replace('\\', '/');
        if (!withSlash.endsWith("/")) {
            withSlash = withSlash + "/";
        }
        String forward = withSlash;
        Resource resource = service.getAudioFile(forward, "track-key", "mp3");
        assertTrue(resource.exists());
        assertArrayEquals(bytes, resource.getContentAsByteArray());

        service.deleteAudioFile(service.databaseDirectory(), "track-key", "mp3");
        assertFalse(Files.exists(stored.path()));
        assertThrows(IllegalStateException.class, () -> service.getAudioFile(forward, "../evil", "mp3"));
    }
}
