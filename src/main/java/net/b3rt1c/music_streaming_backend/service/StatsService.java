package net.b3rt1c.music_streaming_backend.service;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import net.b3rt1c.music_streaming_backend.config.StatsProperties;
import net.b3rt1c.music_streaming_backend.dto.StatsRankResponse;
import net.b3rt1c.music_streaming_backend.dto.StatsSummaryResponse;
import net.b3rt1c.music_streaming_backend.dto.StatsTimelinePoint;
import net.b3rt1c.music_streaming_backend.error.ApiException;
import net.b3rt1c.music_streaming_backend.error.ErrorCodes;

@Service
@RequiredArgsConstructor
public class StatsService {
    static final int DEFAULT_LIMIT = 20;

    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final String COUNTED_PLAYS = "sum(case when p.counted = true then 1 else 0 end)";
    private static final String LISTENED = "sum(p.listenedSeconds)";
    private static final String ORDER = " order by " + COUNTED_PLAYS + " desc, " + LISTENED + " desc, ";

    private final EntityManager entityManager;
    private final StatsProperties statsProperties;

    @Transactional(readOnly = true)
    public StatsSummaryResponse summary(String from, String to) {
        Range window = range(from, to);
        String where = where(window);
        long plays = scalar("select count(p) from PlayEvent p where p.counted = true" + and(where), window);
        long listenedSeconds = scalar("select coalesce(sum(p.listenedSeconds), 0) from PlayEvent p" + where, window);
        long distinctAudios = scalar(
            "select count(distinct p.audio.id) from PlayEvent p where p.counted = true" + and(where),
            window
        );
        long distinctArtists = scalar(
            "select count(distinct ar.id) from PlayEvent p, Artist ar"
                + " where p.counted = true and p.audio.artist = ar.name" + and(where),
            window
        );
        return new StatsSummaryResponse(plays, listenedSeconds, distinctAudios, distinctArtists, window.from(), window.to());
    }

    @Transactional(readOnly = true)
    public List<StatsRankResponse> top(String kindRaw, String from, String to, String limitRaw, String tagType) {
        Kind kind = Kind.parse(kindRaw);
        Range window = range(from, to);
        int limit = limit(limitRaw);
        String having = " group by %s having " + COUNTED_PLAYS + " > 0" + ORDER;
        return switch (kind) {
            case audio -> rank(
                "select a.id, a.title, a.artist, " + COUNTED_PLAYS + ", " + LISTENED
                    + " from PlayEvent p join p.audio a"
                    + where(window)
                    + having.formatted("a.id, a.title, a.artist") + "a.id asc",
                window,
                Map.of(),
                limit,
                true
            );
            case artist -> rank(
                "select ar.id, ar.name, " + COUNTED_PLAYS + ", " + LISTENED
                    + " from PlayEvent p, Artist ar where p.audio.artist = ar.name"
                    + and(where(window))
                    + having.formatted("ar.id, ar.name") + "ar.id asc",
                window,
                Map.of(),
                limit,
                false
            );
            case tag -> {
                String typeKey = blankToNull(tagType);
                Map<String, Object> params = new HashMap<>();
                String typeFilter = "";
                if (typeKey != null) {
                    typeFilter = " and tt.typeKey = :tagType";
                    params.put("tagType", typeKey);
                }
                yield rank(
                    "select t.id, t.name, tt.name, " + COUNTED_PLAYS + ", " + LISTENED
                        + " from PlayEvent p join p.audio a join a.tags t join t.type tt"
                        + where(window, typeFilter)
                        + having.formatted("t.id, t.name, tt.name") + "t.id asc",
                    window,
                    params,
                    limit,
                    true
                );
            }
            case playlist -> rank(
                "select pl.id, pl.name, " + COUNTED_PLAYS + ", " + LISTENED
                    + " from PlayEvent p join p.playlist pl"
                    + where(window)
                    + having.formatted("pl.id, pl.name") + "pl.id asc",
                window,
                Map.of(),
                limit,
                false
            );
        };
    }

