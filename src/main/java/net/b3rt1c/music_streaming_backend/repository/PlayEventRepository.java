package net.b3rt1c.music_streaming_backend.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import net.b3rt1c.music_streaming_backend.model.PlayEvent;

public interface PlayEventRepository extends JpaRepository<PlayEvent, Integer> {
    Optional<PlayEvent> findByClientEventId(String clientEventId);
}
