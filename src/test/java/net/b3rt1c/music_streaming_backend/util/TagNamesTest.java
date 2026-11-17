package net.b3rt1c.music_streaming_backend.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import net.b3rt1c.music_streaming_backend.model.Tag;

class TagNamesTest {

    @Test
    void keyIgnoresCaseAndKeepsAccents() {
        assertEquals("josé", TagNames.key("  José "));
        assertEquals("live", TagNames.key("LIVE"));
        assertNotEquals(TagNames.key("jose"), TagNames.key("José"));
        assertEquals("", TagNames.key("   "));
        assertEquals("", TagNames.key(null));
    }

    @Test
    void sortedNamesAreCaseInsensitive() {
        Tag live = tag("Live");
        Tag album = tag("Álbum");
        assertEquals(List.of("Live", "Álbum"), TagNames.sorted(List.of(album, live)));
        assertEquals(List.of(), TagNames.sorted(null));
        assertEquals(List.of(), TagNames.sorted(List.of()));
    }

    private static Tag tag(String name) {
        Tag tag = new Tag();
        tag.setName(name);
        return tag;
    }
}
