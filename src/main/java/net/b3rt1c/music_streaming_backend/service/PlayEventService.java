package net.b3rt1c.music_streaming_backend.service;

import java.time.Instant;
import java.util.Locale;
import java.util.Set;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import net.b3rt1c.music_streaming_backend.config.StatsProperties;
import net.b3rt1c.music_streaming_backend.dto.PlayEventRequest;
import net.b3rt1c.music_streaming_backend.dto.PlayEventResponse;
import net.b3rt1c.music_streaming_backend.error.ApiException;
import net.b3rt1c.music_streaming_backend.error.ErrorCodes;
import net.b3rt1c.music_streaming_backend.model.AudioData;
import net.b3rt1c.music_streaming_backend.model.PlayEvent;
import net.b3rt1c.music_streaming_backend.model.Playlist;
import net.b3rt1c.music_streaming_backend.repository.AudioDataRepository;
import net.b3rt1c.music_streaming_backend.repository.PlayEventRepository;
import net.b3rt1c.music_streaming_backend.repository.PlaylistRepository;

@Service
public class PlayEventService {
    static final Set<String> SOURCES = Set.of(
        "library", "search", "playlist", "artist", "shuffle", "other"
    );

    private final PlayEventRepository playEventRepository;
    private final AudioDataRepository audioDataRepository;
    private final PlaylistRepository playlistRepository;
    private final StatsProperties statsProperties;
    private final TransactionTemplate transactions;
    private final TransactionTemplate reads;

    public PlayEventService(
            PlayEventRepository playEventRepository,
            AudioDataRepository audioDataRepository,
            PlaylistRepository playlistRepository,
            StatsProperties statsProperties,
            PlatformTransactionManager transactionManager) {
        this.playEventRepository = playEventRepository;
        this.audioDataRepository = audioDataRepository;
        this.playlistRepository = playlistRepository;
        this.statsProperties = statsProperties;
        this.transactions = new TransactionTemplate(transactionManager);
        this.reads = new TransactionTemplate(transactionManager);
        this.reads.setReadOnly(true);
    }

    public Recorded record(PlayEventRequest request) {
        if (request == null) {
            throw ApiException.badRequest(ErrorCodes.INVALID_BODY, "Request body is invalid");
        }
        String clientEventId = normalizeClientEventId(request.clientEventId());
        if (clientEventId != null) {
            PlayEventResponse existing = findByClientEventId(clientEventId);
            if (existing != null) {
                return Recorded.existing(existing);
            }
        }
        try {
            PlayEventResponse created = transactions.execute(status -> insert(request, clientEventId));
            return Recorded.created(created);
        } catch (RuntimeException exception) {
            if (clientEventId != null && isConstraintViolation(exception)) {
                PlayEventResponse existing = findByClientEventId(clientEventId);
                if (existing != null) {
                    return Recorded.existing(existing);
                }
            }
            throw exception;
        }
    }

    public void delete(int id) {
        transactions.executeWithoutResult(status -> {
            if (!playEventRepository.existsById(id)) {
                throw ApiException.notFound("Play event not found");
            }
            playEventRepository.deleteById(id);
        });
    }

    private PlayEventResponse insert(PlayEventRequest request, String clientEventId) {
        if (request.audioId() == null) {
            throw ApiException.badRequest(ErrorCodes.INVALID_BODY, "audioId is required");
        }
        if (request.listenedSeconds() == null) {
            throw ApiException.badRequest(ErrorCodes.INVALID_BODY, "listenedSeconds is required");
        }
        if (request.listenedSeconds() < 0) {
            throw ApiException.badRequest(ErrorCodes.INVALID_BODY, "listenedSeconds must be zero or greater");
        }
        AudioData audio = audioDataRepository.findById(request.audioId())
            .orElseThrow(() -> ApiException.badRequest(ErrorCodes.INVALID_BODY, "Audio not found"));
        Playlist playlist = null;
        if (request.playlistId() != null) {
            playlist = playlistRepository.findById(request.playlistId())
                .orElseThrow(() -> ApiException.badRequest(ErrorCodes.INVALID_BODY, "Playlist not found"));
        }
        String source = normalizeSource(request.source());
        int listenedSeconds = cap(request.listenedSeconds(), audio.getDurationSeconds(), statsProperties.getListenedSlackSeconds());
        boolean counted = counts(
            listenedSeconds,
            audio.getDurationSeconds(),
            statsProperties.getMinCountedSeconds(),
            statsProperties.getCountedDurationRatio()
        );

        PlayEvent event = new PlayEvent();
        event.setAudio(audio);
        event.setStartedAt(request.startedAt() == null ? Instant.now() : request.startedAt());
        event.setListenedSeconds(listenedSeconds);
        event.setCompleted(Boolean.TRUE.equals(request.completed()));
        event.setCounted(counted);
        event.setSource(source);
        event.setPlaylist(playlist);
        event.setClientEventId(clientEventId);
        playEventRepository.saveAndFlush(event);
        return toResponse(event);
    }

    private PlayEventResponse findByClientEventId(String clientEventId) {
        return reads.execute(status -> playEventRepository.findByClientEventId(clientEventId)
            .map(PlayEventService::toResponse)
            .orElse(null));
    }

    static int cap(int requested, Integer durationSeconds, int slackSeconds) {
        if (durationSeconds == null || durationSeconds < 0) {
            return requested;
        }
        long cap = (long) durationSeconds + slackSeconds;
        if (requested > cap) {
            return (int) cap;
        }
        return requested;
    }

    /**
     * A listen counts at {@code minSeconds}, or sooner when half (by default) of a
     * known duration is the smaller bar. Unknown duration uses {@code minSeconds} alone.
     * The fraction is a real number, so a threshold of 15.5 needs 16 listened seconds.
     */
    static boolean counts(int listenedSeconds, Integer durationSeconds, int minSeconds, double ratio) {
        double threshold = minSeconds;
        if (durationSeconds != null && durationSeconds >= 0) {
            threshold = Math.min(minSeconds, durationSeconds * ratio);
        }
        return listenedSeconds >= threshold;
    }

    private static String normalizeSource(String source) {
        if (source == null || source.isBlank()) {
            return null;
        }
        String token = source.trim().toLowerCase(Locale.ROOT);
        if (!SOURCES.contains(token)) {
            throw ApiException.badRequest(
                ErrorCodes.INVALID_BODY,
                "source must be library, search, playlist, artist, shuffle or other"
            );
        }
        return token;
    }

    private static String normalizeClientEventId(String clientEventId) {
        if (clientEventId == null) {
            return null;
        }
        String trimmed = clientEventId.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static PlayEventResponse toResponse(PlayEvent event) {
        return new PlayEventResponse(
            event.getId(),
            event.getAudio().getId(),
            event.getStartedAt(),
            event.getListenedSeconds(),
            event.isCompleted(),
            event.isCounted(),
            event.getSource(),
            event.getPlaylist() == null ? null : event.getPlaylist().getId(),
            event.getClientEventId(),
            event.getCreatedAt()
        );
    }

    private static boolean isConstraintViolation(Throwable throwable) {
        while (throwable != null) {
            if (throwable instanceof DataIntegrityViolationException
                    || throwable instanceof jakarta.persistence.PersistenceException) {
                return true;
            }
            throwable = throwable.getCause();
        }
        return false;
    }

    public record Recorded(boolean created, PlayEventResponse event) {
        static Recorded created(PlayEventResponse event) {
            return new Recorded(true, event);
        }

        static Recorded existing(PlayEventResponse event) {
            return new Recorded(false, event);
        }
    }
}
