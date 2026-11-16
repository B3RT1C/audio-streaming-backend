package net.b3rt1c.music_streaming_backend.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class FilenameUtilsTest {

    @Test
    void usesOnlyTheFileNameSegment() {
        assertEquals("song", FilenameUtils.parse("folder/song.mp3").name());
        assertEquals("mp3", FilenameUtils.parse("folder/song.mp3").extension());
        assertEquals("song", FilenameUtils.parse("C:\\music\\song.MP3").name());
        assertEquals("MP3", FilenameUtils.parse("C:\\music\\song.MP3").extension());
        assertThrows(IllegalArgumentException.class, () -> FilenameUtils.parse(".mp3"));
        assertThrows(IllegalArgumentException.class, () -> FilenameUtils.parse("noext"));
    }
}
