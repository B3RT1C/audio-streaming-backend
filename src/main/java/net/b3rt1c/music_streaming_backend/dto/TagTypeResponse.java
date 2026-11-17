package net.b3rt1c.music_streaming_backend.dto;

public record TagTypeResponse(
    Integer id,
    String key,
    String name,
    boolean system,
    String color,
    int count
) {
}
