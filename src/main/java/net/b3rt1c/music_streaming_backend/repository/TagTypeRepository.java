package net.b3rt1c.music_streaming_backend.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import net.b3rt1c.music_streaming_backend.model.TagType;

public interface TagTypeRepository extends JpaRepository<TagType, Integer> {
    Optional<TagType> findByTypeKey(String typeKey);
}
