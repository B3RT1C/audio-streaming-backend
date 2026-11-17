package net.b3rt1c.music_streaming_backend.service;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import net.b3rt1c.music_streaming_backend.dto.TagTypeResponse;
import net.b3rt1c.music_streaming_backend.error.ApiException;
import net.b3rt1c.music_streaming_backend.error.ErrorCodes;
import net.b3rt1c.music_streaming_backend.model.Artist;
import net.b3rt1c.music_streaming_backend.model.AudioData;
import net.b3rt1c.music_streaming_backend.model.Playlist;
import net.b3rt1c.music_streaming_backend.model.Tag;
import net.b3rt1c.music_streaming_backend.model.TagType;
import net.b3rt1c.music_streaming_backend.repository.ArtistRepository;
import net.b3rt1c.music_streaming_backend.repository.AudioDataRepository;
import net.b3rt1c.music_streaming_backend.repository.PlaylistRepository;
import net.b3rt1c.music_streaming_backend.repository.TagRepository;
import net.b3rt1c.music_streaming_backend.repository.TagTypeRepository;
import net.b3rt1c.music_streaming_backend.util.Names;
import net.b3rt1c.music_streaming_backend.util.TagTypeKeys;
import net.b3rt1c.music_streaming_backend.util.TextFold;

@Service
@RequiredArgsConstructor
public class TagTypeService {
    private static final int NAME_MAX = 100;
    private static final int COLOR_MAX = 32;

    private final TagTypeRepository tagTypeRepository;
    private final TagRepository tagRepository;
    private final AudioDataRepository audioDataRepository;
    private final ArtistRepository artistRepository;
    private final PlaylistRepository playlistRepository;

    @Transactional(readOnly = true)
    public List<TagTypeResponse> list() {
        Map<Integer, Integer> counts = new HashMap<>();
        for (Object[] row : tagRepository.countGroupedByType()) {
            counts.put(((Number) row[0]).intValue(), ((Number) row[1]).intValue());
        }
        int artists = (int) artistRepository.count();
        return tagTypeRepository.findAll().stream()
            .sorted(typeOrder())
            .map(type -> toResponse(
                type,
                TagTypeKeys.ARTIST.equals(type.getTypeKey())
                    ? artists
                    : counts.getOrDefault(type.getId(), 0)
            ))
            .toList();
    }

    @Transactional
    public TagTypeResponse create(String name, String color) {
        String cleaned = Names.required(name, NAME_MAX, "A valid name is required");
        String key = TagTypeKeys.slug(cleaned);
        if (key.isEmpty() || key.length() > NAME_MAX) {
            throw ApiException.badRequest(ErrorCodes.INVALID_NAME, "A valid name is required");
        }
        if (TagTypeKeys.isReservedName(cleaned) || TagTypeKeys.isReservedKey(key)) {
            throw ApiException.conflict("This name is reserved");
        }
        ensureUnique(key, cleaned, null);
        TagType type = new TagType();
        type.setTypeKey(key);
        type.setName(cleaned);
        type.setSystem(false);
        type.setColor(cleanColor(color));
        return toResponse(tagTypeRepository.save(type), 0);
    }

    @Transactional
    public TagTypeResponse patch(int id, boolean nameSet, String name, boolean colorSet, String color) {
        TagType type = require(id);
        if (type.isSystem()) {
            throw ApiException.conflict("System tag types cannot be changed");
        }
        if (nameSet) {
            String cleaned = Names.required(name, NAME_MAX, "A valid name is required");
            if (TagTypeKeys.isReservedName(cleaned)) {
                throw ApiException.conflict("This name is reserved");
            }
            ensureUnique(type.getTypeKey(), cleaned, type.getId());
            type.setName(cleaned);
        }
        if (colorSet) {
            type.setColor(cleanColor(color));
        }
        tagTypeRepository.save(type);
        return toResponse(type, tagCount(type.getId()));
    }

