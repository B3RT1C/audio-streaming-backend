package net.b3rt1c.music_streaming_backend.dto;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PlaylistResponse(
    Integer id,
    String name,
    Instant createdAt,
    Instant updatedAt,
    int itemCount,
    List<PlaylistItemResponse> items
) {
}