    @Transactional(readOnly = true)
    public List<StatsTimelinePoint> timeline(String granularityRaw, String from, String to) {
        Granularity granularity = Granularity.parse(granularityRaw);
        Range window = range(from, to);
        ZoneId zone = zone();
        var query = entityManager.createQuery(
            "select p.startedAt, p.listenedSeconds, p.counted from PlayEvent p" + where(window)
        );
        bind(query, window, Map.of());
        Map<String, long[]> buckets = new HashMap<>();
        LocalDate minDay = null;
        LocalDate maxDay = null;
        for (Object row : query.getResultList()) {
            Object[] cells = (Object[]) row;
            Instant startedAt = (Instant) cells[0];
            int seconds = ((Number) cells[1]).intValue();
            boolean counted = Boolean.TRUE.equals(cells[2]);
            LocalDate day = LocalDate.ofInstant(startedAt, zone);
            long[] bucket = buckets.computeIfAbsent(periodKey(granularity, day), ignored -> new long[2]);
            if (counted) {
                bucket[0]++;
            }
            bucket[1] += seconds;
            if (minDay == null || day.isBefore(minDay)) {
                minDay = day;
            }
            if (maxDay == null || day.isAfter(maxDay)) {
                maxDay = day;
            }
        }
        LocalDate rangeStart;
        LocalDate rangeEnd;
        if (window.from() != null && window.to() != null) {
            rangeStart = window.from();
            rangeEnd = window.to();
        } else if (window.from() != null) {
            if (maxDay == null) {
                return List.of();
            }
            rangeStart = window.from();
            rangeEnd = maxDay;
        } else if (window.to() != null) {
            if (minDay == null) {
                return List.of();
            }
            rangeStart = minDay;
            rangeEnd = window.to();
        } else if (minDay == null) {
            return List.of();
        } else {
            rangeStart = minDay;
            rangeEnd = maxDay;
        }
        List<StatsTimelinePoint> points = new ArrayList<>();
        LocalDate cursor = truncate(granularity, rangeStart);
        LocalDate last = truncate(granularity, rangeEnd);
        while (!cursor.isAfter(last)) {
            long[] bucket = buckets.get(periodKey(granularity, cursor));
            points.add(new StatsTimelinePoint(
                periodKey(granularity, cursor),
                bucket == null ? 0 : bucket[0],
                bucket == null ? 0 : bucket[1]
            ));
            cursor = advance(granularity, cursor);
        }
        return points;
    }

    private List<StatsRankResponse> rank(
            String jpql,
            Range window,
            Map<String, Object> params,
            int limit,
            boolean secondary) {
        var query = entityManager.createQuery(jpql);
        bind(query, window, params);
        query.setMaxResults(limit);
        List<StatsRankResponse> result = new ArrayList<>();
        for (Object row : query.getResultList()) {
            Object[] cells = (Object[]) row;
            if (secondary) {
                result.add(new StatsRankResponse(
                    ((Number) cells[0]).intValue(),
                    (String) cells[1],
                    (String) cells[2],
                    number(cells[3]),
                    number(cells[4])
                ));
            } else {
                result.add(new StatsRankResponse(
                    ((Number) cells[0]).intValue(),
                    (String) cells[1],
                    null,
                    number(cells[2]),
                    number(cells[3])
                ));
            }
        }
        return result;
    }

    private long scalar(String jpql, Range window) {
        var query = entityManager.createQuery(jpql);
        bind(query, window, Map.of());
        return number(query.getSingleResult());
    }

    private void bind(jakarta.persistence.Query query, Range window, Map<String, Object> params) {
        if (window.start() != null) {
            query.setParameter("rangeStart", window.start());
        }
        if (window.end() != null) {
            query.setParameter("rangeEnd", window.end());
        }
        params.forEach(query::setParameter);
    }

    private Range range(String fromRaw, String toRaw) {
        LocalDate from = date(fromRaw, "from");
        LocalDate to = date(toRaw, "to");
        if (from != null && to != null && from.isAfter(to)) {
            throw ApiException.badRequest(ErrorCodes.INVALID_QUERY, "from must be on or before to");
        }
        ZoneId zone = zone();
        Instant start = from == null ? null : from.atStartOfDay(zone).toInstant();
        Instant end = to == null ? null : to.plusDays(1).atStartOfDay(zone).toInstant();
        return new Range(start, end, from, to);
    }

