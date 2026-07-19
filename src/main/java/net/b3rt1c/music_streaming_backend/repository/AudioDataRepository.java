package net.b3rt1c.music_streaming_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import net.b3rt1c.music_streaming_backend.model.AudioData;

public interface AudioDataRepository extends JpaRepository<AudioData, Integer> {
    boolean existsByContentHash(String contentHash);
}
