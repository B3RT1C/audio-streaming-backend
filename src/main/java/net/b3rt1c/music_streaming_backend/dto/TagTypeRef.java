package net.b3rt1c.music_streaming_backend.dto;

import net.b3rt1c.music_streaming_backend.model.TagType;

public record TagTypeRef(Integer id, String key, String name, boolean system) {
    public static TagTypeRef from(TagType type) {
        return new TagTypeRef(type.getId(), type.getTypeKey(), type.getName(), type.isSystem());
    }
}
