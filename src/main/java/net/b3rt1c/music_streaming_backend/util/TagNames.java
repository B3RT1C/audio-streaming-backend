package net.b3rt1c.music_streaming_backend.util;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import net.b3rt1c.music_streaming_backend.model.Tag;

public final class TagNames {
    public static final int MAX_LENGTH = 100;
    public static final int MAX_PER_SUBJECT = 100;

    private TagNames() {
    }

    /**
     * Comparison key for tag names. Case is ignored; accents are kept.
     */
    public static String key(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    public static List<String> sorted(Collection<Tag> tags) {
        if (tags == null || tags.isEmpty()) {
            return List.of();
        }
        return tags.stream()
            .map(Tag::getName)
            .sorted(Comparator.comparing(TagNames::key).thenComparing(name -> name))
            .toList();
    }
}
