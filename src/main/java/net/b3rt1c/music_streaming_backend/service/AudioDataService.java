package net.b3rt1c.music_streaming_backend.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import net.b3rt1c.music_streaming_backend.dto.AudioMetadataPatch;
import net.b3rt1c.music_streaming_backend.dto.TagRequest;
import net.b3rt1c.music_streaming_backend.error.ApiException;
import net.b3rt1c.music_streaming_backend.model.AudioData;
import net.b3rt1c.music_streaming_backend.repository.ArtistRepository;
import net.b3rt1c.music_streaming_backend.repository.AudioDataRepository;
import net.b3rt1c.music_streaming_backend.repository.PlaylistItemRepository;
import net.b3rt1c.music_streaming_backend.repository.PlaylistRepository;
import net.b3rt1c.music_streaming_backend.util.Names;
import net.b3rt1c.music_streaming_backend.util.SearchParams;
import net.b3rt1c.music_streaming_backend.util.TextFold;

@Service
@RequiredArgsConstructor
public class AudioDataService {
    private static final int MAX_TEXT = 500;

    private final AudioDataRepository audioDataRepository;
    private final PlaylistItemRepository playlistItemRepository;
    private final PlaylistRepository playlistRepository;
    private final ArtistRepository artistRepository;
    private final TagService tagService;
    private final ArtistService artistService;
    private final SystemTagService systemTagService;
    private final LibraryFilterService libraryFilterService;

    @Transactional(readOnly = true)
    public List<AudioData> search(AudioQuery query) {
        String text = SearchParams.needle(query.text());
        String title = SearchParams.needle(query.title());
        String artist = SearchParams.needle(query.artist());
        Set<String> tags = SearchParams.tagKeys(query.tags());
        Set<String> artistTags = SearchParams.tagKeys(query.artistTags());
        Set<String> playlistTags = SearchParams.tagKeys(query.playlistTags());
        List<FilterClause> filters = libraryFilterService.parse(query.filters());

        if (!tagService.allExist(tags) || !tagService.allExist(artistTags) || !tagService.allExist(playlistTags)) {
            return List.of();
        }

        Set<String> artistNames = artistTags.isEmpty() ? null : artistNamesWithAllTags(artistTags);
        if (artistNames != null && artistNames.isEmpty()) {
            return List.of();
        }
        Set<Integer> playlistAudioIds = playlistTags.isEmpty() ? null : audioIdsInPlaylistsWithAllTags(playlistTags);
        if (playlistAudioIds != null && playlistAudioIds.isEmpty()) {
            return List.of();
        }

        List<AudioData> audios = distinctInOrder(audioDataRepository.findAllWithTags());
        Map<Integer, Set<String>> playlistFolds = filters.isEmpty()
            ? Map.of()
            : libraryFilterService.playlistFoldsByAudio();
        boolean unrestricted = text.isEmpty()
            && title.isEmpty()
            && artist.isEmpty()
            && tags.isEmpty()
            && artistNames == null
            && playlistAudioIds == null
            && filters.isEmpty();
        List<AudioData> filtered = unrestricted ? audios : audios.stream()
            .filter(audio -> text.isEmpty() || matchesQuery(audio, text))
            .filter(audio -> title.isEmpty() || matchesTitle(audio, title))
            .filter(audio -> artist.isEmpty() || contains(audio.getArtist(), artist))
            .filter(audio -> SearchParams.hasEveryTag(audio.getTags(), tags))
            .filter(audio -> artistNames == null || artistNames.contains(audio.getArtist()))
            .filter(audio -> playlistAudioIds == null || playlistAudioIds.contains(audio.getId()))
            .filter(audio -> libraryFilterService.matchesAll(audio, filters, playlistFolds))
            .toList();
        if (!query.shuffle()) {
            return filtered;
        }
        return SearchParams.shuffled(filtered, query.seed());
    }

    public AudioData require(int id) {
        return audioDataRepository.findById(id)
            .orElseThrow(() -> ApiException.notFound("Audio track not found"));
    }

    public boolean existsByContentHash(String contentHash) {
        return contentHash != null && audioDataRepository.existsByContentHash(contentHash);
    }

    public boolean existsByOriginalPath(String originalPath) {
        return originalPath != null && audioDataRepository.existsByOriginalPath(originalPath);
    }

    @Transactional
    public AudioData save(AudioData audioData) {
        artistService.ensure(audioData.getArtist());
        systemTagService.syncDerived(audioData);
        return audioDataRepository.saveAndFlush(audioData);
    }

