package net.b3rt1c.music_streaming_backend.dto;

import java.time.LocalDate;

public record StatsSummaryResponse(
    long plays,
    long listenedSeconds,
    long distinctAudios,
    long distinctArtists,
    LocalDate from,
    LocalDate to
) {
}
