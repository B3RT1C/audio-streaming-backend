package net.b3rt1c.music_streaming_backend.util;

public final class FilenameUtils {

    private FilenameUtils() {
    }

    public static String baseName(String filename) {
        if (filename == null) {
            return "";
        }
        String trimmed = filename.trim();
        int slash = Math.max(trimmed.lastIndexOf('/'), trimmed.lastIndexOf('\\'));
        return slash >= 0 ? trimmed.substring(slash + 1) : trimmed;
    }

    public static ParsedFilename parse(String filename) {
        String base = baseName(filename);
        if (base.isBlank()) {
            throw new IllegalArgumentException("Filename is required");
        }

        int lastDot = base.lastIndexOf('.');
        if (lastDot <= 0 || lastDot == base.length() - 1) {
            throw new IllegalArgumentException("Filename must include a valid extension");
        }

        return new ParsedFilename(
            base.substring(0, lastDot),
            base.substring(lastDot + 1)
        );
    }

    public record ParsedFilename(String name, String extension) {
    }
}
