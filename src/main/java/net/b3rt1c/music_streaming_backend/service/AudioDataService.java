package net.b3rt1c.music_streaming_backend.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import net.b3rt1c.music_streaming_backend.dto.AudioMetadataPatch;
import net.b3rt1c.music_streaming_backend.error.ApiException;
import net.b3rt1c.music_streaming_backend.model.AudioData;
import net.b3rt1c.music_streaming_backend.repository.AudioDataRepository;
import net.b3rt1c.music_streaming_backend.repository.PlaylistItemRepository;
import net.b3rt1c.music_streaming_backend.util.Names;
import net.b3rt1c.music_streaming_backend.util.TextFold;

@Service
@RequiredArgsConstructor
public class AudioDataService {
    private static final int MAX_TEXT = 500;

    private final AudioDataRepository audioDataRepository;
    private final PlaylistItemRepository playlistItemRepository;

    public List<AudioData> search(String query) {
        List<AudioData> audios = audioDataRepository.findAllByOrderByIdAsc();
        if (query == null || query.isBlank()) {
            return audios;
        }
        String needle = TextFold.fold(query.trim());
        if (needle.isBlank()) {
            return audios;
        }
        return audios.stream()
            .filter(audio -> matches(audio, needle))
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
        if (patch.artistSet()) {
            audio.setArtist(Names.optional(patch.artist(), MAX_TEXT));
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
        return audioDataRepository.save(audio);
    }

    @Transactional
    public void deleteWithPlaylistItems(int id) {
        playlistItemRepository.deleteByAudio_Id(id);
        audioDataRepository.deleteById(id);
        audioDataRepository.flush();
    }

    private static boolean matches(AudioData audio, String needle) {
        return contains(audio.getTitle(), needle)
            || contains(audio.getArtist(), needle)
            || contains(audio.getAlbum(), needle)
            || contains(audio.getName(), needle)
            || contains(audio.getOriginalFilename(), needle);
    }

    private static boolean contains(String value, String needle) {
        return TextFold.fold(value).contains(needle);
    }
}
