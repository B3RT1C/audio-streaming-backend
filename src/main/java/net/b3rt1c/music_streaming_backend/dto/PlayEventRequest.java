package net.b3rt1c.music_streaming_backend.dto;

import java.time.Instant;

public record PlayEventRequest(
    Integer audioId,
    Instant startedAt,
    Integer listenedSeconds,
    Boolean completed,
    String source,
    Integer playlistId,
    String clientEventId
) {
}
