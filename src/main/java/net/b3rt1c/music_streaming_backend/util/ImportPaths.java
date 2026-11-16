package net.b3rt1c.music_streaming_backend.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import net.b3rt1c.music_streaming_backend.error.ApiException;
import net.b3rt1c.music_streaming_backend.error.ErrorCodes;

public final class ImportPaths {
    private ImportPaths() {
    }

    /**
     * Resolves {@code requested} inside {@code baseDir}. A blank request imports the base itself.
     * Absolute paths and symlinks must still stay inside the base.
     */
    public static Path resolve(String baseDir, String requested) {
        if (baseDir == null || baseDir.isBlank()) {
            throw ApiException.badRequest(
                ErrorCodes.IMPORT_NOT_CONFIGURED,
                "Folder import is disabled. Set AUDIO_IMPORT_DIR to the folder that may be imported."
            );
        }

        Path base = Path.of(baseDir.trim()).toAbsolutePath().normalize();
        Path target = requested == null || requested.isBlank()
            ? base
            : resolveRequested(base, requested.trim());

        if (!target.startsWith(base)) {
            throw outside();
        }
        if (!Files.isDirectory(target)) {
            throw ApiException.badRequest(ErrorCodes.INVALID_FILE, "Import path is not a directory");
        }

        try {
            Path realBase = base.toRealPath();
            Path realTarget = target.toRealPath();
            if (!realTarget.startsWith(realBase)) {
                throw outside();
            }
            return realTarget;
        } catch (IOException exception) {
            throw ApiException.badRequest(ErrorCodes.INVALID_FILE, "Import path is not a directory");
        }
    }

    private static Path resolveRequested(Path base, String requested) {
        Path raw = Path.of(requested);
        Path resolved = raw.isAbsolute() ? raw : base.resolve(raw);
        return resolved.toAbsolutePath().normalize();
    }

    private static ApiException outside() {
        return ApiException.badRequest(
            ErrorCodes.PATH_NOT_ALLOWED,
            "Import path must stay inside AUDIO_IMPORT_DIR"
        );
    }
}
