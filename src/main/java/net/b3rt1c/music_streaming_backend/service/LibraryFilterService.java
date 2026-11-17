package net.b3rt1c.music_streaming_backend.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import net.b3rt1c.music_streaming_backend.dto.FilterSuggestion;
import net.b3rt1c.music_streaming_backend.error.ApiException;
import net.b3rt1c.music_streaming_backend.error.ErrorCodes;
import net.b3rt1c.music_streaming_backend.model.AudioData;
import net.b3rt1c.music_streaming_backend.model.Playlist;
import net.b3rt1c.music_streaming_backend.model.Tag;
import net.b3rt1c.music_streaming_backend.model.TagType;
import net.b3rt1c.music_streaming_backend.repository.AudioDataRepository;
import net.b3rt1c.music_streaming_backend.repository.PlaylistItemRepository;
import net.b3rt1c.music_streaming_backend.repository.PlaylistRepository;
import net.b3rt1c.music_streaming_backend.repository.TagTypeRepository;
import net.b3rt1c.music_streaming_backend.util.TagTypeKeys;
import net.b3rt1c.music_streaming_backend.util.TextFold;

@Service
@RequiredArgsConstructor
public class LibraryFilterService {
    private static final Pattern YEAR_FILTER = Pattern.compile("(\\d{4})-(\\d{4})|(\\d{4})");

    private final AudioDataRepository audioDataRepository;
    private final PlaylistRepository playlistRepository;
    private final PlaylistItemRepository playlistItemRepository;
    private final TagTypeRepository tagTypeRepository;
    private final TagTypeService tagTypeService;

    public List<FilterClause> parse(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<FilterClause> clauses = new ArrayList<>();
        for (String token : raw) {
            if (token == null || token.isBlank()) {
                continue;
            }
            clauses.add(parseOne(token.trim()));
        }
        return clauses;
    }

    @Transactional(readOnly = true)
    public List<FilterSuggestion> suggest(String prefix, String query) {
        List<AudioData> audios = distinct(audioDataRepository.findAllWithTags());
        Map<Integer, Set<String>> playlistNames = playlistNamesByAudio();
        Map<Integer, Set<String>> playlistFolds = foldsByAudio(playlistNames);
        Map<String, Candidate> candidates = new LinkedHashMap<>();
        for (AudioData audio : audios) {
            consider(candidates, TagTypeKeys.ARTIST, audio.getArtist(), audio.getId());
            consider(candidates, TagTypeKeys.GENRE, audio.getGenre(), audio.getId());
            consider(candidates, TagTypeKeys.ALBUM, audio.getAlbum(), audio.getId());
            if (audio.getYear() != null) {
                consider(candidates, TagTypeKeys.YEAR, Integer.toString(audio.getYear()), audio.getId());
            }
            if (audio.getTags() != null) {
                for (Tag tag : audio.getTags()) {
                    if (tag.getType() == null) {
                        continue;
                    }
                    consider(candidates, tag.getType().getTypeKey(), tag.getName(), audio.getId());
                }
            }
            for (String name : playlistNames.getOrDefault(audio.getId(), Set.of())) {
                consider(candidates, TagTypeKeys.PLAYLIST, name, audio.getId());
            }
        }

        Set<String> types = suggestTypes(prefix);
        String needle = query == null ? "" : TextFold.fold(query.trim());
        List<FilterSuggestion> suggestions = new ArrayList<>();
        for (Candidate candidate : candidates.values()) {
            if (types != null && !types.contains(candidate.type)) {
                continue;
            }
            if (!needle.isEmpty() && !candidate.folded.startsWith(needle)) {
                continue;
            }
            FilterClause clause = TagTypeKeys.YEAR.equals(candidate.type)
                ? FilterClause.year(Integer.parseInt(candidate.folded), Integer.parseInt(candidate.folded))
                : FilterClause.value(candidate.type, candidate.folded);
            int count = 0;
            for (AudioData audio : audios) {
                if (matches(audio, clause, playlistFolds)) {
                    count++;
                }
            }
            if (count > 0) {
                suggestions.add(new FilterSuggestion(candidate.type, candidate.display(), count));
            }
        }
        suggestions.sort(Comparator
            .comparing(FilterSuggestion::type)
            .thenComparing(suggestion -> TextFold.fold(suggestion.value()))
            .thenComparing(FilterSuggestion::value));
        return suggestions;
    }

