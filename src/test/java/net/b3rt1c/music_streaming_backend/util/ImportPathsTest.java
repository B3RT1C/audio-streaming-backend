package net.b3rt1c.music_streaming_backend.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.b3rt1c.music_streaming_backend.error.ApiException;
import net.b3rt1c.music_streaming_backend.error.ErrorCodes;

class ImportPathsTest {

    @TempDir
    Path tempDir;

    @Test
    void blankBaseDisablesImport() {
        ApiException exception = assertThrows(ApiException.class, () -> ImportPaths.resolve("  ", "library"));
        assertEquals(ErrorCodes.IMPORT_NOT_CONFIGURED, exception.getCode());
    }

    @Test
    void resolvesChildrenAndRejectsPathsOutsideTheBase() throws Exception {
        Path base = tempDir.resolve("music");
        Path nested = base.resolve("rock");
        Path outside = tempDir.resolve("secret");
        Files.createDirectories(nested);
        Files.createDirectories(outside);

        assertEquals(nested.toRealPath(), ImportPaths.resolve(base.toString(), "rock"));
        assertEquals(base.toRealPath(), ImportPaths.resolve(base.toString(), " "));
        assertEquals(base.toRealPath(), ImportPaths.resolve(base.toString(), null));

        ApiException escaped = assertThrows(ApiException.class,
            () -> ImportPaths.resolve(base.toString(), "../secret"));
        assertEquals(ErrorCodes.PATH_NOT_ALLOWED, escaped.getCode());

        ApiException absolute = assertThrows(ApiException.class,
            () -> ImportPaths.resolve(base.toString(), outside.toString()));
        assertEquals(ErrorCodes.PATH_NOT_ALLOWED, absolute.getCode());

        Path file = base.resolve("song.mp3");
        Files.write(file, new byte[] {1});
        ApiException notDirectory = assertThrows(ApiException.class,
            () -> ImportPaths.resolve(base.toString(), "song.mp3"));
        assertEquals(ErrorCodes.INVALID_FILE, notDirectory.getCode());
    }

    @Test
    void rejectsSymlinkThatLeavesTheBase() throws Exception {
        Path base = tempDir.resolve("music");
        Path outside = tempDir.resolve("secret");
        Files.createDirectories(base);
        Files.createDirectories(outside);
        Path link = base.resolve("link");
        try {
            Files.createSymbolicLink(link, outside);
        } catch (UnsupportedOperationException | java.io.IOException exception) {
            assumeTrue(false, "Symlinks are not available");
        }
        assumeTrue(Files.isSymbolicLink(link));

        ApiException exception = assertThrows(ApiException.class,
            () -> ImportPaths.resolve(base.toString(), "link"));
        assertEquals(ErrorCodes.PATH_NOT_ALLOWED, exception.getCode());
    }
}
