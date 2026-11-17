package net.b3rt1c.music_streaming_backend.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import net.b3rt1c.music_streaming_backend.model.Tag;
import net.b3rt1c.music_streaming_backend.model.TagType;

public interface TagRepository extends JpaRepository<Tag, Integer> {
    List<Tag> findByNameKeyIn(Collection<String> nameKeys);

    Optional<Tag> findByTypeAndNameKey(TagType type, String nameKey);

    List<Tag> findByType(TagType type);

    @Query("select t from Tag t join fetch t.type")
    List<Tag> findAllWithType();

    @Query("select t.type.id, count(t) from Tag t group by t.type.id")
    List<Object[]> countGroupedByType();
}
