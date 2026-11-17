package net.b3rt1c.music_streaming_backend.dto;

public record StatsTimelinePoint(
    String period,
    long plays,
    long listenedSeconds
) {
}
