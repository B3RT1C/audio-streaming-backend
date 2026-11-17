package net.b3rt1c.music_streaming_backend.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import net.b3rt1c.music_streaming_backend.dto.RebuildResult;
import net.b3rt1c.music_streaming_backend.service.SystemTagService;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminTagController {
    private final SystemTagService systemTagService;

    @PostMapping("/tags/rebuild-system")
    public RebuildResult rebuildSystemTags() {
        return systemTagService.rebuild();
    }
}
