package net.b3rt1c.music_streaming_backend.service;

import java.nio.file.Path;

public record StoredFile(Path path, String hash) {
}
