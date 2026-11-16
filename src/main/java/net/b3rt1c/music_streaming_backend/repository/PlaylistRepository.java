package net.b3rt1c.music_streaming_backend.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import net.b3rt1c.music_streaming_backend.model.Playlist;

public interface PlaylistRepository extends JpaRepository<Playlist, Integer> {
    @EntityGraph(attributePaths = {"items", "items.audio"})
    @Query("select p from Playlist p where p.id = :id")
    Optional<Playlist> findWithItemsById(@Param("id") Integer id);
}
