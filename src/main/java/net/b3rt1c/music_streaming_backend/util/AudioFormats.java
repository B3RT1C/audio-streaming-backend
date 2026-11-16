package net.b3rt1c.music_streaming_backend.util;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

import net.b3rt1c.music_streaming_backend.error.ApiException;
import net.b3rt1c.music_streaming_backend.error.ErrorCodes;

public final class AudioFormats {
    private static final Map<String, String> CONTENT_TYPES = Map.of(
        "mp3", "audio/mpeg",
        "m4a", "audio/mp4",
        "aac", "audio/aac",
        "flac", "audio/flac",
        "wav", "audio/wav",
        "ogg", "audio/ogg",
        "opus", "audio/opus",
        "webm", "audio/webm"
    );

    private static final Set<String> ALLOWED_MIME = Set.of(
        "audio/mpeg",
        "audio/mp3",
        "audio/mp4",
        "audio/x-m4a",
        "audio/m4a",
        "audio/aac",
        "audio/x-aac",
        "audio/flac",
        "audio/x-flac",
        "audio/wav",
        "audio/x-wav",
        "audio/wave",
        "audio/vnd.wave",
        "audio/ogg",
        "application/ogg",
        "audio/opus",
        "audio/webm",
        "video/webm"
    );

    private AudioFormats() {
    }

    public static boolean isAllowedExtension(String extension) {
        return CONTENT_TYPES.containsKey(normalizeExtension(extension));
    }

    public static String contentType(String extension) {
        return CONTENT_TYPES.getOrDefault(normalizeExtension(extension), "application/octet-stream");
    }

    public static void requireAllowed(String extension, String contentType) {
        String normalized = normalizeExtension(extension);
        if (!CONTENT_TYPES.containsKey(normalized)) {
            throw ApiException.badRequest(
                ErrorCodes.INVALID_FILE,
                "Unsupported audio type: " + (normalized.isEmpty() ? "(none)" : normalized)
            );
        }
        String mime = normalizeMime(contentType);
        if (mime.isEmpty() || "application/octet-stream".equals(mime) || ALLOWED_MIME.contains(mime)) {
            return;
        }
        throw ApiException.badRequest(ErrorCodes.INVALID_FILE, "Unsupported content type: " + mime);
    }

    public static String normalizeExtension(String extension) {
        return extension == null ? "" : extension.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeMime(String contentType) {
        if (contentType == null) {
            return "";
        }
        String mime = contentType.trim().toLowerCase(Locale.ROOT);
        int separator = mime.indexOf(';');
        if (separator >= 0) {
            mime = mime.substring(0, separator).trim();
        }
        return mime;
    }
}
