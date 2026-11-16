package net.b3rt1c.music_streaming_backend.dto;

public record PlaylistItemResponse(int position, int audioId, AudioResponse audio) {
}
