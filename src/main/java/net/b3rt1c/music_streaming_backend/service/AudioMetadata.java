package net.b3rt1c.music_streaming_backend.service;

public record AudioMetadata(
    String title,
    String artist,
    String album,
    Integer durationSeconds,
    Integer trackNumber,
    String genre,
    Integer year
) {
    public static AudioMetadata fallback(String title) {
        return new AudioMetadata(title, null, null, null, null, null, null);
    }
}
