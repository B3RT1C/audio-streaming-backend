package net.b3rt1c.music_streaming_backend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import net.b3rt1c.music_streaming_backend.model.AudioData;

public interface AudioDataRepository extends JpaRepository<AudioData, Integer> {
    List<AudioData> findAllByOrderByIdAsc();

    boolean existsByContentHash(String contentHash);

    boolean existsByOriginalPath(String originalPath);
}
