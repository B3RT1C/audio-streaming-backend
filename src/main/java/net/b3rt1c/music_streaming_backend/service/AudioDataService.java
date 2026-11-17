package net.b3rt1c.music_streaming_backend.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import net.b3rt1c.music_streaming_backend.dto.AudioMetadataPatch;
import net.b3rt1c.music_streaming_backend.error.ApiException;
import net.b3rt1c.music_streaming_backend.model.AudioData;
import net.b3rt1c.music_streaming_backend.model.Tag;
import net.b3rt1c.music_streaming_backend.repository.AudioDataRepository;
import net.b3rt1c.music_streaming_backend.repository.PlaylistItemRepository;
import net.b3rt1c.music_streaming_backend.util.Names;
import net.b3rt1c.music_streaming_backend.util.TagNames;
import net.b3rt1c.music_streaming_backend.util.TextFold;

@Service
@RequiredArgsConstructor
public class AudioDataService {
    private static final int MAX_TEXT = 500;

    private final AudioDataRepository audioDataRepository;
    private final PlaylistItemRepository playlistItemRepository;
    private final TagService tagService;
    private final ArtistService artistService;

    @Transactional(readOnly = true)
    public List<AudioData> search(String query, String tag) {
        List<AudioData> audios = distinctInOrder(audioDataRepository.findAllWithTags());
        String needle = query == null ? "" : TextFold.fold(query.trim());
        String tagKey = tag == null ? "" : TagNames.key(tag);
        boolean filterQuery = !needle.isBlank();
        boolean filterTag = tag != null && !tag.isBlank() && !tagKey.isBlank();
        if (!filterQuery && !filterTag) {
            return audios;
        }
        return audios.stream()
            .filter(audio -> !filterQuery || matches(audio, needle))
            .filter(audio -> !filterTag || hasTag(audio, tagKey))
            .toList();
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
    public AudioData replaceTags(int id, List<String> rawNames) {
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

    private static boolean matches(AudioData audio, String needle) {
        return contains(audio.getTitle(), needle)
            || contains(audio.getSecondaryTitle(), needle)
            || contains(audio.getArtist(), needle)
            || contains(audio.getAlbum(), needle)
            || contains(audio.getName(), needle)
            || contains(audio.getOriginalFilename(), needle);
    }

    private static boolean hasTag(AudioData audio, String tagKey) {
        for (Tag tag : audio.getTags()) {
            if (tagKey.equals(tag.getNameKey())) {
                return true;
            }
        }
        return false;
    }

    private static boolean contains(String value, String needle) {
        return TextFold.fold(value).contains(needle);
    }
}
