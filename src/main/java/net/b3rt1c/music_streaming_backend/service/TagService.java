package net.b3rt1c.music_streaming_backend.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import net.b3rt1c.music_streaming_backend.dto.TagResponse;
import net.b3rt1c.music_streaming_backend.error.ApiException;
import net.b3rt1c.music_streaming_backend.error.ErrorCodes;
import net.b3rt1c.music_streaming_backend.model.Tag;
import net.b3rt1c.music_streaming_backend.repository.ArtistRepository;
import net.b3rt1c.music_streaming_backend.repository.AudioDataRepository;
import net.b3rt1c.music_streaming_backend.repository.PlaylistRepository;
import net.b3rt1c.music_streaming_backend.repository.TagRepository;
import net.b3rt1c.music_streaming_backend.util.Names;
import net.b3rt1c.music_streaming_backend.util.TagNames;

@Service
@RequiredArgsConstructor
public class TagService {
    private final TagRepository tagRepository;
    private final AudioDataRepository audioDataRepository;
    private final ArtistRepository artistRepository;
    private final PlaylistRepository playlistRepository;

    @Transactional(readOnly = true)
    public boolean allExist(Set<String> nameKeys) {
        if (nameKeys == null || nameKeys.isEmpty()) {
            return true;
        }
        return tagRepository.findByNameKeyIn(nameKeys).size() >= nameKeys.size();
    }

    @Transactional(readOnly = true)
    public List<TagResponse> list() {
        Map<Integer, Integer> audios = counts(audioDataRepository.countByTag());
        Map<Integer, Integer> artists = counts(artistRepository.countByTag());
        Map<Integer, Integer> playlists = counts(playlistRepository.countByTag());
        return tagRepository.findAll().stream()
            .sorted(Comparator.comparing((Tag tag) -> TagNames.key(tag.getName())).thenComparing(Tag::getName))
            .map(tag -> new TagResponse(
                tag.getId(),
                tag.getName(),
                audios.getOrDefault(tag.getId(), 0),
                artists.getOrDefault(tag.getId(), 0),
                playlists.getOrDefault(tag.getId(), 0)
            ))
            .toList();
    }

    @Transactional
    public void replace(Set<Tag> current, List<String> rawNames) {
        List<Tag> resolved = resolve(rawNames);
        current.clear();
        current.addAll(resolved);
    }

    private List<Tag> resolve(List<String> rawNames) {
        if (rawNames.size() > TagNames.MAX_PER_SUBJECT) {
            throw ApiException.badRequest(
                ErrorCodes.INVALID_BODY,
                "At most " + TagNames.MAX_PER_SUBJECT + " tags are allowed"
            );
        }
        LinkedHashMap<String, String> byKey = new LinkedHashMap<>();
        for (String raw : rawNames) {
            String cleaned = Names.required(raw, TagNames.MAX_LENGTH, "A valid tag is required");
            byKey.putIfAbsent(TagNames.key(cleaned), cleaned);
        }
        if (byKey.isEmpty()) {
            return List.of();
        }
        Map<String, Tag> existing = new HashMap<>();
        for (Tag tag : tagRepository.findByNameKeyIn(byKey.keySet())) {
            existing.put(tag.getNameKey(), tag);
        }
        List<Tag> resolved = new ArrayList<>();
        for (Map.Entry<String, String> entry : byKey.entrySet()) {
            Tag tag = existing.get(entry.getKey());
            if (tag == null) {
                tag = new Tag();
                tag.setName(entry.getValue());
                tag.setNameKey(entry.getKey());
                tag = tagRepository.save(tag);
            }
            resolved.add(tag);
        }
        return resolved;
    }

    private static Map<Integer, Integer> counts(List<Object[]> rows) {
        Map<Integer, Integer> counts = new HashMap<>();
        for (Object[] row : rows) {
            counts.put(((Number) row[0]).intValue(), ((Number) row[1]).intValue());
        }
        return counts;
    }
}
