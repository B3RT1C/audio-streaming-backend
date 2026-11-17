package net.b3rt1c.music_streaming_backend.util;

import java.util.List;
import java.util.Map;

public final class TagTypeKeys {
    public static final String ARTIST = "artist";
    public static final String GENRE = "genre";
    public static final String ALBUM = "album";
    public static final String YEAR = "year";
    public static final String LANGUAGE = "language";
    public static final String FREE = "free";
    public static final String PLAYLIST = "playlist";

    public record Seed(String key, String name) {
    }

    public static final List<Seed> SYSTEM = List.of(
        new Seed(ARTIST, "Artista"),
        new Seed(GENRE, "Género"),
        new Seed(ALBUM, "Álbum"),
        new Seed(YEAR, "Año"),
        new Seed(LANGUAGE, "Idioma"),
        new Seed(FREE, "Libre")
    );

    private static final Map<String, String> ALIASES = Map.ofEntries(
        Map.entry("artist", ARTIST),
        Map.entry("artista", ARTIST),
        Map.entry("genre", GENRE),
        Map.entry("genero", GENRE),
        Map.entry("album", ALBUM),
        Map.entry("year", YEAR),
        Map.entry("ano", YEAR),
        Map.entry("language", LANGUAGE),
        Map.entry("idioma", LANGUAGE),
        Map.entry("playlist", PLAYLIST),
        Map.entry("free", FREE),
        Map.entry("libre", FREE)
    );

    private TagTypeKeys() {
    }

    public static Map<String, String> aliases() {
        return ALIASES;
    }

    public static String alias(String folded) {
        return ALIASES.get(folded);
    }

    public static boolean derived(String key) {
        return GENRE.equals(key) || ALBUM.equals(key) || YEAR.equals(key);
    }

    public static boolean assignable(String key) {
        return key != null && !derived(key) && !ARTIST.equals(key) && !PLAYLIST.equals(key);
    }

    public static boolean isReservedKey(String key) {
        return ALIASES.containsValue(key) || PLAYLIST.equals(key);
    }

    public static boolean isReservedName(String name) {
        return alias(TextFold.fold(name)) != null;
    }

    /**
     * Stable key for a user-defined type. Accents are folded and case is ignored.
     * The key does not change when the visible name is renamed.
     */
    public static String slug(String name) {
        String folded = TextFold.fold(name == null ? "" : name.trim());
        StringBuilder slug = new StringBuilder(folded.length());
        boolean dash = false;
        for (int offset = 0; offset < folded.length(); ) {
            int codePoint = folded.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (Character.isLetterOrDigit(codePoint)) {
                slug.appendCodePoint(codePoint);
                dash = false;
            } else if (!dash && slug.length() > 0) {
                slug.append('-');
                dash = true;
            }
        }
        if (!slug.isEmpty() && slug.charAt(slug.length() - 1) == '-') {
            slug.setLength(slug.length() - 1);
        }
        return slug.toString();
    }
}
