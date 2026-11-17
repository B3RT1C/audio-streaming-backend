package net.b3rt1c.music_streaming_backend.service;

import net.b3rt1c.music_streaming_backend.util.TagTypeKeys;

public record FilterClause(String typeKey, String foldedValue, int yearFrom, int yearTo, boolean year) {
    public static FilterClause value(String typeKey, String foldedValue) {
        return new FilterClause(typeKey, foldedValue, 0, 0, false);
    }

    public static FilterClause year(int from, int to) {
        return new FilterClause(TagTypeKeys.YEAR, null, from, to, true);
    }
}
