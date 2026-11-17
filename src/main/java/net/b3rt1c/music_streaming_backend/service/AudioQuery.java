package net.b3rt1c.music_streaming_backend.service;

import java.util.List;

public record AudioQuery(
    String text,
    String title,
    String artist,
    List<String> tags,
    List<String> artistTags,
    List<String> playlistTags,
    boolean shuffle,
    Long seed
) {
}
