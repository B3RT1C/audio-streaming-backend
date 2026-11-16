package net.b3rt1c.music_streaming_backend.util;

import java.text.Normalizer;
import java.util.Locale;

public final class TextFold {
    private TextFold() {
    }

    /**
     * Lowercase comparison key with accents removed, so "José" matches "jose".
     */
    public static String fold(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        String decomposed = Normalizer.normalize(value, Normalizer.Form.NFD);
        StringBuilder folded = new StringBuilder(decomposed.length());
        decomposed.codePoints()
            .filter(codePoint -> Character.getType(codePoint) != Character.NON_SPACING_MARK)
            .forEach(folded::appendCodePoint);
        return folded.toString().toLowerCase(Locale.ROOT);
    }
}
