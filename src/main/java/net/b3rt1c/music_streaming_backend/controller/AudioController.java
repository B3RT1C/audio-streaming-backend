package net.b3rt1c.music_streaming_backend.controller;

import java.io.IOException;
import java.util.List;

import org.springframework.core.io.support.ResourceRegion;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import lombok.RequiredArgsConstructor;
import net.b3rt1c.music_streaming_backend.dto.ApiMessage;
import net.b3rt1c.music_streaming_backend.dto.AudioMetadataPatch;
import net.b3rt1c.music_streaming_backend.dto.AudioResponse;
import net.b3rt1c.music_streaming_backend.dto.TagAssignment;
import net.b3rt1c.music_streaming_backend.model.AudioData;
import net.b3rt1c.music_streaming_backend.service.AudioDataService;
import net.b3rt1c.music_streaming_backend.service.AudioFileService;
import net.b3rt1c.music_streaming_backend.service.AudioIngestService;
import net.b3rt1c.music_streaming_backend.service.AudioStreamService;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/audios")
@RequiredArgsConstructor
public class AudioController {
    private final AudioDataService audioDataService;
    private final AudioFileService audioFileService;
    private final AudioIngestService audioIngestService;
    private final AudioStreamService audioStreamService;

    @GetMapping
    public List<AudioResponse> getAllAudiosInfo(
            @RequestParam(value = "q", required = false) String query,
            @RequestParam(value = "tag", required = false) String tag) {
        return audioDataService.search(query, tag).stream()
            .map(AudioResponse::from)
            .toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<ResourceRegion> getAudioFile(
            @PathVariable int id,
            @RequestHeader HttpHeaders headers) throws IOException {
        return audioStreamService.stream(audioDataService.require(id), headers);
    }

    @PostMapping
    public ResponseEntity<ApiMessage> uploadAudio(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "name", required = false) String name) {
        audioIngestService.upload(file, name);
        return ResponseEntity.status(HttpStatus.CREATED).body(new ApiMessage("File received"));
    }

    @PatchMapping("/{id}")
    public AudioResponse updateMetadata(@PathVariable int id, @RequestBody JsonNode body) {
        return AudioResponse.from(audioDataService.updateMetadata(id, AudioMetadataPatch.parse(body)));
    }

    @PutMapping("/{id}/tags")
    public AudioResponse replaceTags(@PathVariable int id, @RequestBody JsonNode body) {
        return AudioResponse.from(audioDataService.replaceTags(id, TagAssignment.parse(body)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAudio(@PathVariable int id) {
        AudioData audio = audioDataService.require(id);
        audioDataService.deleteWithPlaylistItems(id);
        audioFileService.deleteAudioFile(audio.getPath(), audio.getStorageKey(), audio.getExtension());
        return ResponseEntity.noContent().build();
    }
}