    private ZoneId zone() {
        return ZoneId.of(statsProperties.getZone());
    }

    private static String where(Range window) {
        return where(window, "");
    }

    /**
     * Shared time predicate. Stats are instance-wide; a nullable user id can be added here in 0.8.
     */
    private static String where(Range window, String extraAnd) {
        StringBuilder clause = new StringBuilder();
        if (window.start() != null) {
            clause.append("p.startedAt >= :rangeStart");
        }
        if (window.end() != null) {
            if (!clause.isEmpty()) {
                clause.append(" and ");
            }
            clause.append("p.startedAt < :rangeEnd");
        }
        String extra = extraAnd == null ? "" : extraAnd.trim();
        if (extra.startsWith("and ")) {
            extra = extra.substring(4).trim();
        }
        if (!extra.isEmpty()) {
            if (!clause.isEmpty()) {
                clause.append(" and ");
            }
            clause.append(extra);
        }
        if (clause.isEmpty()) {
            return "";
        }
        return " where " + clause;
    }

    private static String and(String where) {
        if (where.isEmpty()) {
            return "";
        }
        return " and " + where.substring(" where ".length());
    }

    private static LocalDate date(String raw, String name) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        if (!trimmed.matches("\\d{4}-\\d{2}-\\d{2}")) {
            throw ApiException.badRequest(ErrorCodes.INVALID_QUERY, name + " must be a date (YYYY-MM-DD)");
        }
        try {
            return LocalDate.parse(trimmed);
        } catch (DateTimeException exception) {
            throw ApiException.badRequest(ErrorCodes.INVALID_QUERY, name + " must be a date (YYYY-MM-DD)");
        }
    }

    private static int limit(String raw) {
        if (raw == null || raw.isBlank()) {
            return DEFAULT_LIMIT;
        }
        try {
            int value = Integer.parseInt(raw.trim());
            if (value < 1) {
                throw new NumberFormatException();
            }
            return value;
        } catch (NumberFormatException exception) {
            throw ApiException.badRequest(ErrorCodes.INVALID_QUERY, "limit must be a positive integer");
        }
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static long number(Object value) {
        if (value == null) {
            return 0L;
        }
        return ((Number) value).longValue();
    }

    private static LocalDate truncate(Granularity granularity, LocalDate date) {
        return switch (granularity) {
            case day -> date;
            case month -> date.withDayOfMonth(1);
            case year -> date.withDayOfYear(1);
        };
    }

    private static LocalDate advance(Granularity granularity, LocalDate date) {
        return switch (granularity) {
            case day -> date.plusDays(1);
            case month -> date.plusMonths(1);
            case year -> date.plusYears(1);
        };
    }

    private static String periodKey(Granularity granularity, LocalDate date) {
        return switch (granularity) {
            case day -> date.toString();
            case month -> date.format(MONTH);
            case year -> Integer.toString(date.getYear());
        };
    }

    private record Range(Instant start, Instant end, LocalDate from, LocalDate to) {
    }

    private enum Kind {
        audio, artist, tag, playlist;

        static Kind parse(String raw) {
            if (raw == null || raw.isBlank()) {
                throw ApiException.badRequest(ErrorCodes.INVALID_QUERY, "kind is required");
            }
            try {
                return Kind.valueOf(raw.trim().toLowerCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                throw ApiException.badRequest(
                    ErrorCodes.INVALID_QUERY,
                    "kind must be audio, artist, tag or playlist"
                );
            }
        }
    }

    private enum Granularity {
        day, month, year;

        static Granularity parse(String raw) {
            if (raw == null || raw.isBlank()) {
                throw ApiException.badRequest(ErrorCodes.INVALID_QUERY, "granularity is required");
            }
            try {
                return Granularity.valueOf(raw.trim().toLowerCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                throw ApiException.badRequest(
                    ErrorCodes.INVALID_QUERY,
                    "granularity must be day, month or year"
                );
            }
        }
    }
}
