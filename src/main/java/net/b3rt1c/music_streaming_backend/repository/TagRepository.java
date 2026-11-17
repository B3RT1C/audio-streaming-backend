package net.b3rt1c.music_streaming_backend.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import net.b3rt1c.music_streaming_backend.model.Tag;

public interface TagRepository extends JpaRepository<Tag, Integer> {
    List<Tag> findByNameKeyIn(Collection<String> nameKeys);
}
