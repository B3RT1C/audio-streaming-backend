package net.b3rt1c.music_streaming_backend.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import net.b3rt1c.music_streaming_backend.error.ApiException;
import net.b3rt1c.music_streaming_backend.error.ErrorCodes;
import net.b3rt1c.music_streaming_backend.model.Tag;

public final class SearchParams {
    private SearchParams() {
    }

    /**
     * Folded substring needle. Blank input is ignored.
     */
    public static String needle(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String folded = TextFold.fold(raw.trim());
        return folded.isBlank() ? "" : folded;
    }

    /**
     * Tag keys for an AND filter. Blank values are dropped. Case is ignored and accents stay.
     */
    public static Set<String> tagKeys(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return Set.of();
        }
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        for (String value : raw) {
            if (value == null || value.isBlank()) {
                continue;
            }
            String key = TagNames.key(value);
            if (!key.isBlank()) {
                keys.add(key);
            }
        }
        return keys;
    }

    public static boolean hasEveryTag(Collection<Tag> tags, Set<String> required) {
        if (required.isEmpty()) {
            return true;
        }
        if (tags == null || tags.isEmpty()) {
            return false;
        }
        Set<String> present = new LinkedHashSet<>();
        for (Tag tag : tags) {
            present.add(tag.getNameKey());
        }
        return present.containsAll(required);
    }

    public static boolean shuffle(String raw) {
        if (raw == null || raw.isBlank()) {
            return false;
        }
        String value = raw.trim();
        if ("true".equalsIgnoreCase(value)) {
            return true;
        }
        if ("false".equalsIgnoreCase(value)) {
            return false;
        }
        throw ApiException.badRequest(ErrorCodes.INVALID_QUERY, "shuffle must be true or false");
    }

    /**
     * Seed used only when shuffling. A blank seed is ignored. A non-integer seed is rejected.
     */
    public static Long seed(String raw, boolean shuffle) {
        if (!shuffle || raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException exception) {
            throw ApiException.badRequest(ErrorCodes.INVALID_QUERY, "seed must be an integer");
        }
    }

    /**
     * Fisher-Yates via {@link Collections#shuffle(List, Random)}. The same seed repeats the order.
     */
    public static <T> List<T> shuffled(List<T> items, Long seed) {
        List<T> copy = new ArrayList<>(items);
        Collections.shuffle(copy, seed == null ? new Random() : new Random(seed));
        return copy;
    }
}
