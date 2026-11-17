package net.b3rt1c.music_streaming_backend.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import net.b3rt1c.music_streaming_backend.dto.TagTypeResponse;
import net.b3rt1c.music_streaming_backend.dto.TagTypeWrite;
import net.b3rt1c.music_streaming_backend.service.TagTypeService;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/tag-types")
@RequiredArgsConstructor
public class TagTypeController {
    private final TagTypeService tagTypeService;

    @GetMapping
    public List<TagTypeResponse> list() {
        return tagTypeService.list();
    }

    @PostMapping
    public ResponseEntity<TagTypeResponse> create(@RequestBody JsonNode body) {
        TagTypeWrite.Create create = TagTypeWrite.parseCreate(body);
        return ResponseEntity.status(HttpStatus.CREATED).body(tagTypeService.create(create.name(), create.color()));
    }

    @PatchMapping("/{id}")
    public TagTypeResponse patch(@PathVariable int id, @RequestBody JsonNode body) {
        TagTypeWrite.Patch patch = TagTypeWrite.parsePatch(body);
        return tagTypeService.patch(id, patch.nameSet(), patch.name(), patch.colorSet(), patch.color());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable int id) {
        tagTypeService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
