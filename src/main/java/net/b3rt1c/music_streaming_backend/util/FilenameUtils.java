package net.b3rt1c.music_streaming_backend.util;

public final class FilenameUtils {

    private FilenameUtils() {
    }

    public static ParsedFilename parse(String filename) {
        if (filename == null || filename.isBlank()) {
            throw new IllegalArgumentException("Filename is required");
        }

        int lastDot = filename.lastIndexOf('.');
        if (lastDot <= 0 || lastDot == filename.length() - 1) {
            throw new IllegalArgumentException("Filename must include a valid extension");
        }

        return new ParsedFilename(
            filename.substring(0, lastDot),
            filename.substring(lastDot + 1)
        );
    }

    public record ParsedFilename(String name, String extension) {
    }
}
