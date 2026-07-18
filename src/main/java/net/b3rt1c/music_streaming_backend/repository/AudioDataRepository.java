package net.b3rt1c.music_streaming_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import net.b3rt1c.music_streaming_backend.model.AudioData;

@Repository
public interface AudioDataRepository extends JpaRepository<AudioData, Integer> {
    boolean existsByName(String name);
    boolean existsByContentHash(String contentHash);
}