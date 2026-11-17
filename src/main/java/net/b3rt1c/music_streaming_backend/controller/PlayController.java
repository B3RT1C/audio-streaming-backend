package net.b3rt1c.music_streaming_backend.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import net.b3rt1c.music_streaming_backend.dto.PlayEventRequest;
import net.b3rt1c.music_streaming_backend.dto.PlayEventResponse;
import net.b3rt1c.music_streaming_backend.service.PlayEventService;
import net.b3rt1c.music_streaming_backend.service.PlayEventService.Recorded;

@RestController
@RequestMapping("/plays")
@RequiredArgsConstructor
public class PlayController {
    private final PlayEventService playEventService;

    @PostMapping
    public ResponseEntity<PlayEventResponse> record(@RequestBody PlayEventRequest request) {
        Recorded recorded = playEventService.record(request);
        HttpStatus status = recorded.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(recorded.event());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable int id) {
        playEventService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