    @Transactional
    public AudioData updateMetadata(int id, AudioMetadataPatch patch) {
        AudioData audio = require(id);
        if (patch.nameSet()) {
            audio.setName(Names.required(patch.name(), MAX_TEXT, "A valid name is required"));
        }
        if (patch.titleSet()) {
            audio.setTitle(Names.required(patch.title(), MAX_TEXT, "A valid title is required"));
        }
        if (patch.secondaryTitleSet()) {
            audio.setSecondaryTitle(Names.optional(patch.secondaryTitle(), MAX_TEXT));
        }
        if (patch.artistSet()) {
            audio.setArtist(Names.optional(patch.artist(), MAX_TEXT));
            artistService.ensure(audio.getArtist());
        }
        if (patch.albumSet()) {
            audio.setAlbum(Names.optional(patch.album(), MAX_TEXT));
            systemTagService.syncDerived(audio);
        }
        if (patch.trackNumberSet()) {
            audio.setTrackNumber(patch.trackNumber());
        }
        if (patch.durationSecondsSet()) {
            audio.setDurationSeconds(patch.durationSeconds());
        }
        audio.getTags().size();
        return audioDataRepository.save(audio);
    }

    @Transactional
    public AudioData replaceTags(int id, List<TagRequest> rawNames) {
        AudioData audio = require(id);
        tagService.replace(audio.getTags(), rawNames);
        return audioDataRepository.save(audio);
    }

    @Transactional
    public void deleteWithPlaylistItems(int id) {
        AudioData audio = require(id);
        audio.getTags().clear();
        playlistItemRepository.deleteByAudio_Id(id);
        audioDataRepository.delete(audio);
        audioDataRepository.flush();
    }

    private static List<AudioData> distinctInOrder(List<AudioData> audios) {
        Map<Integer, AudioData> unique = new LinkedHashMap<>();
        for (AudioData audio : audios) {
            unique.putIfAbsent(audio.getId(), audio);
        }
        return new ArrayList<>(unique.values());
    }

    private Set<String> artistNamesWithAllTags(Set<String> required) {
        Map<String, Set<String>> tagsByName = new HashMap<>();
        for (Object[] row : artistRepository.findNameAndTagKey()) {
            String name = (String) row[0];
            String key = (String) row[1];
            tagsByName.computeIfAbsent(name, ignored -> new HashSet<>()).add(key);
        }
        Set<String> names = new HashSet<>();
        for (Map.Entry<String, Set<String>> entry : tagsByName.entrySet()) {
            if (entry.getValue().containsAll(required)) {
                names.add(entry.getKey());
            }
        }
        return names;
    }

    private Set<Integer> audioIdsInPlaylistsWithAllTags(Set<String> required) {
        Map<Integer, Set<String>> tagsByPlaylist = new HashMap<>();
        for (Object[] row : playlistRepository.findIdAndTagKey()) {
            int playlistId = ((Number) row[0]).intValue();
            String key = (String) row[1];
            tagsByPlaylist.computeIfAbsent(playlistId, ignored -> new HashSet<>()).add(key);
        }
        Set<Integer> playlistIds = new HashSet<>();
        for (Map.Entry<Integer, Set<String>> entry : tagsByPlaylist.entrySet()) {
            if (entry.getValue().containsAll(required)) {
                playlistIds.add(entry.getKey());
            }
        }
        if (playlistIds.isEmpty()) {
            return Set.of();
        }
        Set<Integer> audioIds = new HashSet<>();
        for (Object[] row : playlistItemRepository.findPlaylistAndAudioIds()) {
            int playlistId = ((Number) row[0]).intValue();
            if (playlistIds.contains(playlistId)) {
                audioIds.add(((Number) row[1]).intValue());
            }
        }
        return audioIds;
    }

    private static boolean matchesQuery(AudioData audio, String needle) {
        return contains(audio.getTitle(), needle)
            || contains(audio.getSecondaryTitle(), needle)
            || contains(audio.getArtist(), needle)
            || contains(audio.getAlbum(), needle)
            || contains(audio.getName(), needle)
            || contains(audio.getOriginalFilename(), needle);
    }

    private static boolean matchesTitle(AudioData audio, String needle) {
        return contains(audio.getTitle(), needle) || contains(audio.getSecondaryTitle(), needle);
    }

    private static boolean contains(String value, String needle) {
        return TextFold.fold(value).contains(needle);
    }
}
