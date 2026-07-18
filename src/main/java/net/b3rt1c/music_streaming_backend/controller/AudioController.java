package net.b3rt1c.music_streaming_backend.controller;

import java.io.IOException;
import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.core.io.support.ResourceRegion;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import lombok.RequiredArgsConstructor;
import net.b3rt1c.music_streaming_backend.model.AudioData;
import net.b3rt1c.music_streaming_backend.service.AudioDataService;
import net.b3rt1c.music_streaming_backend.service.AudioFileService;
import net.b3rt1c.music_streaming_backend.service.AudioStreamService;
import net.b3rt1c.music_streaming_backend.util.FilenameUtils;
import net.b3rt1c.music_streaming_backend.util.FilenameUtils.ParsedFilename;

@RestController
@RequestMapping("/audios")
@RequiredArgsConstructor
public class AudioController {
    private static final String SONGS_PATH = "uploads/songs/";
    private static final Logger log = LoggerFactory.getLogger(AudioController.class);

    private final AudioDataService audioDataService;
    private final AudioFileService audioFileService;
    private final AudioStreamService audioStreamService;

    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    public List<AudioData> getAllAudiosInfo() {
        return audioDataService.findAllAudioDatas();
    }

    @GetMapping("/{id}")
    public ResponseEntity<ResourceRegion> getAudioFile(
            @PathVariable int id,
            @RequestHeader HttpHeaders headers) throws IOException {
        AudioData audioData = audioDataService.findAudioData(id);

        if (audioData == null) {
            return ResponseEntity.notFound().build();
        }

        return audioStreamService.streamAudioFile(
            audioData.getPath(),
            audioData.getName(),
            audioData.getExtension(),
            headers
        );
    }

    @PostMapping
    public ResponseEntity<String> uploadAudio(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "name", required = false) String name) {
        if (file.isEmpty() || file.getOriginalFilename() == null) {
            return ResponseEntity.badRequest().body("{\"message\":\"File is required\"}");
        }

        ParsedFilename parsedFilename = FilenameUtils.parse(file.getOriginalFilename());
        String trackName = resolveTrackName(name, parsedFilename.name());

        if (trackName == null) {
            return ResponseEntity.badRequest().body("{\"message\":\"A valid name is required\"}");
        }

        if (audioDataService.existsByName(trackName)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body("{\"message\":\"A song with this name already exists\"}");
        }

        String contentHash = audioFileService.addAudioFileAndComputeSha256(
            file,
            trackName,
            parsedFilename.extension()
        );
        if (audioDataService.existsByContentHash(contentHash)) {
            log.info("Duplicate content detected for upload '{}': sha256={}", file.getOriginalFilename(), contentHash);
        }

        try {
            AudioData audioData = new AudioData(trackName, parsedFilename.extension(), SONGS_PATH);
            audioData.setContentHash(contentHash);
            audioDataService.addAudioData(audioData);
        } catch (DataIntegrityViolationException exception) {
            audioFileService.deleteAudioFile(SONGS_PATH, trackName, parsedFilename.extension());
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body("{\"message\":\"A song with this name already exists\"}");
        }

        return ResponseEntity.status(HttpStatus.CREATED)
            .body("{\"message\":\"File received\"}");
    }

    private static String resolveTrackName(String requestedName, String fallbackName) {
        String candidate = requestedName == null || requestedName.isBlank()
            ? fallbackName
            : requestedName.trim();

        if (candidate.isBlank() || candidate.contains("/") || candidate.contains("\\") || candidate.contains("..")) {
            return null;
        }

        return candidate;
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAudio(@PathVariable int id) {
        AudioData audioData = audioDataService.findAudioData(id);

        if (audioData == null) {
            return ResponseEntity.notFound().build();
        }

        audioFileService.deleteAudioFile(audioData.getPath(), audioData.getName(), audioData.getExtension());
        audioDataService.deleteAudioData(id);

        return ResponseEntity.noContent().build();
    }
}
