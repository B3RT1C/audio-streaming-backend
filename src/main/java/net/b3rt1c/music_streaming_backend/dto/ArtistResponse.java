package net.b3rt1c.music_streaming_backend.dto;

import java.util.List;

import net.b3rt1c.music_streaming_backend.model.Artist;
import net.b3rt1c.music_streaming_backend.util.TagNames;

public record ArtistResponse(
    Integer id,
    String name,
    int audioCount,
    List<String> tags
) {
    public static ArtistResponse from(Artist artist, int audioCount) {
        return new ArtistResponse(
            artist.getId(),
            artist.getName(),
            audioCount,
            TagNames.sorted(artist.getTags())
        );
    }
}
