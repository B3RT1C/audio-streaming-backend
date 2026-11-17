package net.b3rt1c.music_streaming_backend.dto;

/**
 * One entry of a tag assignment. A null type means the free type.
 */
public record TagRequest(String type, String name) {
}