    @Transactional
    public void delete(int id) {
        TagType type = require(id);
        if (type.isSystem()) {
            throw ApiException.conflict("System tag types cannot be deleted");
        }
        List<Tag> tags = tagRepository.findByType(type);
        if (!tags.isEmpty()) {
            Set<Integer> tagIds = new HashSet<>();
            for (Tag tag : tags) {
                tagIds.add(tag.getId());
            }
            for (AudioData audio : audioDataRepository.findAllWithTags()) {
                audio.getTags().removeIf(tag -> tagIds.contains(tag.getId()));
            }
            for (Artist artist : artistRepository.findAllWithTags()) {
                artist.getTags().removeIf(tag -> tagIds.contains(tag.getId()));
            }
            for (Playlist playlist : playlistRepository.findAll()) {
                playlist.getTags().removeIf(tag -> tagIds.contains(tag.getId()));
            }
            audioDataRepository.flush();
            tagRepository.deleteAll(tags);
        }
        tagTypeRepository.delete(type);
    }

    @Transactional(readOnly = true)
    public TagType findByToken(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String folded = TextFold.fold(raw.trim());
        String alias = TagTypeKeys.alias(folded);
        if (TagTypeKeys.PLAYLIST.equals(alias)) {
            return null;
        }
        if (alias != null) {
            return tagTypeRepository.findByTypeKey(alias).orElse(null);
        }
        for (TagType type : tagTypeRepository.findAll()) {
            if (folded.equals(TextFold.fold(type.getTypeKey())) || folded.equals(TextFold.fold(type.getName()))) {
                return type;
            }
        }
        return null;
    }

    @Transactional(readOnly = true)
    public TagType requireByKey(String key) {
        return tagTypeRepository.findByTypeKey(key)
            .orElseThrow(() -> new ApiException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                ErrorCodes.INTERNAL,
                "System tag type is missing"
            ));
    }

    private TagType require(int id) {
        return tagTypeRepository.findById(id)
            .orElseThrow(() -> ApiException.notFound("Tag type not found"));
    }

    private void ensureUnique(String key, String name, Integer ignoreId) {
        tagTypeRepository.findByTypeKey(key).ifPresent(existing -> {
            if (!existing.getId().equals(ignoreId)) {
                throw ApiException.conflict("A tag type with this key already exists");
            }
        });
        String folded = TextFold.fold(name);
        for (TagType type : tagTypeRepository.findAll()) {
            if (type.getId().equals(ignoreId)) {
                continue;
            }
            if (folded.equals(TextFold.fold(type.getName()))) {
                throw ApiException.conflict("A tag type with this name already exists");
            }
        }
    }

    private int tagCount(int typeId) {
        for (Object[] row : tagRepository.countGroupedByType()) {
            if (((Number) row[0]).intValue() == typeId) {
                return ((Number) row[1]).intValue();
            }
        }
        return 0;
    }

    private static String cleanColor(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.indexOf('\0') >= 0 || trimmed.length() > COLOR_MAX) {
            throw ApiException.badRequest(ErrorCodes.INVALID_BODY, "color must be at most 32 characters");
        }
        return trimmed;
    }

    private static Comparator<TagType> typeOrder() {
        return Comparator
            .comparingInt((TagType type) -> systemIndex(type.getTypeKey()))
            .thenComparing(type -> TextFold.fold(type.getName()))
            .thenComparing(TagType::getName);
    }

    private static int systemIndex(String key) {
        List<TagTypeKeys.Seed> system = TagTypeKeys.SYSTEM;
        for (int index = 0; index < system.size(); index++) {
            if (system.get(index).key().equals(key)) {
                return index;
            }
        }
        return Integer.MAX_VALUE;
    }

    private static TagTypeResponse toResponse(TagType type, int count) {
        return new TagTypeResponse(
            type.getId(),
            type.getTypeKey(),
            type.getName(),
            type.isSystem(),
            type.getColor(),
            count
        );
    }
}