    public Map<Integer, Set<String>> playlistFoldsByAudio() {
        return foldsByAudio(playlistNamesByAudio());
    }

    public boolean matchesAll(AudioData audio, List<FilterClause> clauses, Map<Integer, Set<String>> playlistFolds) {
        for (FilterClause clause : clauses) {
            if (!matches(audio, clause, playlistFolds)) {
                return false;
            }
        }
        return true;
    }

    public boolean matches(AudioData audio, FilterClause clause, Map<Integer, Set<String>> playlistFolds) {
        if (clause.year()) {
            return yearHit(audio, clause.yearFrom(), clause.yearTo());
        }
        String type = clause.typeKey();
        if (TagTypeKeys.ARTIST.equals(type)) {
            return equalsFold(audio.getArtist(), clause.foldedValue());
        }
        if (TagTypeKeys.PLAYLIST.equals(type)) {
            return playlistFolds.getOrDefault(audio.getId(), Set.of()).contains(clause.foldedValue());
        }
        if (TagTypeKeys.ALBUM.equals(type) && equalsFold(audio.getAlbum(), clause.foldedValue())) {
            return true;
        }
        if (TagTypeKeys.GENRE.equals(type) && equalsFold(audio.getGenre(), clause.foldedValue())) {
            return true;
        }
        return tagHit(audio, type, clause.foldedValue());
    }

    private FilterClause parseOne(String token) {
        if (token.startsWith("#")) {
            return freeClause(token.substring(1));
        }
        int colon = token.indexOf(':');
        if (colon < 0) {
            return freeClause(token);
        }
        String prefix = token.substring(0, colon).trim();
        String value = token.substring(colon + 1).trim();
        if (prefix.isEmpty() || value.isEmpty()) {
            throw ApiException.badRequest(ErrorCodes.INVALID_QUERY, "Filter is missing a prefix or a value");
        }
        String typeKey = resolveFilterType(prefix);
        if (TagTypeKeys.YEAR.equals(typeKey)) {
            return yearClause(value);
        }
        String folded = TextFold.fold(value);
        if (folded.isEmpty()) {
            throw ApiException.badRequest(ErrorCodes.INVALID_QUERY, "Filter value is empty");
        }
        return FilterClause.value(typeKey, folded);
    }

    private String resolveFilterType(String prefix) {
        String alias = TagTypeKeys.alias(TextFold.fold(prefix));
        if (alias != null) {
            return alias;
        }
        TagType type = tagTypeService.findByToken(prefix);
        if (type == null) {
            throw ApiException.badRequest(ErrorCodes.INVALID_QUERY, "Unknown filter prefix");
        }
        return type.getTypeKey();
    }

