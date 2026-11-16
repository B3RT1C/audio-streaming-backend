package net.b3rt1c.music_streaming_backend.dto;

import java.util.List;

public record ImportResult(int imported, int skipped, int failed, int ignored, List<String> errors) {
    public ImportResult {
        errors = List.copyOf(errors);
    }
}
