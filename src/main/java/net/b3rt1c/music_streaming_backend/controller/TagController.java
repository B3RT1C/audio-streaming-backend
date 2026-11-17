package net.b3rt1c.music_streaming_backend.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import net.b3rt1c.music_streaming_backend.dto.TagResponse;
import net.b3rt1c.music_streaming_backend.service.TagService;

@RestController
@RequestMapping("/tags")
@RequiredArgsConstructor
public class TagController {
    private final TagService tagService;

    @GetMapping
    public List<TagResponse> list() {
        return tagService.list();
    }
}
