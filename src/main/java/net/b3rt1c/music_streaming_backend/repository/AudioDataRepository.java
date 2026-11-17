package net.b3rt1c.music_streaming_backend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import net.b3rt1c.music_streaming_backend.model.AudioData;

public interface AudioDataRepository extends JpaRepository<AudioData, Integer> {
    @Query("select a from AudioData a left join fetch a.tags order by a.id asc")
    List<AudioData> findAllWithTags();

    boolean existsByContentHash(String contentHash);

    boolean existsByOriginalPath(String originalPath);

    long countByArtist(String artist);

    @Query("select a.artist, count(a) from AudioData a where a.artist is not null group by a.artist")
    List<Object[]> countGroupedByArtist();

    @Query("select t.id, count(a) from AudioData a join a.tags t group by t.id")
    List<Object[]> countByTag();
}
