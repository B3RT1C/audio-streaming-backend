package net.b3rt1c.music_streaming_backend.dto;

public record TagResponse(
    Integer id,
    String name,
    int audioCount,
    int artistCount,
    int playlistCount,
    boolean readOnly,
    TagTypeRef type
) {
}
