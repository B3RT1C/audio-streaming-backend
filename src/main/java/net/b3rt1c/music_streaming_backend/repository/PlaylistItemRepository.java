package net.b3rt1c.music_streaming_backend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import net.b3rt1c.music_streaming_backend.model.PlaylistItem;

public interface PlaylistItemRepository extends JpaRepository<PlaylistItem, Integer> {
    long deleteByAudio_Id(Integer audioId);

    @Query("select i.playlist.id, count(i) from PlaylistItem i group by i.playlist.id")
    List<Object[]> countByPlaylist();
}
