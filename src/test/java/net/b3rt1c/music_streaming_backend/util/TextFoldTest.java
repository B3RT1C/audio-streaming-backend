package net.b3rt1c.music_streaming_backend.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TextFoldTest {

    @Test
    void foldsCaseAndSpanishAccents() {
        assertEquals("", TextFold.fold(null));
        assertEquals("", TextFold.fold(""));
        assertEquals("jose", TextFold.fold("José"));
        assertEquals("album", TextFold.fold("ÁLBUM"));
        assertEquals("cancion", TextFold.fold("Canción"));
        assertEquals("nono", TextFold.fold("Ñoño"));
        assertEquals("heartbeats", TextFold.fold("Heartbeats"));
    }
}
