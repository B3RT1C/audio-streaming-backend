package net.b3rt1c.music_streaming_backend.service;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import net.b3rt1c.music_streaming_backend.dto.AudioResponse;
import net.b3rt1c.music_streaming_backend.dto.PlaylistItemResponse;
import net.b3rt1c.music_streaming_backend.dto.PlaylistResponse;
import net.b3rt1c.music_streaming_backend.error.ApiException;
import net.b3rt1c.music_streaming_backend.error.ErrorCodes;
import net.b3rt1c.music_streaming_backend.model.AudioData;
import net.b3rt1c.music_streaming_backend.model.Playlist;
import net.b3rt1c.music_streaming_backend.model.PlaylistItem;
import net.b3rt1c.music_streaming_backend.repository.AudioDataRepository;
import net.b3rt1c.music_streaming_backend.repository.PlaylistItemRepository;
import net.b3rt1c.music_streaming_backend.repository.PlaylistRepository;
import net.b3rt1c.music_streaming_backend.util.Names;

@Service
@RequiredArgsConstructor
public class PlaylistService {
    private final PlaylistRepository playlistRepository;
    private final PlaylistItemRepository playlistItemRepository;
    private final AudioDataRepository audioDataRepository;

    @Transactional(readOnly = true)
    public List<PlaylistResponse> list() {
        Map<Integer, Integer> counts = itemCounts();
        return playlistRepository.findAll(Sort.by("id")).stream()
            .map(playlist -> summary(playlist, counts.getOrDefault(playlist.getId(), 0)))
            .toList();
    }

    @Transactional(readOnly = true)
    public PlaylistResponse get(int id) {
        return detail(require(id));
    }

    @Transactional
    public PlaylistResponse create(String name) {
        Playlist playlist = new Playlist();
        playlist.setName(validateName(name));
        return detail(playlistRepository.save(playlist));
    }

    @Transactional
    public PlaylistResponse rename(int id, String name) {
        Playlist playlist = require(id);
        playlist.setName(validateName(name));
        playlist.setUpdatedAt(Instant.now());
        return detail(playlistRepository.save(playlist));
    }

    @Transactional
    public void delete(int id) {
        if (!playlistRepository.existsById(id)) {
            throw ApiException.notFound("Playlist not found");
        }
        playlistRepository.deleteById(id);
    }

    @Transactional
    public AddItemResult addItem(int playlistId, Integer audioId) {
        if (audioId == null) {
            throw ApiException.badRequest(ErrorCodes.INVALID_FILE, "audioId is required");
        }
        Playlist playlist = require(playlistId);
        AudioData audio = audioDataRepository.findById(audioId)
            .orElseThrow(() -> ApiException.notFound("Audio track not found"));

        for (PlaylistItem item : playlist.getItems()) {
            if (audioId.equals(item.getAudio().getId())) {
                return new AddItemResult(false, toItem(item));
            }
        }

        int next = playlist.getItems().stream()
            .mapToInt(PlaylistItem::getPosition)
            .max()
            .orElse(0) + 1;
        PlaylistItem item = new PlaylistItem();
        item.setPlaylist(playlist);
        item.setAudio(audio);
        item.setPosition(next);
        playlist.getItems().add(item);
        playlist.setUpdatedAt(Instant.now());
        playlistRepository.saveAndFlush(playlist);
        return new AddItemResult(true, toItem(item));
    }

    @Transactional
    public void removeItem(int playlistId, int audioId) {
        Playlist playlist = require(playlistId);
        boolean removed = playlist.getItems().removeIf(item -> audioId == item.getAudio().getId());
        if (!removed) {
            throw ApiException.notFound("Track is not in the playlist");
        }
        playlist.setUpdatedAt(Instant.now());
        playlistRepository.save(playlist);
    }

    @Transactional
    public PlaylistResponse reorder(int playlistId, List<Integer> audioIds) {
        Playlist playlist = require(playlistId);
        if (audioIds == null) {
            throw ApiException.badRequest(ErrorCodes.INVALID_ORDER, "Expected a list of audioIds");
        }

        LinkedHashSet<Integer> ordered = new LinkedHashSet<>();
        for (Integer audioId : audioIds) {
            if (audioId == null || !ordered.add(audioId)) {
                throw ApiException.badRequest(ErrorCodes.INVALID_ORDER, "audioIds must be unique integers");
            }
        }

        Set<Integer> current = playlist.getItems().stream()
            .map(item -> item.getAudio().getId())
            .collect(java.util.stream.Collectors.toSet());
        if (!current.equals(ordered)) {
            throw ApiException.badRequest(
                ErrorCodes.INVALID_ORDER,
                "The audioIds list must contain exactly the tracks in the playlist"
            );
        }

        Map<Integer, PlaylistItem> byAudio = new HashMap<>();
        for (PlaylistItem item : playlist.getItems()) {
            byAudio.put(item.getAudio().getId(), item);
        }
        int position = 1;
        for (Integer audioId : ordered) {
            byAudio.get(audioId).setPosition(position++);
        }
        playlist.setUpdatedAt(Instant.now());
        playlistRepository.save(playlist);
        return detail(playlist);
    }

    public record AddItemResult(boolean created, PlaylistItemResponse item) {
    }

    private Playlist require(int id) {
        return playlistRepository.findWithItemsById(id)
            .orElseThrow(() -> ApiException.notFound("Playlist not found"));
    }

    private Map<Integer, Integer> itemCounts() {
        Map<Integer, Integer> counts = new HashMap<>();
        for (Object[] row : playlistItemRepository.countByPlaylist()) {
            counts.put(((Number) row[0]).intValue(), ((Number) row[1]).intValue());
        }
        return counts;
    }

    private static PlaylistResponse summary(Playlist playlist, int itemCount) {
        return new PlaylistResponse(
            playlist.getId(),
            playlist.getName(),
            playlist.getCreatedAt(),
            playlist.getUpdatedAt(),
            itemCount,
            null
        );
    }

    private static PlaylistResponse detail(Playlist playlist) {
        List<PlaylistItemResponse> items = playlist.getItems().stream()
            .sorted(Comparator.comparingInt(PlaylistItem::getPosition))
            .map(PlaylistService::toItem)
            .toList();
        return new PlaylistResponse(
            playlist.getId(),
            playlist.getName(),
            playlist.getCreatedAt(),
            playlist.getUpdatedAt(),
            items.size(),
            items
        );
    }

    private static PlaylistItemResponse toItem(PlaylistItem item) {
        return new PlaylistItemResponse(
            item.getPosition(),
            item.getAudio().getId(),
            AudioResponse.from(item.getAudio())
        );
    }

    private static String validateName(String name) {
        return Names.required(name, 255, "A valid name is required");
    }
}
