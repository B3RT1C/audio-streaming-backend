package net.b3rt1c.music_streaming_backend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import net.b3rt1c.music_streaming_backend.model.Artist;

public interface ArtistRepository extends JpaRepository<Artist, Integer> {
    boolean existsByName(String name);

    @Query("select distinct a from Artist a left join fetch a.tags")
    List<Artist> findAllWithTags();

    @Query("select a.name, t.nameKey from Artist a join a.tags t")
    List<Object[]> findNameAndTagKey();

    @Query("select t.id, count(a) from Artist a join a.tags t group by t.id")
    List<Object[]> countByTag();
}
