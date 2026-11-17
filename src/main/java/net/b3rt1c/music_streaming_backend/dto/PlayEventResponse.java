package net.b3rt1c.music_streaming_backend.dto;

import java.time.Instant;

public record PlayEventResponse(
    Integer id,
    Integer audioId,
    Instant startedAt,
    int listenedSeconds,
    boolean completed,
    boolean counted,
    String source,
    Integer playlistId,
    String clientEventId,
    Instant createdAt
) {
}
