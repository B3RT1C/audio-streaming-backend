package net.b3rt1c.music_streaming_backend.controller;

import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import net.b3rt1c.music_streaming_backend.dto.PlaylistItemRequest;
import net.b3rt1c.music_streaming_backend.dto.PlaylistItemResponse;
import net.b3rt1c.music_streaming_backend.dto.PlaylistResponse;
import net.b3rt1c.music_streaming_backend.dto.PlaylistWriteRequest;
import net.b3rt1c.music_streaming_backend.error.ApiException;
import net.b3rt1c.music_streaming_backend.error.ErrorCodes;
import net.b3rt1c.music_streaming_backend.service.PlaylistService;
import net.b3rt1c.music_streaming_backend.service.PlaylistService.AddItemResult;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/playlists")
@RequiredArgsConstructor
public class PlaylistController {
    private final PlaylistService playlistService;

    @GetMapping
    public List<PlaylistResponse> list() {
        return playlistService.list();
    }

    @PostMapping
    public ResponseEntity<PlaylistResponse> create(@RequestBody PlaylistWriteRequest request) {
        String name = request == null ? null : request.name();
        return ResponseEntity.status(HttpStatus.CREATED).body(playlistService.create(name));
    }

    @GetMapping("/{id}")
    public PlaylistResponse get(@PathVariable int id) {
        return playlistService.get(id);
    }

    @PatchMapping("/{id}")
    public PlaylistResponse rename(@PathVariable int id, @RequestBody PlaylistWriteRequest request) {
        String name = request == null ? null : request.name();
        return playlistService.rename(id, name);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable int id) {
        playlistService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/items")
    public ResponseEntity<PlaylistItemResponse> addItem(
            @PathVariable int id,
            @RequestBody PlaylistItemRequest request) {
        AddItemResult result = playlistService.addItem(id, request == null ? null : request.audioId());
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(result.item());
    }

    @DeleteMapping("/{id}/items/{audioId}")
    public ResponseEntity<Void> removeItem(@PathVariable int id, @PathVariable int audioId) {
        playlistService.removeItem(id, audioId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/items")
    public PlaylistResponse reorder(@PathVariable int id, @RequestBody JsonNode body) {
        return playlistService.reorder(id, audioIds(body));
    }

    static List<Integer> audioIds(JsonNode body) {
        JsonNode list = body;
        if (body != null && body.isObject()) {
            list = body.get("audioIds");
        }
        if (list == null || !list.isArray()) {
            throw ApiException.badRequest(ErrorCodes.INVALID_ORDER, "Expected a list of audioIds");
        }
        List<Integer> ids = new ArrayList<>();
        for (JsonNode node : list) {
            if (!node.isIntegralNumber() || !node.canConvertToInt()) {
                throw ApiException.badRequest(ErrorCodes.INVALID_ORDER, "audioIds must be integers");
            }
            ids.add(node.intValue());
        }
        return ids;
    }
}
