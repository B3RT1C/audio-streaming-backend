package net.b3rt1c.music_streaming_backend.util;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import net.b3rt1c.music_streaming_backend.error.ApiException;
import net.b3rt1c.music_streaming_backend.error.ErrorCodes;

class AudioFormatsTest {

    @Test
    void whitelistsAudioExtensionsAndContentTypes() {
        assertEquals("audio/mpeg", AudioFormats.contentType("MP3"));
        assertEquals("audio/mp4", AudioFormats.contentType("m4a"));
        assertEquals("audio/opus", AudioFormats.contentType("opus"));
        assertEquals("audio/webm", AudioFormats.contentType("webm"));
        assertTrue(AudioFormats.isAllowedExtension("flac"));
        assertFalse(AudioFormats.isAllowedExtension("html"));
        assertFalse(AudioFormats.isAllowedExtension("mp4"));

        assertDoesNotThrow(() -> AudioFormats.requireAllowed("wav", null));
        assertDoesNotThrow(() -> AudioFormats.requireAllowed("ogg", "application/octet-stream"));
        assertDoesNotThrow(() -> AudioFormats.requireAllowed("webm", "video/webm"));

        ApiException rejected = assertThrows(ApiException.class,
            () -> AudioFormats.requireAllowed("html", "text/html"));
        assertEquals(ErrorCodes.INVALID_FILE, rejected.getCode());

        ApiException mismatched = assertThrows(ApiException.class,
            () -> AudioFormats.requireAllowed("mp3", "text/html"));
        assertEquals(ErrorCodes.INVALID_FILE, mismatched.getCode());
    }
}
