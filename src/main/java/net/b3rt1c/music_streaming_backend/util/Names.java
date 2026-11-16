package net.b3rt1c.music_streaming_backend.util;

import net.b3rt1c.music_streaming_backend.error.ApiException;
import net.b3rt1c.music_streaming_backend.error.ErrorCodes;

public final class Names {
    private Names() {
    }

    public static String required(String value, int max, String message) {
        String cleaned = clean(value, max, false);
        if (cleaned == null) {
            throw ApiException.badRequest(ErrorCodes.INVALID_NAME, message);
        }
        return cleaned;
    }

    public static String optional(String value, int max) {
        return clean(value, max, false);
    }

    public static String clip(String value, int max) {
        return clean(value, max, true);
    }

    private static String clean(String value, int max, boolean truncate) {
        if (value == null) {
            return null;
        }
        if (value.indexOf('\0') >= 0) {
            throw ApiException.badRequest(ErrorCodes.INVALID_NAME, "A valid name is required");
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() > max) {
            if (truncate) {
                return trimmed.substring(0, max);
            }
            throw ApiException.badRequest(ErrorCodes.INVALID_NAME, "Name is too long");
        }
        return trimmed;
    }
}
