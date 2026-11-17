package net.b3rt1c.music_streaming_backend.dto;

public record RebuildResult(int processed, int updated, int unchanged, int failed) {
}
