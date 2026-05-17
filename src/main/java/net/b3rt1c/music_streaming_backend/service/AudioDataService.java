package net.b3rt1c.music_streaming_backend.service;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import net.b3rt1c.music_streaming_backend.model.AudioData;
import net.b3rt1c.music_streaming_backend.repository.AudioDataRepository;

@Service
@RequiredArgsConstructor
public class AudioDataService {
    private final AudioDataRepository audioDataRepository;
    
    public Resource getSong() {
        Resource songFile = new FileSystemResource(Path.of("uploads/songs/RickAstley-NeverGonnaGiveYouUp.mp3"));

        return songFile;
    }

    public List<AudioData> findAllAudioDatas() {
        return audioDataRepository.findAll();
    }

    public AudioData findAudioData(Integer id) {
        Optional<AudioData> optAD = audioDataRepository.findById(id);

        if (optAD.isEmpty()) {
            return null;
        }
        return optAD.get();
    }

    public void addAudioData(AudioData audioData) {
        try {
            audioDataRepository.save(audioData);
        } catch (Exception e) {
        
        }
    }

    public void deleteAudioData(Integer id) {
        audioDataRepository.deleteById(id);
    }
}
