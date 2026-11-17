package net.b3rt1c.music_streaming_backend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record StatsRankResponse(
    int id,
    String name,
    String secondaryName,
    long plays,
    long listenedSeconds
) {
}
