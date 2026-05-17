package net.b3rt1c.music_streaming_backend.controller;

import java.util.List;

import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import lombok.RequiredArgsConstructor;
import net.b3rt1c.music_streaming_backend.model.AudioData;
import net.b3rt1c.music_streaming_backend.service.AudioDataService;
import net.b3rt1c.music_streaming_backend.service.AudioFileService;

@RestController
@RequestMapping("/song")
@RequiredArgsConstructor
public class AudioController {
    private final AudioDataService audioDataService;
    private final AudioFileService audioFileService;

    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    public List<AudioData> getAllAudiosInfo() {
        return audioDataService.findAllAudioDatas();
    }

    @GetMapping("/file")
    @ResponseStatus(HttpStatus.OK)
    public ResponseEntity<Resource> getAudioFile(@RequestParam int id) {
        AudioData audioData = audioDataService.findAudioData(id);

        if (audioData == null) {
            return ResponseEntity.notFound().build();
        }

        Resource audioFile = audioFileService.getAudioFile(audioData.getPath(), audioData.getName(), audioData.getExtension());
        ResponseEntity<Resource> responseEntity = ResponseEntity.ok(audioFile);
        responseEntity.getHeaders().add("Content-Type", "audio/mpeg");
        return responseEntity;
    }

    @PostMapping("/file")
    public ResponseEntity<String> uploadAudio(@RequestParam("file") MultipartFile file) {
        System.out.println("File received: " + file.getOriginalFilename() + " " + file.getSize() + "B");

        long bytesSaved = audioFileService.addAudioFile(file);
        System.out.println("File written: " + bytesSaved);

        String[] fullName = file.getOriginalFilename().split("\\.");
        String name = fullName[0];
        String extension = fullName[1];
        String rootPath = "uploads/songs/";
        AudioData audioData = new AudioData(name, extension, rootPath);
        audioDataService.addAudioData(audioData);

        return ResponseEntity.status(HttpStatus.CREATED)
                                .body("{\"message\":\"File received\"}");
    }
}