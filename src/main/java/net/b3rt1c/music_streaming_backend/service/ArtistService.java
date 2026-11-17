package net.b3rt1c.music_streaming_backend.service;

import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import net.b3rt1c.music_streaming_backend.dto.ArtistResponse;
import net.b3rt1c.music_streaming_backend.dto.TagRequest;
import net.b3rt1c.music_streaming_backend.error.ApiException;
import net.b3rt1c.music_streaming_backend.model.Artist;
import net.b3rt1c.music_streaming_backend.repository.ArtistRepository;
import net.b3rt1c.music_streaming_backend.repository.AudioDataRepository;
import net.b3rt1c.music_streaming_backend.util.SearchParams;
import net.b3rt1c.music_streaming_backend.util.TagNames;
import net.b3rt1c.music_streaming_backend.util.TextFold;

@Service
@RequiredArgsConstructor
public class ArtistService {
    private final ArtistRepository artistRepository;
    private final AudioDataRepository audioDataRepository;
    private final TagService tagService;

    @Transactional
    public void ensure(String name) {
        if (name == null || name.isBlank()) {
            return;
        }
        if (artistRepository.existsByName(name)) {
            return;
        }
        Artist artist = new Artist();
        artist.setName(name);
        artistRepository.save(artist);
    }

    @Transactional(readOnly = true)
    public List<ArtistResponse> list(String query, List<String> tags) {
        String needle = SearchParams.needle(query);
        Set<String> tagKeys = SearchParams.tagKeys(tags);
        if (!tagService.allExist(tagKeys)) {
            return List.of();
        }
        Map<String, Integer> counts = new HashMap<>();
        for (Object[] row : audioDataRepository.countGroupedByArtist()) {
            counts.put((String) row[0], ((Number) row[1]).intValue());
        }
        Map<Integer, Artist> unique = new LinkedHashMap<>();
        for (Artist artist : artistRepository.findAllWithTags()) {
            unique.putIfAbsent(artist.getId(), artist);
        }
        return unique.values().stream()
            .filter(artist -> needle.isEmpty() || TextFold.fold(artist.getName()).contains(needle))
            .filter(artist -> SearchParams.hasEveryTag(artist.getTags(), tagKeys))
            .sorted(Comparator.comparing((Artist artist) -> TagNames.key(artist.getName()))
                .thenComparing(Artist::getName))
            .map(artist -> ArtistResponse.from(artist, counts.getOrDefault(artist.getName(), 0)))
            .toList();
    }

    @Transactional
    public ArtistResponse replaceTags(int id, List<TagRequest> rawNames) {
        Artist artist = artistRepository.findById(id)
            .orElseThrow(() -> ApiException.notFound("Artist not found"));
        tagService.replace(artist.getTags(), rawNames);
        artistRepository.save(artist);
        int audioCount = (int) audioDataRepository.countByArtist(artist.getName());
        return ArtistResponse.from(artist, audioCount);
    }
}
