package net.b3rt1c.music_streaming_backend.dto;

import java.time.Instant;

import net.b3rt1c.music_streaming_backend.model.AudioData;

public record AudioResponse(
    Integer id,
    String name,
    String title,
    String artist,
    String album,
    Integer durationSeconds,
    Integer trackNumber,
    String extension,
    String path,
    String contentHash,
    String originalFilename,
    Instant createdAt
) {
    public static AudioResponse from(AudioData audio) {
        return new AudioResponse(
            audio.getId(),
            audio.getName(),
            audio.getTitle(),
            audio.getArtist(),
            audio.getAlbum(),
            audio.getDurationSeconds(),
            audio.getTrackNumber(),
            audio.getExtension(),
            audio.getPath(),
            audio.getContentHash(),
            audio.getOriginalFilename(),
            audio.getCreatedAt()
        );
    }
}