    private static FilterClause freeClause(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty() || TextFold.fold(value).isEmpty()) {
            throw ApiException.badRequest(ErrorCodes.INVALID_QUERY, "Filter value is empty");
        }
        return FilterClause.value(TagTypeKeys.FREE, TextFold.fold(value));
    }

    private static FilterClause yearClause(String value) {
        Matcher matcher = YEAR_FILTER.matcher(value);
        if (!matcher.matches()) {
            throw ApiException.badRequest(ErrorCodes.INVALID_QUERY, "year filter must be YYYY or YYYY-YYYY");
        }
        int from;
        int to;
        if (matcher.group(1) != null) {
            from = Integer.parseInt(matcher.group(1));
            to = Integer.parseInt(matcher.group(2));
        } else {
            from = Integer.parseInt(matcher.group(3));
            to = from;
        }
        if (from > to) {
            int swap = from;
            from = to;
            to = swap;
        }
        return FilterClause.year(from, to);
    }

    private Set<String> suggestTypes(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return null;
        }
        String folded = TextFold.fold(prefix.trim());
        LinkedHashSet<String> exact = new LinkedHashSet<>();
        String alias = TagTypeKeys.alias(folded);
        if (alias != null) {
            exact.add(alias);
        }
        for (TagType type : tagTypeRepository.findAll()) {
            if (folded.equals(TextFold.fold(type.getTypeKey())) || folded.equals(TextFold.fold(type.getName()))) {
                exact.add(type.getTypeKey());
            }
        }
        if (!exact.isEmpty()) {
            return exact;
        }
        LinkedHashSet<String> partial = new LinkedHashSet<>();
        for (Map.Entry<String, String> entry : TagTypeKeys.aliases().entrySet()) {
            if (entry.getKey().startsWith(folded)) {
                partial.add(entry.getValue());
            }
        }
        for (TagType type : tagTypeRepository.findAll()) {
            if (TextFold.fold(type.getTypeKey()).startsWith(folded)
                    || TextFold.fold(type.getName()).startsWith(folded)) {
                partial.add(type.getTypeKey());
            }
        }
        return partial;
    }

    private Map<Integer, Set<String>> playlistNamesByAudio() {
        Map<Integer, String> names = new HashMap<>();
        for (Playlist playlist : playlistRepository.findAll()) {
            names.put(playlist.getId(), playlist.getName());
        }
        Map<Integer, Set<String>> byAudio = new HashMap<>();
        for (Object[] row : playlistItemRepository.findPlaylistAndAudioIds()) {
            int playlistId = ((Number) row[0]).intValue();
            int audioId = ((Number) row[1]).intValue();
            String name = names.get(playlistId);
            if (name == null || name.isBlank()) {
                continue;
            }
            byAudio.computeIfAbsent(audioId, ignored -> new HashSet<>()).add(name.trim());
        }
        return byAudio;
    }

    private static Map<Integer, Set<String>> foldsByAudio(Map<Integer, Set<String>> namesByAudio) {
        Map<Integer, Set<String>> folds = new HashMap<>();
        namesByAudio.forEach((audioId, names) -> {
            Set<String> folded = new HashSet<>();
            for (String name : names) {
                String value = TextFold.fold(name.trim());
                if (!value.isEmpty()) {
                    folded.add(value);
                }
            }
            folds.put(audioId, folded);
        });
        return folds;
    }

    private static boolean yearHit(AudioData audio, int from, int to) {
        if (audio.getYear() != null && audio.getYear() >= from && audio.getYear() <= to) {
            return true;
        }
        if (audio.getTags() == null) {
            return false;
        }
        for (Tag tag : audio.getTags()) {
            if (tag.getType() == null || !TagTypeKeys.YEAR.equals(tag.getType().getTypeKey())) {
                continue;
            }
            Integer year = AudioMetadataReader.parseYear(tag.getName());
            if (year != null && year >= from && year <= to) {
                return true;
            }
        }
        return false;
    }

    private static boolean tagHit(AudioData audio, String typeKey, String folded) {
        if (audio.getTags() == null) {
            return false;
        }
        for (Tag tag : audio.getTags()) {
            if (tag.getType() != null
                    && typeKey.equals(tag.getType().getTypeKey())
                    && TextFold.fold(tag.getName()).equals(folded)) {
                return true;
            }
        }
        return false;
    }

    private static boolean equalsFold(String value, String folded) {
        return value != null && folded != null && TextFold.fold(value.trim()).equals(folded);
    }

    private static void consider(Map<String, Candidate> candidates, String type, String raw, int audioId) {
        if (raw == null || raw.isBlank()) {
            return;
        }
        String spelling = raw.trim();
        String folded = TextFold.fold(spelling);
        if (folded.isEmpty()) {
            return;
        }
        candidates.computeIfAbsent(type + "\0" + folded, ignored -> new Candidate(type, folded))
            .add(spelling, audioId);
    }

    private static List<AudioData> distinct(List<AudioData> audios) {
        Map<Integer, AudioData> unique = new LinkedHashMap<>();
        for (AudioData audio : audios) {
            unique.putIfAbsent(audio.getId(), audio);
        }
        return new ArrayList<>(unique.values());
    }

    private static final class Candidate {
        private final String type;
        private final String folded;
        private final Map<String, Set<Integer>> spellings = new HashMap<>();

        private Candidate(String type, String folded) {
            this.type = type;
            this.folded = folded;
        }

        private void add(String spelling, int audioId) {
            spellings.computeIfAbsent(spelling, ignored -> new HashSet<>()).add(audioId);
        }

        private String display() {
            String best = null;
            int bestScore = -1;
            for (Map.Entry<String, Set<Integer>> entry : spellings.entrySet()) {
                int score = entry.getValue().size();
                if (best == null
                        || score > bestScore
                        || (score == bestScore && entry.getKey().compareTo(best) < 0)) {
                    best = entry.getKey();
                    bestScore = score;
                }
            }
            return best;
        }
    }
}
