package net.b3rt1c.music_streaming_backend.service;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import net.b3rt1c.music_streaming_backend.model.TagType;
import net.b3rt1c.music_streaming_backend.repository.TagTypeRepository;
import net.b3rt1c.music_streaming_backend.util.TagTypeKeys;

@Component
@RequiredArgsConstructor
public class TagTypeBootstrap implements ApplicationRunner {
    private final TagTypeRepository tagTypeRepository;

    @Override
    public void run(ApplicationArguments args) {
        for (TagTypeKeys.Seed seed : TagTypeKeys.SYSTEM) {
            if (tagTypeRepository.findByTypeKey(seed.key()).isPresent()) {
                continue;
            }
            TagType type = new TagType();
            type.setTypeKey(seed.key());
            type.setName(seed.name());
            type.setSystem(true);
            tagTypeRepository.save(type);
        }
    }
}
