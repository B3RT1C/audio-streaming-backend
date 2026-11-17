package net.b3rt1c.music_streaming_backend.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import net.b3rt1c.music_streaming_backend.dto.ArtistResponse;
import net.b3rt1c.music_streaming_backend.dto.TagAssignment;
import net.b3rt1c.music_streaming_backend.service.ArtistService;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/artists")
@RequiredArgsConstructor
public class ArtistController {
    private final ArtistService artistService;

    @GetMapping
    public List<ArtistResponse> list(
            @RequestParam(value = "q", required = false) String query,
            @RequestParam(value = "tag", required = false) List<String> tags) {
        return artistService.list(query, tags);
    }

    @PutMapping("/{id}/tags")
    public ArtistResponse replaceTags(@PathVariable int id, @RequestBody JsonNode body) {
        return artistService.replaceTags(id, TagAssignment.parse(body));
    }
}
