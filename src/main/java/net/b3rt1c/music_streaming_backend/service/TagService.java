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

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import net.b3rt1c.music_streaming_backend.dto.TagRequest;
import net.b3rt1c.music_streaming_backend.dto.TagResponse;
import net.b3rt1c.music_streaming_backend.dto.TagTypeRef;
import net.b3rt1c.music_streaming_backend.error.ApiException;
import net.b3rt1c.music_streaming_backend.error.ErrorCodes;
import net.b3rt1c.music_streaming_backend.model.Tag;
import net.b3rt1c.music_streaming_backend.model.TagType;
import net.b3rt1c.music_streaming_backend.repository.ArtistRepository;
import net.b3rt1c.music_streaming_backend.repository.AudioDataRepository;
import net.b3rt1c.music_streaming_backend.repository.PlaylistRepository;
import net.b3rt1c.music_streaming_backend.repository.TagRepository;
import net.b3rt1c.music_streaming_backend.util.Names;
import net.b3rt1c.music_streaming_backend.util.TagNames;
import net.b3rt1c.music_streaming_backend.util.TagTypeKeys;
import net.b3rt1c.music_streaming_backend.util.TextFold;

@Service
@RequiredArgsConstructor
public class TagService {
    private final TagRepository tagRepository;
    private final AudioDataRepository audioDataRepository;
    private final ArtistRepository artistRepository;
    private final PlaylistRepository playlistRepository;
    private final TagTypeService tagTypeService;

    @Transactional(readOnly = true)
    public boolean allExist(Set<String> nameKeys) {
        if (nameKeys == null || nameKeys.isEmpty()) {
            return true;
        }
        Set<String> found = new HashSet<>();
        for (Tag tag : tagRepository.findByNameKeyIn(nameKeys)) {
            found.add(tag.getNameKey());
        }
        return found.containsAll(nameKeys);
    }

    @Transactional(readOnly = true)
    public List<TagResponse> list(String typeToken) {
        TagType filtered = null;
        if (typeToken != null && !typeToken.isBlank()) {
            filtered = tagTypeService.findByToken(typeToken);
            if (filtered == null || TagTypeKeys.ARTIST.equals(filtered.getTypeKey())) {
                return List.of();
            }
        }
        Map<Integer, Integer> audios = counts(audioDataRepository.countByTag());
        Map<Integer, Integer> artists = counts(artistRepository.countByTag());
        Map<Integer, Integer> playlists = counts(playlistRepository.countByTag());
        TagType selected = filtered;
        return tagRepository.findAllWithType().stream()
            .filter(tag -> selected == null || selected.getId().equals(tag.getType().getId()))
            .sorted(Comparator
                .comparing((Tag tag) -> TagNames.key(tag.getName()))
                .thenComparing(Tag::getName)
                .thenComparing(tag -> tag.getType().getTypeKey()))
            .map(tag -> new TagResponse(
                tag.getId(),
                tag.getName(),
                audios.getOrDefault(tag.getId(), 0),
                artists.getOrDefault(tag.getId(), 0),
                playlists.getOrDefault(tag.getId(), 0),
                TagTypeKeys.derived(tag.getType().getTypeKey()),
                TagTypeRef.from(tag.getType())
            ))
            .toList();
    }

    /**
     * Replaces assignable tags. Derived genre, album and year tags already on the subject stay,
     * even when the request omits them or is empty.
     */
    @Transactional
    public void replace(Set<Tag> current, List<TagRequest> requested) {
        List<Tag> resolved = resolve(requested);
        LinkedHashSet<Tag> next = new LinkedHashSet<>();
        for (Tag tag : current) {
            if (tag.getType() != null && TagTypeKeys.derived(tag.getType().getTypeKey())) {
                next.add(tag);
            }
        }
        next.addAll(resolved);
        current.clear();
        current.addAll(next);
    }

    private List<Tag> resolve(List<TagRequest> requested) {
        if (requested == null) {
            throw ApiException.badRequest(ErrorCodes.INVALID_BODY, "tags is required");
        }
        if (requested.size() > TagNames.MAX_PER_SUBJECT) {
            throw ApiException.badRequest(
                ErrorCodes.INVALID_BODY,
                "At most " + TagNames.MAX_PER_SUBJECT + " tags are allowed"
            );
        }
        LinkedHashMap<String, Pending> pending = new LinkedHashMap<>();
        for (TagRequest request : requested) {
            TagType type = assignableType(request.type());
            String cleaned = Names.required(request.name(), TagNames.MAX_LENGTH, "A valid tag is required");
            pending.putIfAbsent(type.getId() + "\0" + TagNames.key(cleaned), new Pending(type, cleaned));
        }
        List<Tag> resolved = new ArrayList<>();
        for (Pending entry : pending.values()) {
            String nameKey = TagNames.key(entry.name());
            Tag tag = tagRepository.findByTypeAndNameKey(entry.type(), nameKey).orElseGet(() -> {
                Tag created = new Tag();
                created.setType(entry.type());
                created.setName(entry.name());
                created.setNameKey(nameKey);
                return tagRepository.save(created);
            });
            resolved.add(tag);
        }
        return resolved;
    }

    private TagType assignableType(String rawType) {
        if (rawType != null && !rawType.isBlank()) {
            String alias = TagTypeKeys.alias(TextFold.fold(rawType.trim()));
            if (TagTypeKeys.PLAYLIST.equals(alias)) {
                throw ApiException.badRequest(
                    ErrorCodes.INVALID_BODY,
                    "Tags of this type cannot be assigned manually"
                );
            }
        }
        TagType type = rawType == null || rawType.isBlank()
            ? tagTypeService.requireByKey(TagTypeKeys.FREE)
            : tagTypeService.findByToken(rawType);
        if (type == null) {
            throw ApiException.badRequest(ErrorCodes.INVALID_BODY, "Unknown tag type");
        }
        if (!TagTypeKeys.assignable(type.getTypeKey())) {
            throw ApiException.badRequest(
                ErrorCodes.INVALID_BODY,
                "Tags of this type cannot be assigned manually"
            );
        }
        return type;
    }

    private static Map<Integer, Integer> counts(List<Object[]> rows) {
        Map<Integer, Integer> counts = new HashMap<>();
        for (Object[] row : rows) {
            counts.put(((Number) row[0]).intValue(), ((Number) row[1]).intValue());
        }
        return counts;
    }

    private record Pending(TagType type, String name) {
    }
}
